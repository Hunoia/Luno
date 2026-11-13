package hunoia.luno.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import hunoia.luno.core.AppContext
import hunoia.luno.bridge.system.AccessibilitySettings
import hunoia.luno.bridge.ui.assertNotMainThread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Method
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuManager {

    private const val RequestCode = 41051
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val autoPermissionMutex = Mutex()
    private val permissionMutex = Mutex()
    private val statusMutableStateFlow = MutableStateFlow(snapshot())

    @Volatile
    private var permissionResult: CompletableDeferred<Boolean>? = null

    @Volatile
    private var autoPermissionRequested = false

    @Volatile
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val isRoot get() = runCatching { Shizuku.getUid() }.getOrDefault(-1) == 0
    private val callerPackage get() = if (isRoot) "com.android.shell" else AppContext.get().packageName

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateStatus()
        scope.launch {
            autoRequestPermissionIfNeeded()
            ensureWriteSecureSettings()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateStatus()
        permissionResult?.complete(false)
        permissionResult = null
        autoPermissionRequested = false
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != RequestCode) return@OnRequestPermissionResultListener
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            permissionResult?.complete(granted)
            permissionResult = null
            if (!granted) {
                autoPermissionRequested = false
            } else {
                scope.launch { grantWriteSecureSettings() }
            }
            updateStatus()
        }

    init {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        updateStatus()
    }

    // ---- Hidden API proxies ----

    private val pmClass by lazy { Class.forName("android.content.pm.IPackageManager") }

    private val pmProxy by lazy {
        val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
        val binder = SystemServiceHelper.getSystemService("package")
        HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder))
    }

    private val launcherAppsProxy by lazy {
        val stubClass = Class.forName("android.content.pm.ILauncherApps\$Stub")
        val binder = SystemServiceHelper.getSystemService(Context.LAUNCHER_APPS_SERVICE)
        HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder))
    }

    private val amProxy by lazy {
        val stubClass = Class.forName("android.app.IActivityManager\$Stub")
        val binder = SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder))
        } else {
            stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
        }
    }

    private val activityTaskProxy by lazy {
        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        val binder = SystemServiceHelper.getSystemService("activity_task")
        HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder))
    }

    /** Android 13~17 的签名一致：12 参，最后一个参数是 userId，options 是 Bundle。 */
    private val startActivityAsUserMethod by lazy {
        HiddenApiBypass.getDeclaredMethods(Class.forName("android.app.IActivityTaskManager"))
            .filterIsInstance<Method>()
            .firstOrNull { it.name == "startActivityAsUser" && it.parameterCount == 12 }
            ?: throw NoSuchMethodException("IActivityTaskManager.startActivityAsUser not found")
    }

    /**
     * Android 16 的 startShortcut 是 8 参（callingToken / callingPackage / packageName /
     * featureId / shortcutId / bounds / options / userId）；按实际签名调，避免绑死版本。
     */
    private val startShortcutMethod by lazy {
        val methods = Class.forName("android.content.pm.ILauncherApps").methods
        methods.firstOrNull { it.name == "startShortcutWithFeature" }
            ?: methods.firstOrNull { it.name == "startShortcut" && it.parameterCount >= 5 }
            ?: throw NoSuchMethodException(
                "ILauncherApps.startShortcut methods=" +
                    methods.filter { "hortcut" in it.name }.joinToString { it.name + it.parameterCount }
            )
    }

    private val userId by lazy {
        Class.forName("android.os.UserHandle").getMethod("myUserId").invoke(null) as Int
    }

    private val setEnabledSetting by lazy {
        runCatching {
            pmClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                String::class.java
            )
        }.getOrElse {
            pmClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
        }
    }

    // ---- Status ----

    val statusFlow: StateFlow<ShizukuStatus> = statusMutableStateFlow.asStateFlow()

    fun currentStatus(): ShizukuStatus = statusMutableStateFlow.value

    fun updateStatus() {
        statusMutableStateFlow.value = snapshot()
    }

    // ---- Permission ----

    suspend fun autoRequestPermissionIfNeeded(): Boolean {
        if (!shouldAutoRequest(currentStatus())) return false
        return autoPermissionMutex.withLock {
            val status = currentStatus()
            if (!shouldAutoRequest(status)) return@withLock false
            if (Shizuku.isPreV11()) return@withLock false
            if (Shizuku.shouldShowRequestPermissionRationale()) return@withLock false
            autoPermissionRequested = true
            requestPermission()
        }
    }

    suspend fun requestPermission(): Boolean {
        if (Shizuku.isPreV11()) return false
        return permissionMutex.withLock {
            if (!isBinderAlive()) return@withLock false
            if (hasPermission()) return@withLock true
            val deferred = CompletableDeferred<Boolean>()
            permissionResult = deferred
            Shizuku.requestPermission(RequestCode)
            val granted = deferred.await()
            updateStatus()
            granted
        }
    }

    /**
     * 让 Shizuku 变为可用：binder 活着就请求授权，binder 不在就拉起 Shizuku 应用。
     *
     * 修的是「启动 Shizuku」按钮在 Shizuku 未运行时什么都不做（[autoRequestPermissionIfNeeded]
     * 遇到 binder 不在直接返回 false，用户点了没反应）。返回 false = Shizuku 未安装 / 无法拉起。
     */
    suspend fun requestOrLaunch(): Boolean {
        if (isBinderAlive()) return requestPermission()
        return launchApp()
    }

    /** 拉起 Shizuku 应用；未安装时返回 false，由调用方提示。 */
    fun launchApp(): Boolean {
        val context = AppContext.get()
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    // ---- Shell Execution ----

    private suspend fun grantWriteSecureSettings() = withContext(Dispatchers.IO) {
        assertNotMainThread("shizuku.grantWriteSecureSettings")
        runCatching {
            val svc = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val proc = svc.newProcess(arrayOf("sh"), null, null)
            val pkg = AppContext.get().packageName
            ParcelFileDescriptor.AutoCloseOutputStream(proc.outputStream).use { out ->
                out.write("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS\nexit\n".toByteArray())
                out.flush()
            }
            proc.waitFor()
            proc.destroy()
        }
    }

    suspend fun ensureWriteSecureSettings() {
        val context = AppContext.get()
        if (!AccessibilitySettings.hasWriteSecureSettings(context)) {
            grantWriteSecureSettings()
        }
    }

    suspend fun executeShell(command: String, maxChars: Int = 4096): ShellResult = withContext(Dispatchers.IO) {
        assertNotMainThread("shizuku.executeShell")
        if (command.isBlank()) {
            return@withContext ShellResult(-1, "", "", false, "Command is blank")
        }
        if (!isBinderAlive()) {
            return@withContext ShellResult(-1, "", "", false, "Shizuku binder unavailable")
        }
        if (!hasPermission()) {
            return@withContext ShellResult(-1, "", "", false, "Shizuku permission denied")
        }
        runCatching {
            val svc = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val proc = svc.newProcess(arrayOf(if (isRoot) "su" else "sh"), null, null)
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(proc.outputStream).use { out ->
                    out.write((command + "\nexit\n").toByteArray())
                    out.flush()
                }
                val stdoutResult = StringBuilder()
                val stderrResult = StringBuilder()
                // 必须把管道一直读到 EOF，哪怕已经超上限。半途停止读取会让子进程的
                // write() 永久阻塞在满管道上，进而 proc.waitFor() 永不返回 —— 输出超过
                // 管道缓冲（~64KB）的命令（如 dumpsys）会直接把这条协程和 IO 线程挂死。
                val stdoutJob = launch { drain(proc.inputStream, stdoutResult, maxChars) }
                val stderrJob = launch { drain(proc.errorStream, stderrResult, maxChars) }

                val exitCode = withTimeoutOrNull(SHELL_TIMEOUT_MS) {
                    val code = proc.waitFor()
                    stdoutJob.join()
                    stderrJob.join()
                    code
                } ?: run {
                    proc.destroy()
                    stdoutJob.cancel()
                    stderrJob.cancel()
                    return@runCatching ShellResult(
                        -1, "", "", false, "shell command timed out after ${SHELL_TIMEOUT_MS}ms",
                    )
                }
                val stdout = stdoutResult.toString()
                val stderr = stderrResult.toString()
                ShellResult(exitCode, stdout.ifBlank { stderr }, stderr, false, "")
            } finally {
                runCatching { proc.destroy() }
            }
        }.getOrElse { t ->
            ShellResult(-1, "", "", false, t.message ?: t.javaClass.simpleName)
        }
    }

    /** 读到 EOF 为止；只保留前 [maxChars] 个字符，但不会提前停止读取 */
    private fun CoroutineScope.drain(
        fd: ParcelFileDescriptor,
        sink: StringBuilder,
        maxChars: Int,
    ) {
        runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                input.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (sink.length <= maxChars) {
                            if (sink.isNotEmpty()) sink.append('\n')
                            sink.append(line)
                        }
                        line = reader.readLine()
                    }
                }
            }
        }
    }

    // ---- Shortcut Operations ----

    /**
     * 以 shell 身份向指定用户启动 Activity，并带上 ActivityOptions（小窗 bounds 在
     * 首次显示时就生效，避免先按系统记忆尺寸出现再 resize 的闪动）。
     *
     * 显式 userId 也用于绕开 MIUI XSpace 对「主应用/分身」的二选一拦截。
     */
    suspend fun startActivityAsUser(intent: Intent, userId: Int, options: Bundle? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (!isBinderAlive()) return@withContext false
            if (!hasPermission()) return@withContext false
            runCatching {
                val result = startActivityAsUserMethod.invoke(
                    activityTaskProxy,
                    null, // caller
                    "com.android.shell", // callingPackage
                    null, // callingFeatureId
                    intent,
                    null, // resolvedType
                    null, // resultTo
                    null, // resultWho
                    0, // requestCode
                    intent.flags,
                    null, // profilerInfo
                    options,
                    userId,
                ) as? Int
                result != null && result >= 0
            }.getOrElse { t ->
                hunoia.luno.core.LunoLog.w(
                    "ShizukuManager",
                    "startActivityAsUser failed pkg=${intent.component?.packageName} user=$userId",
                    t,
                )
                false
            }
        }

    /**
     * 把已有 task 提到前台，并允许带 freeform 的 ActivityOptions。
     *
     * 与 [startActivityAsUser] 的差别：后者按 Intent 走一次「启动」，已有 task 时也可能
     * 重新走 Activity 启动流程；这里直接复用系统任务，不经过 ActivityStarter 的
     * 「后台启动 / 冻结应用」判定。小窗打开已有 task 的默认快路径用它，见
     * `Launcher.tryReuseTaskInMiniWindow`（开关：`debug.luno.mini_reuse`）。
     */
    private val startActivityFromRecentsMethod by lazy {
        HiddenApiBypass.getDeclaredMethods(Class.forName("android.app.IActivityTaskManager"))
            .filterIsInstance<Method>()
            .firstOrNull { it.name == "startActivityFromRecents" }
            ?: throw NoSuchMethodException("IActivityTaskManager.startActivityFromRecents not found")
    }

    suspend fun startActivityFromRecents(taskId: Int, options: Bundle? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (!isBinderAlive()) return@withContext false
            if (!hasPermission()) return@withContext false
            runCatching {
                val result = startActivityFromRecentsMethod.invoke(activityTaskProxy, taskId, options) as? Int
                result != null && result >= 0
            }.getOrElse { t ->
                hunoia.luno.core.LunoLog.w(
                    "ShizukuManager",
                    "startActivityFromRecents failed task=$taskId",
                    t,
                )
                false
            }
        }

    /**
     * MIUI/HyperOS 私有事务：把任务挂成「迷你小窗」。
     *
     * 系统没有公开 API，也没有 settings/prop 开关；只能按 MIUI 自己的 AIDL 事务码
     * 通过 `service call activity_task` 派发（[launchMiniFreeFormWindowCode] 反射不到时
     * 用真机验证过的 138 / 0x8a 兜底）。
     *
     * @param positionCode 1 = 左/左上，2 = 右/右上
     */
    suspend fun launchMiniFreeFormWindow(taskId: Int, positionCode: Int): Boolean =
        withContext(Dispatchers.IO) {
            if (!isBinderAlive()) return@withContext false
            if (!hasPermission()) return@withContext false
            val code = launchMiniFreeFormWindowCode
            val command = "service call activity_task $code " +
                "i32 $taskId i32 $positionCode s16 \"\" s16 \"null\""
            val result = executeShell(command, maxChars = 8_192)
            val output = (result.stdout + result.stderr).trim()
            val failed = !result.isSuccess ||
                output.lowercase().contains("fffffff") ||
                output.contains("Exception", ignoreCase = true) ||
                output.contains("Error", ignoreCase = true)
            hunoia.luno.core.LunoLog.i(
                "ShizukuManager",
                "launchMiniFreeFormWindow task=$taskId position=$positionCode " +
                    "code=$code ok=${!failed} out=$output",
            )
            !failed
        }

    /**
     * 任务当前是否已经处于「迷你小窗」态。
     *
     * 用来避免对已经在迷你态的 task 重复派发（会先被恢复全屏再挂回，产生多余动画）。
     * `miui.app.MiuiFreeFormManager` 是 MIUI 的公开浮窗 API，取不到时返回 false，
     * 调用方按普通小窗处理即可。
     */
    fun isMiniFreeFormTask(taskId: Int): Boolean = runCatching {
        val manager = Class.forName("miui.app.MiuiFreeFormManager")
        val getter = manager.getMethod(
            "getFreeFormStackInfoByStackId",
            Int::class.javaPrimitiveType,
        )
        val info = getter.invoke(null, taskId) ?: return@runCatching false
        val inMini = info.javaClass.getMethod("isInMiniFreeFormMode")
        inMini.invoke(info) as? Boolean ?: false
    }.getOrDefault(false)

    /**
     * 从 `IActivityTaskManager$Stub` 反射 MIUI 私有事务码；两个版本号字段都找不到时
     * 退回已验证的 138 (0x8a)。
     */
    private val launchMiniFreeFormWindowCode: Int by lazy {
        val stub = runCatching {
            Class.forName("android.app.IActivityTaskManager\$Stub")
        }.getOrNull()
        val names = listOf(
            "TRANSACTION_launchMiniFreeFormWindowVersion2",
            "TRANSACTION_launchMiniFreeFormWindow",
        )
        val code = stub?.let { clazz ->
            names.firstNotNullOfOrNull { name ->
                runCatching {
                    clazz.getDeclaredField(name).apply { isAccessible = true }.getInt(null)
                }.getOrNull()
            }
        }
        hunoia.luno.core.LunoLog.i(
            "ShizukuManager",
            "launchMiniFreeFormWindow transaction=${code ?: 138} " +
                if (code != null) "source=reflect" else "source=fallback",
        )
        code ?: 138
    }

    /**
     * 以 shell 身份启动快捷方式。非默认 launcher 调 `LauncherApps.startShortcut` 会被
     * 「Caller can't access shortcut information」拦住；shell 持有 ACCESS_SHORTCUTS，
     * 通过 Shizuku 代理调用即可启动应用未导出的 shortcut trampoline。
     */
    suspend fun startShortcut(packageName: String, shortcutId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isBinderAlive()) return@withContext false
        if (!hasPermission()) return@withContext false
        runCatching {
            val args: Array<Any?> = when (startShortcutMethod.parameterCount) {
                8 -> arrayOf(
                    null, // callingActivityToken
                    "com.android.shell", // callingPackage
                    packageName,
                    null, // featureId
                    shortcutId,
                    null, // sourceBounds
                    null, // startActivityOptions
                    userId,
                )
                7 -> arrayOf(
                    "com.android.shell", // callingPackage
                    packageName,
                    null, // featureId
                    shortcutId,
                    null, // sourceBounds
                    null, // startActivityOptions
                    userId,
                )
                else -> arrayOf(packageName, shortcutId, null, null, userId)
            }
            startShortcutMethod.invoke(launcherAppsProxy, *args)
            true
        }.getOrElse { t ->
            hunoia.luno.core.LunoLog.w("ShizukuManager", "startShortcut failed pkg=$packageName id=$shortcutId", t)
            false
        }
    }

    // ---- Package Operations (public, with checks) ----

    suspend fun enablePackage(
        packageName: String,
        shellFallback: Boolean = true,
    ): PackageResult = withContext(Dispatchers.IO) {
        if (!isBinderAlive()) {
            return@withContext PackageResult(false, packageName, "Shizuku binder unavailable")
        }
        if (!hasPermission()) {
            return@withContext PackageResult(false, packageName, "Shizuku permission denied")
        }
        enablePackageUnchecked(packageName, shellFallback)
    }

    /**
     * 禁用（UI 文案「冻结」）单个应用。
     *
     * 优先走 binder 的 `setApplicationEnabledSetting(DISABLED_USER)`：批量场景下比 shell
     * 快一个数量级；binder 失败时再对单包用 `pm disable-user` 兜底（批量调用方不逐包兜底，
     * 避免一次操作起几十个 shell）。
     */
    suspend fun disablePackage(
        packageName: String,
        shellFallback: Boolean = true,
    ): PackageResult = withContext(Dispatchers.IO) {
        if (!isBinderAlive()) {
            return@withContext PackageResult(false, packageName, "Shizuku binder unavailable")
        }
        if (!hasPermission()) {
            return@withContext PackageResult(false, packageName, "Shizuku permission denied")
        }
        disablePackageUnchecked(packageName, shellFallback)
    }

    // ---- Internal (unchecked, no IO wrapper) ----

    private fun enablePackageUnchecked(packageName: String, shellFallback: Boolean): PackageResult =
        setPackageEnabled(packageName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, shellFallback) {
            packageShell(packageName, enable = true)
        }

    private fun disablePackageUnchecked(packageName: String, shellFallback: Boolean): PackageResult {
        // 禁用位变化不保证进程立刻退出（尤其 MIUI/HyperOS）：先 force-stop，避免
        // 用户看到「已冻结」但应用还在后台跑。
        forceStopAppUnchecked(packageName)
        return setPackageEnabled(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            shellFallback,
        ) {
            packageShell(packageName, enable = false)
        }
    }

    private fun forceStopAppUnchecked(packageName: String): Boolean {
        return runCatching {
            amProxy
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.invoke(amProxy::class.java, amProxy, "forceStopPackage", packageName, userId)
            } else {
                amProxy::class.java.getMethod("forceStopPackage", String::class.java, Int::class.java)
                    .invoke(amProxy, packageName, userId)
            }
            true
        }.getOrDefault(false)
    }

    private inline fun setPackageEnabled(
        packageName: String,
        state: Int,
        shellFallback: Boolean,
        shell: () -> PackageResult,
    ): PackageResult = runCatching {
        if (setEnabledSetting.parameterCount == 5) {
            setEnabledSetting.invoke(pmProxy, packageName, state, 0, userId, callerPackage)
        } else {
            setEnabledSetting.invoke(pmProxy, packageName, state, 0, userId)
        }
        PackageResult(true, packageName)
    }.getOrElse { t ->
        if (shellFallback) {
            shell()
        } else {
            PackageResult(false, packageName, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun packageShell(packageName: String, enable: Boolean): PackageResult {
        val command = if (enable) {
            "pm enable $packageName"
        } else {
            "pm disable-user --user $userId $packageName"
        }
        return runCatching {
            val svc = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val proc = svc.newProcess(arrayOf(if (isRoot) "su" else "sh"), null, null)
            ParcelFileDescriptor.AutoCloseOutputStream(proc.outputStream).use {
                it.write("$command\n".toByteArray())
            }
            val exitCode = proc.waitFor()
            proc.destroy()
            if (exitCode == 0) {
                PackageResult(true, packageName)
            } else {
                PackageResult(false, packageName, "$command exit=$exitCode")
            }
        }.getOrElse { t ->
            PackageResult(false, packageName, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun snapshot(): ShizukuStatus {
        val binderAlive = isBinderAlive()
        val permissionGranted = binderAlive && hasPermission()

        return ShizukuStatus(
            binderAlive = binderAlive,
            permissionGranted = permissionGranted,
        )
    }

    private fun isBinderAlive(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    private fun hasPermission(): Boolean {
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    private fun shouldAutoRequest(status: ShizukuStatus): Boolean {
        return status.binderAlive &&
            !status.permissionGranted &&
            !autoPermissionRequested
    }

    /** shell 命令最长等待时间。超时后强制 destroy 进程并放弃结果，避免 IO 线程被永久占住。 */
    private const val SHELL_TIMEOUT_MS = 30_000L
}
