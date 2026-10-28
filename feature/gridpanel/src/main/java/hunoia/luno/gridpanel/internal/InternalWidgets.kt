package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import hunoia.luno.gridpanel.R

/**
 * 内置小部件（Compose 自绘，不走 AppWidgetHost）的定义与内容。
 * 不挂整格级手势：编辑态拖拽/缩放由层根 gridLayerGestures 接管。
 */

/** 内置小部件的持久化 id（存进 [hunoia.luno.config.model.GridPanelItem.Kind.Internal.widgetId]）。 */
object InternalWidgetIds {
    const val MEDIA = "internal.media"
    const val VOLUME = "internal.volume"
    const val BRIGHTNESS = "internal.brightness"
    const val RECENTS = "internal.recents"
    /** 页面类部件：整块可滚动的剪贴板历史（默认跨格，内部纵向滚动）。 */
    const val CLIPBOARD = "internal.clipboardPage"
    /** 页面类部件：整块可滚动的应用抽屉（与快速启动器共用应用列表）。 */
    const val APP_DRAWER = "internal.appDrawer"
}

/** 一个内置小部件的选择页条目与落格默认值。 */
data class InternalWidgetDef(
    val id: String,
    val labelRes: Int,
    val spanX: Int,
    val spanY: Int,
)

/** 选择页「Luno 内置」分区的清单。 */
val InternalWidgetDefs = listOf(
    InternalWidgetDef(InternalWidgetIds.MEDIA, R.string.grid_panel_internal_media, 4, 2),
    InternalWidgetDef(InternalWidgetIds.VOLUME, R.string.grid_panel_internal_volume, 4, 1),
    InternalWidgetDef(InternalWidgetIds.BRIGHTNESS, R.string.grid_panel_internal_brightness, 4, 1),
    InternalWidgetDef(InternalWidgetIds.RECENTS, R.string.grid_panel_internal_recents, 4, 1),
    InternalWidgetDef(InternalWidgetIds.CLIPBOARD, R.string.grid_panel_internal_clipboard, 4, 3),
    InternalWidgetDef(InternalWidgetIds.APP_DRAWER, R.string.grid_panel_internal_app_drawer, 4, 4),
)

/**
 * 竖排判定：spanY > spanX 时竖排；正方形保持横排。
 * 滞回下界 [VerticalHysteresis]：resize 时 span 比会来回穿过 1.0，无滞回会反复翻面。
 * 比的是自身占位而非面板行列，否则默认 4 列时横排分支形同虚设。
 */
internal const val VerticalHysteresis = 0.85f

@Composable
internal fun rememberVerticalSpan(spanX: Int, spanY: Int): Boolean {
    var last by remember { mutableStateOf(spanY > spanX) }
    val ratio = spanY.toFloat() / spanX.coerceAtLeast(1).toFloat()
    val vertical = if (last) ratio > VerticalHysteresis else ratio > 1f
    SideEffect { last = vertical }
    return vertical
}

/**
 * 内置部件的最小占位。媒体部件最小 2×1：一格宽（内区约 32dp）连歌名都放不下，
 * 缩放手柄直接不让人拖到那么窄。
 */
fun internalWidgetMinSpan(widgetId: String): Pair<Int, Int> =
    when (widgetId) {
        // 剪贴板列表：一格宽放不下条目文字，两格起步。
        InternalWidgetIds.CLIPBOARD -> 2 to 2
        // 应用抽屉：1×1 只有一个图标，堆不下有意义的列表。
        InternalWidgetIds.APP_DRAWER -> 2 to 2
        InternalWidgetIds.MEDIA -> 2 to 1
        else -> 1 to 1
    }

/**
 * 滑条所在容器的内边距。格子是方格（cellH == cellW），横向的上下内边距必须与
 * 竖向的左右内边距等量（都 8dp）：不然 1×4 的轨道比 4×1 的粗一圈，两个方向
 * 放一起看很别扭。竖向格只有一格宽（1×4 约 52dp），横向按 4dp 给 —— 早先留 10dp
 * 会吃掉三分之一轨道，「没吃满宽度」的真凶是这里，不是 fill。
 */
internal fun sliderPadding(vertical: Boolean): PaddingValues = if (vertical) {
    PaddingValues(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
} else {
    PaddingValues(horizontal = 8.dp, vertical = 4.dp)
}
