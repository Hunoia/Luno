package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hunoia.luno.gridpanel.R

/** 内置部件渲染时拿到的上下文。 */
data class PanelWidgetContentScope(
    val modifier: Modifier,
    /** 当前实时 span（resize 跟手）。 */
    val spanX: Int,
    val spanY: Int,
    /** 落库 span（部分部件需要稳定占位，不受 resize 中间态影响）。 */
    val itemSpanX: Int,
    val itemSpanY: Int,
    val enabled: Boolean,
)

/**
 * 一个内置面板部件（Compose 自绘，不走 AppWidgetHost）。
 *
 * 新增部件只需注册一条 [PanelWidget]；面板层不再按 `widgetId` 写中心 `when`。
 */
data class PanelWidget(
    val id: String,
    val labelRes: Int,
    val defaultSpanX: Int,
    val defaultSpanY: Int,
    val minSpanX: Int = 1,
    val minSpanY: Int = 1,
    val content: @Composable (PanelWidgetContentScope) -> Unit,
)

/** 面板部件注册表：id 唯一，顺序即选择页「Luno 内置」分区顺序。 */
object PanelWidgetRegistry {

    private val widgets = LinkedHashMap<String, PanelWidget>()

    init {
        registerBuiltins()
    }

    fun register(widget: PanelWidget) {
        require(widget.id.isNotBlank()) { "panel widget id must not be blank" }
        require(!widgets.containsKey(widget.id)) { "duplicate panel widget: ${widget.id}" }
        widgets[widget.id] = widget
    }

    fun byId(id: String): PanelWidget? = widgets[id]

    val all: List<PanelWidget> get() = widgets.values.toList()

    val ids: Set<String> get() = widgets.keys.toSet()

    private fun registerBuiltins() {
        register(
            PanelWidget(
                id = InternalWidgetIds.MEDIA,
                labelRes = R.string.grid_panel_internal_media,
                defaultSpanX = 4,
                defaultSpanY = 2,
                minSpanX = 2,
                minSpanY = 1,
            ) { scope ->
                MediaControlCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.VOLUME,
                labelRes = R.string.grid_panel_internal_volume,
                defaultSpanX = 4,
                defaultSpanY = 1,
            ) { scope ->
                VolumeSliderCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.BRIGHTNESS,
                labelRes = R.string.grid_panel_internal_brightness,
                defaultSpanX = 4,
                defaultSpanY = 1,
            ) { scope ->
                BrightnessSliderCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.RECENTS,
                labelRes = R.string.grid_panel_internal_recents,
                defaultSpanX = 4,
                defaultSpanY = 1,
            ) { scope ->
                RecentAppsCell(
                    modifier = scope.modifier,
                    spanX = scope.itemSpanX,
                    spanY = scope.itemSpanY,
                    enabled = scope.enabled,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.CLIPBOARD,
                labelRes = R.string.grid_panel_internal_clipboard,
                defaultSpanX = 4,
                defaultSpanY = 3,
                minSpanX = 2,
                minSpanY = 2,
            ) { scope ->
                ClipboardPageCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.APP_DRAWER,
                labelRes = R.string.grid_panel_internal_app_drawer,
                defaultSpanX = 4,
                defaultSpanY = 4,
                minSpanX = 2,
                minSpanY = 2,
            ) { scope ->
                AppDrawerCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                )
            },
        )
    }
}
