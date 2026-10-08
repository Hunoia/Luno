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
import hunoia.luno.bridge.ui.initToastScope
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
            // 原来 600ms：图标网格一次加载几十张，每张都产生一段 GPU 交叉淡入，
            // 弹键盘/切页时叠加起来既掉帧又耗电。150ms 足够掩盖解码但成本低得多。
            .crossfade(150)
            // 快速启动器的应用图标交给 Coil 自己缓存（见 AppIconFetcher）：
            // 原生尺寸的 Drawable 不再另有一层常驻缓存。
            .components {
                add(AppIconFetcherFactory(), AppIconModel::class.java)
                add(AppIconKeyer, AppIconModel::class.java)
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // 原来是 0.2，因为图标另有一层 IconResizeCache 常驻原生尺寸。
                    // 那层已删除，但数值先不动，等 meminfo 实测后再调。
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
        initToastScope(appScope)
        initVibrationContext(applicationContext)
        DensityProvider.init(applicationContext)

        UDFComponentDefaults.set(UDFComponentDefaultsImpl())

        // 两进程都连分发 Binder：主进程靠它转发，:service 靠它自查就绪。
        hunoia.luno.service.DispatchClient.bind(applicationContext)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
    }

    /**
     * Debug 包主线程看门狗：penaltyLog 不打断执行，只在 logcat 打违规，
     * 让"主线程 IO / 慢调用"（如 MB 级文本在 UI 线程排版数秒导致 ANR）
     * 在开发期就暴露，而不是等用户端 ANR 被杀进程才发现。
     */
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
