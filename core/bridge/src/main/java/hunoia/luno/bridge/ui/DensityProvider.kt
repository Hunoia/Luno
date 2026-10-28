package hunoia.luno.bridge.ui

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt

object DensityProvider {

    private var displayMetrics: DisplayMetrics? = null
    private var applicationContext: Context? = null

    // maximumWindowMetrics 每次构造都要过一次 WMS（分配 Rect/WindowMetrics），
    // 拖球/动画的逐帧路径用不起，缓存到全局配置变化（旋转/折叠/分屏）才失效。
    @Volatile private var cachedScreenWidthPx = 0
    @Volatile private var cachedScreenHeightPx = 0

    fun init(context: Context) {
        applicationContext = context.applicationContext
        displayMetrics = context.resources.displayMetrics
        applicationContext?.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                cachedScreenWidthPx = 0
                cachedScreenHeightPx = 0
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() {}
        })
    }

    val density: Float get() = displayMetrics?.density ?: 1f

    val densityDpi: Int get() = displayMetrics?.densityDpi ?: DisplayMetrics.DENSITY_DEFAULT

    val screenWidthPx: Int
        get() {
            val cached = cachedScreenWidthPx
            if (cached > 0) return cached
            val width = applicationContext?.realScreenWidthPx ?: displayMetrics?.widthPixels ?: 0
            // 0（init 前的兜底值）不缓存，否则会把启动早期的假值钉死。
            if (width > 0) cachedScreenWidthPx = width
            return width
        }

    val screenHeightPx: Int
        get() {
            val cached = cachedScreenHeightPx
            if (cached > 0) return cached
            val height = applicationContext?.realScreenHeightPx ?: displayMetrics?.heightPixels ?: 0
            if (height > 0) cachedScreenHeightPx = height
            return height
        }

    fun dp2px(dp: Float): Int = (dp * density).roundToInt()

    fun dp2px(dp: Int): Int = (dp * density).roundToInt()

    private val Context.realScreenWidthPx: Int
        get() = getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds?.width() ?: 0

    private val Context.realScreenHeightPx: Int
        get() = getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds?.height() ?: 0
}
