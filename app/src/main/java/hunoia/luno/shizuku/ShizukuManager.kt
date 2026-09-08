package hunoia.luno.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import hunoia.luno.BuildConfig
import hunoia.luno.bridge.accessibility.AccessibilitySettings
import hunoia.luno.core.AppContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuManager {

    private const val RequestCode = 41051
    private const val ShizukuPackageName = "moe.shizuku.privileged.api"

    private val autoPermissionMutex = Mutex()
    private val permissionMutex = Mutex()
    private val statusMutableStateFlow = MutableStateFlow(snapshot())

    @Volatile
    private var permissionResult: CompletableDeferred<Boolean>? = null

    @Volatile
    private var autoPermissionRequested = false

    private var installedShizuku: Boolean? = null

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

    // --- Hidden API proxies ---

    private val pmClass by lazy { Class.forName("android.content.pm.IPackageManager") }

    private val pmProxy by lazy {
        val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
        val binder = SystemServiceHelper.getSystemService("package")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder))
        } else {
            stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
        }
    }

    private val amProxy by lazy {
        val stubClass = Class.forName("android.app.IActivityManager\$Stub")
        val binder = SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder))
        } else {
            stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
        }
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

    // --- Status ---

    val statusFlow: StateFlow<ShizukuStatus> = statusMutableStateFlow.asStateFlow()

    fun currentStatus(): ShizukuStatus = statusMutableStateFlow.value

    fun updateStatus() {
        installedShizuku = null
        statusMutableStateFlow.value = snapshot()
    }

    internal fun release() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    // --- Permission ---

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

    // --- Shell Execution ---

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
            ParcelFileDescriptor.AutoCloseOutputStream(proc.outputStream).use { out ->
                out.write((command + "\nexit\n").toByteArray())
                out.flush()
            }
            val stdoutResult = StringBuilder()
            val stderrResult = StringBuilder()
            val maxChars = 4096
            val stdoutJob = launch {
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(proc.inputStream).use { input ->
                        input.bufferedReader().use { reader ->
                            var line = reader.readLine()
                            while (line != null && stdoutResult.length <= maxChars) {
                                if (stdoutResult.isNotEmpty()) stdoutResult.append('\n')
                                stdoutResult.append(line)
                                line = reader.readLine()
                            }
                        }
                    }
                }
            }
            val stderrJob = launch {
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(proc.errorStream).use { input ->
                        input.bufferedReader().use { reader ->
                            var line = reader.readLine()
                            while (line != null && stderrResult.length <= maxChars) {
                                if (stderrResult.isNotEmpty()) stderrResult.append('\n')
                                stderrResult.append(line)
                                line = reader.readLine()
                            }
                        }
                    }
                }
            }
            val exitCode = proc.waitFor()
            stdoutJob.join()
            stderrJob.join()
            proc.destroy()
            val stdout = stdoutResult.toString().take(maxChars)
            val stderr = stderrResult.toString().take(maxChars)
            val output = stdout.ifBlank { stderr }
            ShellResult(exitCode, output, stderr, false, "")
        }.getOrElse { t ->
            ShellResult(-1, "", "", false, t.message ?: t.javaClass.simpleName)
        }
    }

    // --- Package Operations (public, with checks) ---

    suspend fun forceStopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        forceStopAppUnchecked(packageName)
    }

    suspend fun disablePackage(packageName: String): PackageResult = withContext(Dispatchers.IO) {
        if (!isBinderAlive()) {
            return@withContext PackageResult(false, packageName, "Shizuku binder unavailable")
        }
        if (!hasPermission()) {
            return@withContext PackageResult(false, packageName, "Shizuku permission denied")
        }
        disablePackageUnchecked(packageName)
    }

    suspend fun enablePackage(packageName: String): PackageResult = withContext(Dispatchers.IO) {
        if (!isBinderAlive()) {
            return@withContext PackageResult(false, packageName, "Shizuku binder unavailable")
        }
        if (!hasPermission()) {
            return@withContext PackageResult(false, packageName, "Shizuku permission denied")
        }
        enablePackageUnchecked(packageName)
    }

    suspend fun executeBatch(
        packageNames: List<String>,
        disable: Boolean
    ): BatchFrozenResult {
        val requestedCount = packageNames.size
        if (packageNames.isEmpty()) return BatchFrozenResult(0, 0, 0, 0, false)

        pmProxy
        amProxy
        setEnabledSetting

        return coroutineScope {
            val deferreds = packageNames.map { pkg ->
                async(Dispatchers.IO) {
                    if (disable) disablePackageUnchecked(pkg) else enablePackageUnchecked(pkg)
                }
            }
            var successCount = 0
            var failedCount = 0
            deferreds.forEach {
                val r = it.await()
                if (r.success) successCount++ else failedCount++
            }
            BatchFrozenResult(
                requestedCount = requestedCount,
                attemptedCount = packageNames.size,
                successCount = successCount,
                failedCount = failedCount,
                fallbackTriggered = false
            )
        }
    }

    // --- Internal (unchecked, no IO wrapper) ---

    private fun forceStopAppUnchecked(packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.invoke(amProxy::class.java, amProxy, "forceStopPackage", packageName, userId)
            } else {
                amProxy::class.java.getMethod("forceStopPackage", String::class.java, Int::class.java)
                    .invoke(amProxy, packageName, userId)
            }
            true
        }.getOrElse { false }
    }

    private fun disablePackageUnchecked(packageName: String): PackageResult {
        forceStopAppUnchecked(packageName)
        return runCatching {
            val state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            if (setEnabledSetting.parameterCount == 5) {
                setEnabledSetting.invoke(pmProxy, packageName, state, 0, userId, callerPackage)
            } else {
                setEnabledSetting.invoke(pmProxy, packageName, state, 0, userId)
            }
            PackageResult(true, packageName)
        }.getOrElse { t ->
            PackageResult(false, packageName, t.message ?: t.javaClass.simpleName)
        }
    }

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
        val context = runCatching { AppContext.get() }.getOrNull()
        if (context == null) {
            return ShizukuStatus(
                installed = false, binderAlive = false,
                permissionGranted = false, uid = null
            )
        }
        val binderAlive = isBinderAlive()
        val permissionGranted = binderAlive && hasPermission()
        val uid = if (binderAlive) runCatching { Shizuku.getUid() }.getOrNull() else null
        val installed = installedShizuku ?: runCatching {
            context.packageManager.getPackageInfo(ShizukuPackageName, 0)
            true
        }.getOrDefault(false).also { installedShizuku = it }

        return ShizukuStatus(
            installed = installed,
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
        return status.installed &&
            status.binderAlive &&
            !status.permissionGranted &&
            !autoPermissionRequested
    }
}
