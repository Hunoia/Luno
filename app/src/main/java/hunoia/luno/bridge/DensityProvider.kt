package hunoia.luno.bridge

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt

object DensityProvider {

    private var displayMetrics: DisplayMetrics? = null
    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
        displayMetrics = context.resources.displayMetrics
    }

    val density: Float get() = displayMetrics?.density ?: 1f

    val densityDpi: Int get() = displayMetrics?.densityDpi ?: DisplayMetrics.DENSITY_DEFAULT

    val screenWidthPx: Int get() = applicationContext?.realScreenWidthPx ?: displayMetrics?.widthPixels ?: 0

    val screenHeightPx: Int get() = applicationContext?.realScreenHeightPx ?: displayMetrics?.heightPixels ?: 0

    fun dp2px(dp: Float): Int = (dp * density).roundToInt()

    fun dp2px(dp: Int): Int = (dp * density).roundToInt()

    private val Context.realScreenWidthPx: Int
        get() = getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds?.width() ?: 0

    private val Context.realScreenHeightPx: Int
        get() = getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds?.height() ?: 0
}
