package hunoia.luno.gridpanel.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    /** 每实例配置（JSON 串，见 architecture §9.2）：无参部件为 null，有参部件自行解析。 */
    val params: String? = null,
    /**
     * 是否作为网格项渲染（而不是单功能面板整页）。
     * 网格项不再叠旧版 6–14dp 外边距，页面仍沿用各自原有的整页内边距。
     */
    val isGridItem: Boolean = false,
    /**
     * 部件请求重新配置（如习惯部件指向的习惯被删后重选目标）：面板层接住后打开选择页，
     * 选中新部件时**原位替换**该项。无配置需求的部件忽略此回调。
     */
    val onReconfigure: (() -> Unit)? = null,
)

/**
 * 一个内置面板部件（Compose 自绘，不走 AppWidgetHost）。
 *
 * 新增部件只需注册一条 [PanelWidget]；面板层不再按 `widgetId` 写中心 `when`。
 */
data class PanelWidget(
    val id: String,
    val labelRes: Int,
    /**
     * 底栏 / 列表用图标。单功能面板进底栏时必须有一个，因此不是可空字段：
     * 忘了给图标会在编译期就挂，不会拖到运行时才出现空 tab。
     */
    val icon: ImageVector,
    val defaultSpanX: Int,
    val defaultSpanY: Int,
    val minSpanX: Int = 1,
    val minSpanY: Int = 1,
    /**
     * 能否作为单功能面板的整页内容。不是所有部件都适合整页：音量/亮度这类
     * 条状控件放进整页面板会失去「面板里的一条」语义，默认拒绝。
     */
    val supportsSinglePanel: Boolean = false,
    /** 是否可作为网格面板里的一个格子项；整页专用的功能应设 false。 */
    val availableAsGridItem: Boolean = true,
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

    /** 可作为单功能面板整页内容的部件，顺序与 [all] 一致。 */
    val singlePanelWidgets: List<PanelWidget>
        get() = widgets.values.filter { it.supportsSinglePanel }

    val ids: Set<String> get() = widgets.keys.toSet()

    private fun registerBuiltins() {
        register(
            PanelWidget(
                id = InternalWidgetIds.MEDIA,
                labelRes = R.string.grid_panel_internal_media,
                icon = Icons.Default.PlayArrow,
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
                icon = Icons.AutoMirrored.Filled.VolumeUp,
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
                icon = Icons.Default.Brightness6,
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
                icon = Icons.Default.History,
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
                icon = Icons.Default.ContentPaste,
                defaultSpanX = 4,
                defaultSpanY = 3,
                minSpanX = 2,
                minSpanY = 2,
                supportsSinglePanel = true,
            ) { scope ->
                ClipboardPageCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                    gridItem = scope.isGridItem,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.APP_DRAWER,
                labelRes = R.string.grid_panel_internal_app_drawer,
                icon = Icons.Default.Apps,
                defaultSpanX = 4,
                defaultSpanY = 4,
                minSpanX = 2,
                minSpanY = 2,
                supportsSinglePanel = true,
            ) { scope ->
                AppDrawerCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.QUICK_LAUNCH,
                labelRes = R.string.grid_panel_internal_quick_launch,
                icon = Icons.Default.Keyboard,
                defaultSpanX = 4,
                defaultSpanY = 4,
                minSpanX = 2,
                minSpanY = 2,
                supportsSinglePanel = true,
                availableAsGridItem = false,
            ) { scope ->
                QuickLaunchPanelCell(
                    modifier = scope.modifier,
                    enabled = scope.enabled,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.HABIT_HISTORY,
                labelRes = R.string.grid_panel_internal_habit_history,
                icon = Icons.Default.CheckCircle,
                defaultSpanX = 2,
                defaultSpanY = 2,
                minSpanX = 2,
                minSpanY = 2,
            ) { scope ->
                HabitHistoryCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                    params = scope.params,
                    onReconfigure = scope.onReconfigure,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.HABIT_HEATMAP,
                labelRes = R.string.grid_panel_internal_habit_heatmap,
                icon = Icons.Default.GridOn,
                defaultSpanX = 2,
                defaultSpanY = 2,
                minSpanX = 2,
                minSpanY = 2,
            ) { scope ->
                HabitHeatmapCell(
                    modifier = scope.modifier,
                    enabled = scope.enabled,
                    params = scope.params,
                    onReconfigure = scope.onReconfigure,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.TODO,
                labelRes = R.string.grid_panel_internal_todo,
                icon = Icons.Default.List,
                defaultSpanX = 2,
                defaultSpanY = 2,
                minSpanX = 1,
                minSpanY = 1,
            ) { scope ->
                TodoListCell(
                    modifier = scope.modifier,
                    spanX = scope.spanX,
                    spanY = scope.spanY,
                    enabled = scope.enabled,
                    params = scope.params,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.SYS_MONITOR,
                labelRes = R.string.grid_panel_internal_sys_monitor,
                icon = Icons.Default.Speed,
                defaultSpanX = 4,
                defaultSpanY = 3,
                minSpanX = 2,
                minSpanY = 2,
            ) { scope ->
                SysMonitorCell(
                    modifier = scope.modifier,
                    enabled = scope.enabled,
                )
            },
        )
        register(
            PanelWidget(
                id = InternalWidgetIds.TREND,
                labelRes = R.string.grid_panel_internal_trend,
                icon = Icons.Default.AddChart,
                defaultSpanX = 2,
                defaultSpanY = 2,
                minSpanX = 1,
                minSpanY = 1,
            ) { scope ->
                TrendWidgetCell(
                    modifier = scope.modifier,
                    enabled = scope.enabled,
                    params = scope.params,
                    onReconfigure = scope.onReconfigure,
                )
            },
        )
    }
}
