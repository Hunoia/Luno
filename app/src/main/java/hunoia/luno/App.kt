package hunoia.luno

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.aaron.compose.component.UDFComponentDefaults
import hunoia.luno.ui.UDFComponentDefaultsImpl
import hunoia.luno.core.AppContext
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.core.CrashHandler
import hunoia.luno.core.Events
import hunoia.luno.bridge.isMainThread
import hunoia.luno.bridge.feedback.initToastScope
import hunoia.luno.bridge.vibration.initVibrationContext
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
            .memoryCache {
                MemoryCache.Builder(this)
                    // 默认是 0.2；这里图标本身还有 IconResizeCache 那层，
                    // 没必要让 Coil 再吃掉四分之一堆内存。
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
        AppContext.init(applicationContext, scope)
        val appScope = AppContext.applicationScope ?: error("ApplicationScope not initialized")
        Events.initScope(appScope) { isMainThread() }
        initToastScope(appScope)
        initVibrationContext(applicationContext)
        DensityProvider.init(applicationContext)

        UDFComponentDefaults.set(UDFComponentDefaultsImpl())

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
    }

    private fun currentProcessName(context: Context): String? {
        return getProcessName()
    }
}
