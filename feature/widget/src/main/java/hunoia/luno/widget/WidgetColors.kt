package hunoia.luno.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * 主题色加 alpha 必须保留日/夜两套值。
 *
 * 不能 `ColorProvider(getColor(context).copy(alpha = a))`：那是 FixedColorProvider，
 * Glance 翻译成固定 `setTextColor(int)` / `setInt("setBackgroundColor")`，
 * 系统深浅色切换时不会重新解析，只能等下一次部件刷新（30 分钟 / 数据变化）。
 *
 * DayNightColorProvider 在 API 31+ 会写成 `RemoteViews.setColorInt(day, night)`，
 * 宿主切换 uiMode 时即时生效。
 */
internal fun ColorProvider.alpha(context: Context, a: Float): ColorProvider {
    if (a >= 1f) return this
    val day = getColor(context.withNightMode(night = false)).copy(alpha = a)
    val night = getColor(context.withNightMode(night = true)).copy(alpha = a)
    return androidx.glance.color.ColorProvider(day, night)
}

/** 可空主题色 + alpha：null 视为透明（模式无关）。 */
internal fun ColorProvider?.withAlpha(context: Context, a: Float): ColorProvider = when {
    this == null -> ColorProvider(Color.Transparent)
    a >= 1f -> this
    else -> alpha(context, a)
}

/** 用指定夜间模式重建 Context，用来同时解析动态色的日/夜值。 */
private fun Context.withNightMode(night: Boolean): Context {
    val config = Configuration(resources.configuration)
    val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
    return createConfigurationContext(config)
}
