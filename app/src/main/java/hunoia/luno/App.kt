package hunoia.luno

import android.app.Application
import android.content.Context
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.aaron.compose.component.UDFComponentDefaults
import hunoia.luno.ui.UDFComponentDefaultsImpl
import hunoia.luno.core.AppContext
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.core.CrashHandler
import hunoia.luno.core.Events
import hunoia.luno.bridge.ui.isMainThread
import hunoia.luno.bridge.vibration.initVibrationContext
import hunoia.luno.quicklaunch.icon.AppIconFetcherFactory
import hunoia.luno.quicklaunch.icon.AppIconKeyer
import hunoia.luno.quicklaunch.icon.AppIconModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.weishu.reflection.Reflection
import rikka.shizuku.ShizukuProvider

class App : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // 150ms 足够掩盖解码；几十张图标同时淡入时，再长会掉帧。
            .crossfade(150)
            // 应用图标交给 Coil 缓存，不另设常驻 Drawable 缓存。
            .components {
                add(AppIconFetcherFactory(), AppIconModel::class.java)
                add(AppIconKeyer, AppIconModel::class.java)
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // 暂无图标位图常驻缓存，先留 10%，待 meminfo 实测调整。
                    .maxSizePercent(0.10)
                    .build()
            }
            .build()
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val applicationScope: CoroutineScope get() = AppContext.applicationScope ?: scope
    }

    private var isProviderProcess = true

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        isProviderProcess = base?.let { currentProcessName(it) == it.packageName } ?: true
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
        val appScope = AppContext.applicationScope ?: error("ApplicationScope not initialized")
        Events.initScope(appScope) { isMainThread() }
        initVibrationContext(applicationContext)
        DensityProvider.init(applicationContext)

        UDFComponentDefaults.set(UDFComponentDefaultsImpl())

        // 两进程都连分发 Binder：主进程靠它转发，:service 靠它自查就绪。
        hunoia.luno.service.DispatchClient.bind(applicationContext)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // 进程级搜索索引保留，但拼音索引/图标位图是大头；内存吃紧时退回小上限。
            hunoia.luno.quicklaunch.query.AppSearch.trimPinyinCache(1_000)
            hunoia.luno.quicklaunch.icon.SearchIconCache.clear()
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

    private fun currentProcessName(context: Context): String? {
        return getProcessName()
    }
}
