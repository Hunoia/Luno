package hunoia.luno

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.StrictMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.aaron.compose.component.UDFComponentDefaults
import hunoia.luno.ui.UDFComponentDefaultsImpl
import hunoia.luno.core.AppContext
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.core.CrashHandler
import hunoia.luno.core.Events
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import hunoia.luno.bridge.ui.isMainThread
import hunoia.luno.bridge.vibration.initVibrationContext
import hunoia.luno.quickaccess.icon.AppIconFetcherFactory
import hunoia.luno.quickaccess.icon.AppIconKeyer
import hunoia.luno.ui.theme.toProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.weishu.reflection.Reflection
import rikka.shizuku.ShizukuProvider

class App : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            // Coil 3 去掉了 ImageLoader 级别的全局 crossfade（改成按请求配置）；
            // 应用图标本来就是同步命中内存缓存，淡入收益不大，这里不再全局开。
            // 应用图标交给 Coil 缓存，不另设常驻 Drawable 缓存。
            .components {
                add(AppIconFetcherFactory())
                add(AppIconKeyer)
            }
            .memoryCache {
                MemoryCache.Builder()
                    // 暂无图标位图常驻缓存，先留 10%，待 meminfo 实测调整。
                    .maxSizePercent(context, 0.10)
                    .build()
            }
            .build()
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    private var isProviderProcess = true

    /** 上次记录的夜间模式，用来过滤非深浅色配置变化（旋转 / 字体等）。 */
    private var lastNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        isProviderProcess = base?.let { getProcessName() == it.packageName } ?: true
        ShizukuProvider.enableMultiProcessSupport(isProviderProcess)
        if (!isProviderProcess && base != null) {
            ShizukuProvider.requestBinderForNonProviderProcess(base)
        }
        Reflection.unseal(base)
    }

    override fun onCreate() {
        super.onCreate()
        lastNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (BuildConfig.DEBUG) enableStrictMode()
        AppContext.init(applicationContext, scope)
        LunoLog.i(
            LOG_TAG,
            "app: onCreate process=${Application.getProcessName()} pid=${android.os.Process.myPid()}",
        )
        val appScope = AppContext.applicationScope ?: error("ApplicationScope not initialized")
        // 剪贴板历史上次进程崩溃可能留下无条目引用的图片副本：启动时清一次。
        appScope.launch(Dispatchers.IO) {
            runCatching { hunoia.luno.clipboard.ClipboardCenter.onProcessStart() }
        }
        Events.initScope(appScope) { isMainThread() }
        // 桌面小部件实时同步（architecture §7.5）：进程启动即订阅配置流。
        // receiver onUpdate 只在放置/周期更新触发，进程重启后不保证重发，故在此兜底。
        hunoia.luno.widget.WidgetSync.ensureStarted(applicationContext)
        // 习惯提醒：闹钟 / 部件打卡等入口可能在 MainActivity 未打开时拉起主进程，
        // 在这里订阅配置流才能保证「关提醒 / 打卡 / 删习惯」后立即撤销在屏通知与闹钟。
        if (isProviderProcess) {
            hunoia.luno.domain.habit.HabitReminderScheduler.startObserving(applicationContext)
            // 待办提醒同理：配置流一变更（设 / 改 / 删 / 完成 / 撤销）就重排，
            // 首次订阅同时负责「应用打开时补弹错过提醒」。
            hunoia.luno.domain.todo.TodoReminderScheduler.startObserving(applicationContext)
        }
        // Surface 动作统一进 Dispatcher：进程级门面不依赖无障碍服务生命周期。
        hunoia.luno.runtime.SurfaceActionRuntime.install(applicationContext)
        // 动作编辑页的 Activity 选择器：契约在 :core:domain，实现在 :app。
        hunoia.luno.runtime.dispatch.ActivityOptions.install(
            hunoia.luno.runtime.QuickLaunchActivityOptionsSource(applicationContext),
        )
        // 手势/子手势删除后的孤儿资源回收：实现在 :feature:gridpanel，调用点在 :feature:gesture，
        // 直接依赖会成环，经 :core:runtime 的钩子注入。
        hunoia.luno.runtime.maintenance.PanelMaintenance.cleanupOrphanResources = {
            hunoia.luno.gridpanel.PanelResources.cleanup()
        }
        initVibrationContext(applicationContext)
        DensityProvider.init(applicationContext)
        // 动效档位：两进程都要生效（主进程 Compose + :service 原生动效），进程启动即订阅。
        hunoia.luno.domain.tune.TuneConfig.advanced
            .onEach { hunoia.luno.ui.theme.MotionTuning.profile = it.motion.toProfile() }
            .launchIn(appScope)

        UDFComponentDefaults.set(UDFComponentDefaultsImpl())

        // 两进程都连分发 Binder：主进程靠它转发，:service 靠它自查就绪。
        // 执行口不认识宿主组件，服务类与同进程就绪判断由这里注入。
        hunoia.luno.service.DispatchClient.serviceClass =
            hunoia.luno.service.DispatchService::class.java
        hunoia.luno.service.DispatchClient.localReady = {
            hunoia.luno.service.SideGestureService.current?.isDispatchReady() == true
        }
        hunoia.luno.service.DispatchClient.bind(applicationContext)
        // 链接分发的应用内导航（规则编辑页 / 选择页）需要 context。
        hunoia.luno.link.AppLinkPorts.context = applicationContext
        // OCR 模型页要扫模型目录、并在导入/删除后通知 :service 重载引擎。
        hunoia.luno.domain.ocr.OcrModelCenter.init(applicationContext)

        // 启动能力库的协作者注入（architecture §9.4）：能力库自己不认识配置流与查询层。
        hunoia.luno.search.SearchCapabilities.actionSearch = hunoia.luno.actions.LunoActionSearchSource
        hunoia.luno.launch.LaunchCapability.telemetry = hunoia.luno.config.DataLaunchTelemetry
        // 冻结/解冻写入成功后显式重扫应用清单：包广播是兜底，部分 ROM 对 pm disable 不发。
        hunoia.luno.launch.LaunchCapability.onPackageStateChanged = {
            hunoia.luno.quickaccess.query.AppListRepository.refresh()
        }
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        // 无障碍服务进程崩溃后系统会重启进程（PID 变）但常不重连连接；
        // 主进程/:service 进程启动时检测最近崩溃，用 Shizuku 权限强制重绑（免手动重开）。
        hunoia.luno.service.AccessibilityCrashRecovery.tryRecover(applicationContext)
    }

    /**
     * 深浅色切换时主动重绘桌面部件：不依赖启动器是否重新 apply RemoteViews。
     * 只在夜间模式真正变化时触发，旋转 / 字体缩放等配置变化直接跳过。
     * 非主进程不刷新（WorkManager 只在主进程自动初始化）。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightMode == lastNightMode) return
        lastNightMode = nightMode
        if (isProviderProcess) {
            hunoia.luno.widget.WidgetSync.refreshAll(applicationContext)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // 进程级搜索索引保留，但拼音索引/图标位图是大头；内存吃紧时退回小上限。
            hunoia.luno.quickaccess.query.AppSearch.trimPinyinCache(1_000)
            hunoia.luno.ui.icon.SearchIconCache.clear()
        }
    }

    /** Debug 主线程看门狗：penaltyLog 只记日志，用于曝光主线程 IO/慢调用。 */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build(),
        )
    }
}
