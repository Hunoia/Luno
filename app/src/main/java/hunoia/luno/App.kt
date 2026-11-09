package hunoia.luno

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        hunoia.luno.launch.LaunchCapability.targetResolver =
            hunoia.luno.core.domain.LaunchTargetResolver { packageName ->
                hunoia.luno.quickaccess.query.AppQuery
                    .findLauncherActivity(applicationContext, packageName)
                    ?.let { hunoia.luno.core.domain.LaunchTarget(it.packageName, it.className) }
            }
        hunoia.luno.launch.LaunchCapability.disabledApps =
            object : hunoia.luno.core.domain.DisabledAppState {
                override fun isDisabled(packageName: String): Boolean =
                    hunoia.luno.quickaccess.query.DisabledAppQuery
                        .isDisabled(applicationContext, packageName)

                override fun markEnabled(packageName: String) {
                    hunoia.luno.quickaccess.query.DisabledAppQuery.markEnabled(packageName)
                }

                override fun invalidateLauncherCache() {
                    hunoia.luno.quickaccess.query.AppQuery.invalidateLauncherCache()
                }
            }

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
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
