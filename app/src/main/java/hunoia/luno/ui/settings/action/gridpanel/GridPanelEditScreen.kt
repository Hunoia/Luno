package hunoia.luno.ui.settings.action.gridpanel

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.geometry.gridPanelVisibleRows
import hunoia.luno.config.model.withGeometry
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "GridPanelEdit"

/**
 * 网格面板属性编辑：名称 / 列数 / 卡片宽高 / 透明度 / 离屏边距。项在面板内管理。
 * 页数是派生值（编辑态滑到末尾虚页落项即建页），不在这里设置。
 * 列数走 [resizeGrid]（当场重排；放不下时先弹确认再移除）；宽度/透明度/边距是纯外观，
 * 松手落盘，步进 1（1% / 1dp），拖动过程无级。
 * 装载必须显式 collect：拿到 DataStore 真实值前不能判「面板不存在」，首帧空配置会误报。
 */
@Composable
fun GridPanelEditScreen(
    panelId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 当前朝向：横屏打开改的是横屏设置，竖屏改竖屏，实时跟随旋转。
    fun isLandscape(): Boolean = context.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var loaded by remember { mutableStateOf(false) }
    var panel by remember(panelId) { mutableStateOf<GridPanel?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 列数缩小时会移除项：先预演，有损失就弹确认，确认后才落盘。
    var pendingShrink by remember { mutableStateOf<PendingShrink?>(null) }
    // 取消缩列时滑杆要回到已落盘值：换 key 重建，否则 LunoSlider 的本地值会停在
    // 拖动位置（value 没变，它不会自己复位）。
    var columnSliderEpoch by remember { mutableStateOf(0) }
    LaunchedEffect(panelId) {
        TuneConfig.action.collect { settings ->
            panel = settings.actionGridPanels.panelById(panelId)
            loaded = true
        }
    }

    val current = panel
    if (current == null) {
        if (loaded) {
            LunoScreen(title = stringResource(R.string.grid_panel_unnamed), onBack = onBack) {
                EmptyState(message = stringResource(R.string.action_panel_missing))
            }
        }
        return
    }

    fun update(transform: (GridPanel) -> GridPanel) {
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panelId) ?: return@updateAction settings
                settings.copy(actionGridPanels = settings.actionGridPanels.withPanel(transform(latest)))
            }
        }
    }

    /**
     * 改列数后必须当场重排并落盘：网格变小而项还留在旧坐标会被画到可视区外，
     * 看起来像「刚加的小部件凭空消失」。
     *
     * 先预演 [planGridResize]：有项放不下就弹确认（列出会被移除的项），取消则
     * 什么都不写（滑块自然回弹）；确认后按最新配置重算一遍再落盘。被丢项的
     * AppWidget id 由落盘后的 `PanelResources.cleanup()` 回收。
     */
    fun resizeGrid(transform: (GridPanel) -> GridPanel, confirmed: Boolean = false) {
        val cfg = context.resources.configuration
        val screenWdp = cfg.screenWidthDp.toFloat()
        val screenHdp = cfg.screenHeightDp.toFloat()
        val landscape = isLandscape()

        fun plan(panel: GridPanel): Pair<GridResizePlan, Int> {
            val geo = panel.geometry(landscape)
            val cols = geo.columnCount
                .coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
            return planGridResize(
                items = panel.items,
                columns = cols,
                cardWidthFraction = geo.cardWidthFraction,
                cardHeightFraction = geo.cardHeightFraction,
                screenWdp = screenWdp,
                screenHdp = screenHdp,
            ) to cols
        }

        if (!confirmed) {
            val (preview, cols) = plan(transform(current))
            if (preview.dropped.isNotEmpty()) {
                pendingShrink = PendingShrink(transform, cols, preview.dropped)
                return
            }
        }

        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panelId) ?: return@updateAction settings
                val resized = transform(latest)
                val (result, cols) = plan(resized)
                if (result.dropped.isNotEmpty()) {
                    Log.w(TAG, "resizeGrid drops ${result.dropped.size} item(s) at $cols columns")
                }
                val newGeo = resized.geometry(landscape).copy(columnCount = cols)
                settings.copy(
                    actionGridPanels = settings.actionGridPanels.withPanel(
                        (if (landscape) resized.copy(landscape = newGeo) else resized.copy(portrait = newGeo))
                            .copy(items = result.items),
                    ),
                )
            }
            // 缩列可能丢弃项：落盘完成后统一回收被丢项的图标/宿主 id。
            hunoia.luno.domain.gridpanel.PanelResources.cleanup()
        }
    }

    LunoScreenLazy(
        title = current.name.ifBlank { stringResource(R.string.grid_panel_unnamed) },
        onBack = onBack,
        state = rememberLazyListState(),
        actions = {
            // 删除从列表页移到编辑页右上角：带确认，删后返回。
            LunoIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                onClick = { showDeleteConfirm = true },
            )
        },
        dialogs = {
            if (showDeleteConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.grid_panel_delete_title),
                    text = stringResource(
                        R.string.action_panel_delete_desc,
                        current.name.ifBlank { stringResource(R.string.grid_panel_unnamed) },
                    ),
                    confirmLabel = stringResource(R.string.delete),
                    destructive = true,
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            TuneConfig.updateAction { settings ->
                                settings.copy(actionGridPanels = settings.actionGridPanels.withoutPanel(panelId))
                            }
                            hunoia.luno.domain.gridpanel.PanelResources.cleanup()
                            onBack()
                        }
                    },
                )
            }
            pendingShrink?.let { pending ->
                val named = pending.dropped.mapNotNull { it.labelOrNull() }
                ConfirmDialog(
                    onDismissRequest = { pendingShrink = null; columnSliderEpoch++ },
                    title = stringResource(R.string.grid_panel_shrink_title),
                    text = if (named.isEmpty()) {
                        stringResource(
                            R.string.grid_panel_shrink_desc_generic,
                            pending.columns,
                            pending.dropped.size,
                        )
                    } else {
                        stringResource(
                            R.string.grid_panel_shrink_desc_named,
                            pending.columns,
                            pending.dropped.size,
                            named.joinToString("、").take(60),
                        )
                    },
                    confirmLabel = stringResource(R.string.grid_panel_shrink_confirm),
                    destructive = true,
                    onConfirm = {
                        val transform = pending.transform
                        pendingShrink = null
                        resizeGrid(transform, confirmed = true)
                    },
                )
            }
        },
    ) {
        item {
            OutlinedTextField(
                value = current.name,
                onValueChange = { name -> update { it.copy(name = name) } },
                label = { Text(stringResource(R.string.action_panel_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                shape = LargeShape,
            )
        }
        item {
            LunoGroup {
                // 列/行是破坏性调整（放不下会挤掉项并回收 widget id），只在松手
                // （onValueChangeFinished）落盘一次：拖动途中逐刻度 resize 会把经过的
                // 中间刻度挤掉的项永久删除，再滑回来也回不去。拖动中的值只进 pending。
                var pendingColumns by remember { mutableStateOf<Int?>(null) }
                key(columnSliderEpoch) {
                    LunoTextSlider(
                        value = current.geometry(isLandscape()).columnCount.toFloat(),
                        onValueChange = { value -> pendingColumns = value.toInt() },
                        onValueChangeFinished = {
                            pendingColumns?.let { v ->
                                resizeGrid(transform = {
                                    it.withGeometry(isLandscape()) { g -> g.copy(columnCount = v) }
                                })
                            }
                            pendingColumns = null
                        },
                        text = stringResource(R.string.grid_panel_column_count),
                        valueDisplay = { "${it.toInt()}" },
                        valueRange = GridPanelDefaults.MinColumnCount.toFloat()..GridPanelDefaults.MaxColumnCount.toFloat(),
                        steps = GridPanelDefaults.MaxColumnCount - GridPanelDefaults.MinColumnCount - 1,
                        shape = segmentedShape(0, 2),
                    )
                }
                var pendingHeight by remember { mutableStateOf<Float?>(null) }
                LunoTextSlider(
                    value = current.geometry(isLandscape()).cardHeightFraction,
                    onValueChange = { value -> pendingHeight = value.percentStep() },
                    onValueChangeFinished = {
                        pendingHeight?.let { v ->
                            update { it.withGeometry(isLandscape()) { g -> g.copy(cardHeightFraction = v) } }
                        }
                        pendingHeight = null
                    },
                    text = stringResource(R.string.grid_panel_card_height),
                    valueDisplay = { "${(it * 100).roundToInt()}%" },
                    valueRange = GridPanelDefaults.MinCardHeightFraction..GridPanelDefaults.MaxCardHeightFraction,
                    // 无级拖动、按 1% 落盘；固定 steps 会把刻度画成密集圆点。
                    steps = 0,
                    shape = segmentedShape(1, 2),
                )
            }
        }
        item {
            LunoGroup {
                var pendingWidth by remember { mutableStateOf<Float?>(null) }
                LunoTextSlider(
                    value = current.geometry(isLandscape()).cardWidthFraction,
                    onValueChange = { value -> pendingWidth = value.percentStep() },
                    onValueChangeFinished = {
                        pendingWidth?.let { v ->
                            update { it.withGeometry(isLandscape()) { g -> g.copy(cardWidthFraction = v) } }
                        }
                        pendingWidth = null
                    },
                    text = stringResource(R.string.grid_panel_card_width),
                    valueDisplay = { "${(it * 100).roundToInt()}%" },
                    valueRange = (
                        if (isLandscape()) {
                            GridPanelDefaults.MinCardWidthFractionLandscape
                        } else {
                            GridPanelDefaults.MinCardWidthFraction
                        }
                    )..GridPanelDefaults.MaxCardWidthFraction,
                    steps = 0,
                    shape = segmentedShape(0, 3),
                )
                var localAlpha by remember(current.cardAlpha) { mutableFloatStateOf(current.cardAlpha) }
                LunoTextSlider(
                    value = localAlpha,
                    onValueChange = { value -> localAlpha = value.percentStep() },
                    onValueChangeFinished = { update { it.copy(cardAlpha = localAlpha) } },
                    text = stringResource(R.string.grid_panel_card_alpha),
                    valueDisplay = { "${(it * 100).roundToInt()}%" },
                    valueRange = GridPanelDefaults.MinCardAlpha..GridPanelDefaults.MaxCardAlpha,
                    steps = 0,
                    shape = segmentedShape(1, 3),
                )
                // 离屏边距：纯外观（clamp + 圆角联动），无级拖动、按 1dp 落盘。
                var pendingMargin by remember { mutableStateOf<Float?>(null) }
                LunoTextSlider(
                    value = current.geometry(isLandscape()).screenMarginDp,
                    onValueChange = { value -> pendingMargin = value.roundToInt().toFloat() },
                    onValueChangeFinished = {
                        pendingMargin?.let { v ->
                            update { it.withGeometry(isLandscape()) { g -> g.copy(screenMarginDp = v) } }
                        }
                        pendingMargin = null
                    },
                    text = stringResource(R.string.grid_panel_screen_margin),
                    valueDisplay = { "${it.roundToInt()}dp" },
                    valueRange = GridPanelDefaults.MinScreenMarginDp..GridPanelDefaults.MaxScreenMarginDp,
                    steps = 0,
                    shape = segmentedShape(2, 3),
                )
            }
        }
    }
}

/** 百分比滑杆：连续拖动、按 1% 量化后落盘。 */
private fun Float.percentStep(): Float = (this * 100f).roundToInt() / 100f

/** 待确认的列数缩小：`transform` 原样重放，确认后按最新配置重算一次。 */
private class PendingShrink(
    val transform: (GridPanel) -> GridPanel,
    val columns: Int,
    val dropped: List<GridPanelItem>,
)

/** [planGridResize] 的结果：重排后的项 + 确实放不下、会被移除的项。 */
internal class GridResizePlan(
    val items: List<GridPanelItem>,
    val dropped: List<GridPanelItem>,
)

/**
 * 列数变化的落位预演（纯函数）：夹进网格 → 放不下按比例缩小 → 本页找空位 →
 * 仍放不下才进 [GridResizePlan.dropped]。
 *
 * 弹窗预演与真实落盘共用这一份规则，所以「弹窗里说要移除哪些」就是实际会移除的。
 */
internal fun planGridResize(
    items: List<GridPanelItem>,
    columns: Int,
    cardWidthFraction: Float,
    cardHeightFraction: Float,
    screenWdp: Float,
    screenHdp: Float,
): GridResizePlan {
    val cols = columns.coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
    // 页数派生：按现有项的最大页归属重排，不在这里增删页。
    val pages = (items.maxOfOrNull { it.pageIndex } ?: 0) + 1
    val placed = mutableListOf<GridPanelItem>()
    val dropped = mutableListOf<GridPanelItem>()
    // 实际行数：max(可见行数, 现有占用)，放不下的项再往上撑，无上限。
    var effectiveRows = maxOf(
        gridPanelVisibleRows(screenWdp, screenHdp, cols, cardWidthFraction, cardHeightFraction),
        items.maxOfOrNull { it.bottom } ?: 0,
    )
    val coerced = items.map { it.copy(pageIndex = it.pageIndex.coerceIn(0, pages - 1)) }
    for (p in 0 until pages) {
        for (raw in coerced.filter { it.pageIndex == p }) {
            // 先夹到网格内，放不下的按比例缩小：宁可变小也要留住内容。下界 1×1
            // 避免退化项（planReorder 对退化项行为未定义，见 GridPanelItem.normalized）。
            val clamped = raw.normalized(cols, effectiveRows)
            val candidate = if (clamped.spanX <= cols && clamped.spanY <= effectiveRows) {
                clamped
            } else {
                val scale = minOf(
                    cols.toFloat() / clamped.spanX,
                    effectiveRows.toFloat() / clamped.spanY,
                ).coerceAtMost(1f)
                val shrunk = clamped.copy(
                    spanX = (clamped.spanX * scale).roundToInt().coerceIn(1, cols),
                    spanY = (clamped.spanY * scale).roundToInt().coerceIn(1, effectiveRows),
                )
                // 缩小后若纵向仍然撑破本页，抬高实际行数保它（无上限）
                if (shrunk.y + shrunk.spanY > effectiveRows) {
                    effectiveRows = shrunk.y + shrunk.spanY
                }
                shrunk.normalized(cols, effectiveRows)
            }
            when {
                GridPanelItemLayout.isFree(
                    placed, candidate.x, candidate.y, candidate.spanX, candidate.spanY,
                ) -> placed += candidate
                else -> {
                    val slot = GridPanelItemLayout.firstFreeSlot(
                        placed, cols, effectiveRows, candidate.spanX, candidate.spanY,
                    )
                    if (slot != null) {
                        placed += candidate.copy(x = slot.x, y = slot.y)
                    } else {
                        dropped += candidate
                    }
                }
            }
        }
    }
    return GridResizePlan(placed, dropped)
}
