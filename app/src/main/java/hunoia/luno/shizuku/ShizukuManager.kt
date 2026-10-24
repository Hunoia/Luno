package hunoia.luno.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import hunoia.luno.BuildConfig
import hunoia.luno.bridge.system.AccessibilitySettings
import hunoia.luno.core.AppContext
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
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuManager {

    private const val RequestCode = 41051

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
    private val callerPackage get() = if (isRoot) "com.android.shell" else BuildConfig.APPLICATION_ID

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

    private val amProxy by lazy {
        val stubClass = Class.forName("android.app.IActivityManager\$Stub")
        val binder = SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
        HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder))
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

    internal fun release() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

    // ---- Shell Execution ----

    private suspend fun grantWriteSecureSettings() = withContext(Dispatchers.IO) {
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

    internal suspend fun ensureWriteSecureSettings() {
        val context = AppContext.get()
        if (!AccessibilitySettings.hasWriteSecureSettings(context)) {
            grantWriteSecureSettings()
        }
    }

    suspend fun executeShell(command: String): ShellResult = withContext(Dispatchers.IO) {
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
                val maxChars = 4096
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

    // ---- Package Operations (public, with checks) ----

    suspend fun enablePackage(packageName: String): PackageResult = withContext(Dispatchers.IO) {
        if (!isBinderAlive()) {
            return@withContext PackageResult(false, packageName, "Shizuku binder unavailable")
        }
        if (!hasPermission()) {
            return@withContext PackageResult(false, packageName, "Shizuku permission denied")
        }
        enablePackageUnchecked(packageName)
    }

    // ---- Internal (unchecked, no IO wrapper) ----

    private fun enablePackageUnchecked(packageName: String): PackageResult {
        return runCatching {
            val state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            if (setEnabledSetting.parameterCount == 5) {
                setEnabledSetting.invoke(pmProxy, packageName, state, 0, userId, callerPackage)
            } else {
                setEnabledSetting.invoke(pmProxy, packageName, state, 0, userId)
            }
            PackageResult(true, packageName)
        }.getOrElse {
            enablePackageShell(packageName)
        }
    }

    private fun enablePackageShell(packageName: String): PackageResult {
        return runCatching {
            val svc = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val proc = svc.newProcess(arrayOf(if (isRoot) "su" else "sh"), null, null)
            ParcelFileDescriptor.AutoCloseOutputStream(proc.outputStream).use {
                it.write("pm enable $packageName\n".toByteArray())
            }
            val exitCode = proc.waitFor()
            proc.destroy()
            if (exitCode == 0) {
                PackageResult(true, packageName)
            } else {
                PackageResult(false, packageName, "pm enable exit=$exitCode")
            }
        }.getOrElse { t ->
            PackageResult(false, packageName, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun snapshot(): ShizukuStatus {
        val binderAlive = isBinderAlive()
        val permissionGranted = binderAlive && hasPermission()
        val uid = if (binderAlive) runCatching { Shizuku.getUid() }.getOrNull() else null

        return ShizukuStatus(
            binderAlive = binderAlive,
            permissionGranted = permissionGranted,
            uid = uid
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
