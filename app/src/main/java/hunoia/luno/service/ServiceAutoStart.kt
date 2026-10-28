package hunoia.luno.service

import android.content.Context
import android.content.Intent
import android.util.Log
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.store.DataStoreFiles
import hunoia.luno.config.store.dataStoreFile
import hunoia.luno.core.AppContext
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.runtime.floatball.FloatBallService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import hunoia.luno.core.LOG_TAG

/**
 * 常驻前台服务「按落盘配置自动拉起」的单一判定入口。
 *
 * 开机（[BootReceiver]）与应用更新（[PackageReplacedReceiver]）走同一套读法：
 * 两处各写一份判断迟早漂移 —— 开机拉起、更新后不拉起，症状就是更新后悬浮球消失。
 *
 * 读的是 DataStore 文件本体（不是 DataStore API）：广播回调是同步的，
 * 而 DataStore 的读是挂起的，且多进程 DataStore 在别的进程持锁时会阻塞。
 */
internal object ServiceAutoStart {

    private const val TAG = LOG_TAG

    /** 保活开关与 [DaemonService] 共用同一份 prefs（见 `KeepAliveUseCase`）。 */
    private const val PREFS_NAME = "daemon"
    private const val KEY_KEEP_ALIVE = "keep_alive"

    /**
     * 按落盘配置把该起的常驻前台服务拉起来（读盘 + 全量 JSON 反序列化在 IO 线程）。
     * 调用方（receiver 的 onReceive、Activity 冷启动）都在主线程，此前同步读两份
     * 完整设置文件是启动路径上的主线程卡顿源；拉起服务没有亚秒级时序要求，挪后台无感。
     * 各服务自己判权限与总闸，不满足条件时自己退出（见 [DaemonService.onStartCommand]）。
     */
    fun restore(context: Context) {
        val app = context.applicationContext
        val scope = AppContext.applicationScope
        if (scope != null) {
            scope.launch(Dispatchers.IO) { restoreBlocking(app) }
        } else {
            // scope 缺失（异常初始化环境）退回原地同步执行，保行为不变。
            runCatching { restoreBlocking(app) }
        }
    }

    private fun restoreBlocking(context: Context) {
        if (readKeepAliveEnabled(context)) startDaemon(context)
        if (readMasterSwitchEnabled(context) && readFloatBallEnabled(context)) FloatBallService.start(context)
    }

    /**
     * 配置整写（备份恢复 / 恢复默认）后的运行态对齐。
     *
     * 恢复只落 DataStore，而保活的判定源是 prefs —— 不同步的话，恢复「保活开」后 UI
     * 显示开、开机/更新自启却不会拉起守护（反向亦然）。这里先把 prefs 对齐成恢复后的值，
     * 再按恢复后的配置：该关的关（服务自己管窗口/前台通知），该开的拉起，最后走常规恢复兜底。
     */
    fun syncAfterConfigRestore(context: Context) {
        val app = context.applicationContext
        val keepAlive = readSettings<AdvancedSettings>(app, DataStoreFiles.ADVANCED_SETTINGS)
            ?.keepAliveEnabled ?: false
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_ALIVE, keepAlive)
            .apply()
        if (!keepAlive) app.stopService(Intent(app, DaemonService::class.java))
        if (!readMasterSwitchEnabled(app) || !readFloatBallEnabled(app)) {
            FloatBallService.stop(app)
        }
        restore(app)
    }

    private val restored = AtomicBoolean(false)

    /**
     * 进程内只执行一次的 [restore]，给主进程 Activity 启动路径兜底：
     * MIUI/HyperOS 未授「自启动」时会拦掉 MY_PACKAGE_REPLACED / BOOT_COMPLETED 的
     * 进程冷启动（BroadcastQueueInjector: process is not permitted to auto start），
     * 接收器根本没机会跑；而用户点开应用不受此限 —— 主进程首次启动时补一次恢复。
     */
    fun restoreOnce(context: Context) {
        if (!restored.compareAndSet(false, true)) return
        Log.i(TAG, "process start fallback: restoring services")
        restore(context)
    }

    private fun startDaemon(context: Context) {
        // DaemonService 是 specialUse 前台服务，必须用 startForegroundService：
        // Android 12+ 用 startService 拉前台服务会抛 ForegroundServiceStartNotAllowedException，
        // 且服务必须在 5s 内调 startForeground，否则崩溃。
        runCatching {
            context.startForegroundService(Intent(context, DaemonService::class.java))
        }.onFailure {
            Log.w(TAG, "start daemon service FAILED: ${it.message}")
        }
    }

    /** 总开关（手势总闸）：默认 true，DataStore 缺文件时生效的就是它，读失败按「开」处理。 */
    fun readMasterSwitchEnabled(context: Context): Boolean =
        readSettings<InitialSettings>(context, DataStoreFiles.INITIAL_SETTINGS)
            ?.gestureEnabled ?: true

    /**
     * 悬浮球服务要不要起：球开关、通知胶囊或剪贴板球任一开启（FloatBallService 是共同宿主）。
     * 读失败按「不开」处理：拉起路径抛异常会连带影响保活服务。
     */
    fun readFloatBallEnabled(context: Context): Boolean =
        readSettings<ActionSettings>(context, DataStoreFiles.ACTION_SETTINGS)?.floatBall?.let {
            it.enabled || it.notificationCapsule.enabled || it.clipboardBall.enabled
        } ?: false

    /** 保活开关（SharedPreferences，与 `KeepAliveUseCase` 同一份）。 */
    fun readKeepAliveEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_ALIVE, false)
    }.getOrDefault(false)

    /** 读 DataStore 文件并反序列化；文件缺失/内容损坏返回 null（调用方按各自默认处理）。 */
    private inline fun <reified T> readSettings(context: Context, fileName: String): T? {
        val json = runCatching {
            val file = context.dataStoreFile(fileName)
            if (!file.exists()) return@runCatching ""
            file.readText()
        }.getOrDefault("")
        if (json.isBlank()) return null
        return runCatching { JsonSerializer.decodeFromString<T>(json) }.getOrNull()
    }
}
