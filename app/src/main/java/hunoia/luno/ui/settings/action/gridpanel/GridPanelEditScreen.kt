package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.geometry.gridPanelVisibleRows
import hunoia.luno.config.model.withGeometry
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 网格面板属性编辑：
 * 名称 / 列数 / 行数 / 页数（不设上限）/ 卡片宽度 / 卡片透明度。
 * 项的管理在面板内（长按空白进编辑态，不在设置页做）。
 *
 * 列/行/页数走 [resizeGrid]（当场重排 + 放不下才丢弃）；宽度/透明度是纯外观，
 * 逐刻度直写无破坏性。
 *
 * 装载沿用 ActionPanelEditScreen 的模式：显式 collect、拿到 DataStore 真实值前
 * 不做「面板不存在」判断（首帧空配置会 miss，见那边注释）。
 */
@Composable
fun GridPanelEditScreen(
    panelId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 当前朝向：横屏打开改的是横屏设置，竖屏改竖屏，实时跟随旋转
    fun isLandscape(): Boolean = context.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var loaded by remember { mutableStateOf(false) }
    var panel by remember(panelId) { mutableStateOf<GridPanel?>(null) }
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
     * 改网格尺寸/页数后必须**当场重排并落盘**：网格变小而项还留在旧坐标上时，
     * 那些项会被画到可视区之外 —— 用户看到的是「刚加的小部件凭空消失」。
     * 页数变小同理：溢出项先压进最后一页，页内放不下的顺延首个空位，仍放不下才丢。
     * 丢弃的 AppWidget 项必须回收 id（宿主具名资源，漏收会一直占槽位）。
     * 面板渲染时也做了一次同样的夹取兜底，但那只是显示层的，数据仍然是越界的。
     */
    fun resizeGrid(transform: (GridPanel) -> GridPanel) {
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panelId) ?: return@updateAction settings
                val resized = transform(latest)
                // 只改当前朝向那份几何：横屏下改的就是横屏的设置
                val landscape = isLandscape()
                val resizedGeo = resized.geometry(landscape)
                val columns = resizedGeo.columnCount
                    .coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
                val pages = resized.pageCount.coerceAtLeast(1)
                val droppedNames = mutableListOf<String>()
                val placed = mutableListOf<GridPanelItem>()
                // 页归属先夹一次（页数变小时溢出项进最后一页），再逐页归位：
                // 各页按原顺序处理，放不下的先在**本页**找空位，无空位才丢。
                val coerced = resized.items.map { it.copy(pageIndex = it.pageIndex.coerceIn(0, pages - 1)) }
                // 实际行数：动态行数（行数不限制后 gridRows 字段已删）——
                // 起点 = max(可见行数, 现有占用) + DynamicRowExtra，放不下的项再往上撑，无上限。
                var effectiveRows = maxOf(
                    gridPanelVisibleRows(
                        screenWdp = context.resources.configuration.screenWidthDp.toFloat(),
                        screenHdp = context.resources.configuration.screenHeightDp.toFloat(),
                        columns = columns,
                        cardWidthFraction = resizedGeo.cardWidthFraction,
                        cardHeightFraction = resizedGeo.cardHeightFraction,
                        pageCount = pages,
                    ),
                    resized.items.maxOfOrNull { it.bottom } ?: 0,
                ) + hunoia.luno.config.model.DynamicRowExtra
                for (p in 0 until pages) {
                    for (raw in coerced.filter { it.pageIndex == p }) {
                        // 先夹到网格内，再按比例缩小放不下的项：宁可变小也要留住内容。
                        // 下界1×1 保证不产生退化项（degenerate 项在 planReorder 里行为
                        // 未定义，见 GridPanelItem.normalized 的注释）。
                        val clamped = raw.normalized(columns, effectiveRows)
                        val candidate = if (clamped.spanX <= columns && clamped.spanY <= effectiveRows) {
                            clamped
                        } else {
                            val scale = minOf(
                                columns.toFloat() / clamped.spanX,
                                effectiveRows.toFloat() / clamped.spanY,
                            ).coerceAtMost(1f)
                            val shrunk = clamped.copy(
                                spanX = (clamped.spanX * scale).roundToInt().coerceIn(1, columns),
                                spanY = (clamped.spanY * scale).roundToInt().coerceIn(1, effectiveRows),
                            )
                            // 缩小后若纵向仍然撑破本页，抬高实际行数保它（无上限）
                            if (shrunk.y + shrunk.spanY > effectiveRows) {
                                effectiveRows = shrunk.y + shrunk.spanY
                            }
                            shrunk.normalized(columns, effectiveRows)
                        }
                        when {
                            GridPanelItemLayout.isFree(
                                placed, candidate.x, candidate.y, candidate.spanX, candidate.spanY,
                            ) -> placed += candidate
                            else -> {
                                val slot = GridPanelItemLayout.firstFreeSlot(
                                    placed, columns, effectiveRows, candidate.spanX, candidate.spanY,
                                )
                                if (slot != null) {
                                    placed += candidate.copy(x = slot.x, y = slot.y)
                                } else {
                                    // 到这一步是真的没有空间了（小部件占多格时常见）。
                                    // 仍然丢弃，但要让用户知道为什么 —— 静默删除是最难排查的一类问题。
                                    // 小部件 id 由落盘后的 PanelResources.cleanup() 在 :service 统一回收。
                                    droppedNames += candidate.labelOrNull() ?: ""
                                }
                            }
                        }
                    }
                }
                // 丢弃提示在协程外发：showToast 走 Context，不能在落盘线程里调。
                if (droppedNames.isNotEmpty()) {
                    val named = droppedNames.filter { it.isNotBlank() }
                    showToast(
                        if (named.isEmpty()) {
                            context.getString(R.string.grid_panel_items_dropped_generic, droppedNames.size)
                        } else {
                            context.getString(
                                R.string.grid_panel_items_dropped,
                                droppedNames.size,
                                named.joinToString("、").take(60),
                            )
                        },
                    )
                }
                val newGeo = resizedGeo.copy(columnCount = columns)
                settings.copy(
                    actionGridPanels = settings.actionGridPanels.withPanel(
                        (if (landscape) resized.copy(landscape = newGeo) else resized.copy(portrait = newGeo))
                            .copy(pageCount = pages, items = placed),
                    ),
                )
            }
            // 缩列/缩页可能丢弃项：落盘完成后统一回收被丢项的图标/宿主 id。
            hunoia.luno.domain.gridpanel.PanelResources.cleanup()
        }
    }

    LunoScreenLazy(
        title = current.name.ifBlank { stringResource(R.string.grid_panel_unnamed) },
        onBack = onBack,
        state = rememberLazyListState(),
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
                // 列/行是**破坏性**调整（放不下会挤掉项、回收小部件 id），必须只在
                // 松手（onValueChangeFinished）落盘一次：拖动途中每个刻度都 resizeGrid
                // 的话，从 6 列滑去 2 列的路上会把中间刻度挤掉的项**永久删掉**——
                // 再滑回来也回不来。拖动中的实时值只进 pending，角标显示由
                // LunoTextSlider 内部的 liveValue 跟手。
                var pendingColumns by remember { mutableStateOf<Int?>(null) }
                LunoTextSlider(
                    value = current.geometry(isLandscape()).columnCount.toFloat(),
                    onValueChange = { value -> pendingColumns = value.toInt() },
                    onValueChangeFinished = {
                        pendingColumns?.let { v ->
                            resizeGrid { it.withGeometry(isLandscape()) { g -> g.copy(columnCount = v) } }
                        }
                        pendingColumns = null
                    },
                    text = stringResource(R.string.grid_panel_column_count),
                    valueDisplay = { "${it.toInt()}" },
                    valueRange = GridPanelDefaults.MinColumnCount.toFloat()..GridPanelDefaults.MaxColumnCount.toFloat(),
                    steps = GridPanelDefaults.MaxColumnCount - GridPanelDefaults.MinColumnCount - 1,
                    shape = segmentedShape(0, 3),
                )
                var pendingHeight by remember { mutableStateOf<Float?>(null) }
                LunoTextSlider(
                    value = current.geometry(isLandscape()).cardHeightFraction,
                    onValueChange = { value -> pendingHeight = value },
                    onValueChangeFinished = {
                        pendingHeight?.let { v ->
                            update { it.withGeometry(isLandscape()) { g -> g.copy(cardHeightFraction = v) } }
                        }
                        pendingHeight = null
                    },
                    text = stringResource(R.string.grid_panel_card_height),
                    valueDisplay = { "${(it * 100).toInt()}%" },
                    valueRange = GridPanelDefaults.MinCardHeightFraction..GridPanelDefaults.MaxCardHeightFraction,
                    steps = 11,
                    shape = segmentedShape(1, 3),
                )
                // 行数由卡片高度反推，行数滑杆已删：剩 列数 / 卡片高度 / 卡片宽度 / 页数。
                // 页数不设上限（§10 拍板）：滑杆必须有 Range，这里用 +/- 步进行。
                PageStepperRow(
                    pageCount = current.pageCount,
                    onSelect = { value -> resizeGrid { it.copy(pageCount = value) } },
                    shape = segmentedShape(2, 3),
                )
            }
        }
        item {
            LunoGroup {
                var pendingWidth by remember { mutableStateOf<Float?>(null) }
                LunoTextSlider(
                    value = current.geometry(isLandscape()).cardWidthFraction,
                    onValueChange = { value -> pendingWidth = value },
                    onValueChangeFinished = {
                        pendingWidth?.let { v ->
                            update { it.withGeometry(isLandscape()) { g -> g.copy(cardWidthFraction = v) } }
                        }
                        pendingWidth = null
                    },
                    text = stringResource(R.string.grid_panel_card_width),
                    valueDisplay = { "${(it * 100).toInt()}%" },
                    valueRange = GridPanelDefaults.MinCardWidthFraction..GridPanelDefaults.MaxCardWidthFraction,
                    steps = 7,
                    shape = segmentedShape(0, 2),
                )
                var localAlpha by remember(current.cardAlpha) { mutableFloatStateOf(current.cardAlpha) }
                LunoTextSlider(
                    value = localAlpha,
                    onValueChange = { value -> localAlpha = value },
                    onValueChangeFinished = { update { it.copy(cardAlpha = localAlpha) } },
                    text = stringResource(R.string.grid_panel_card_alpha),
                    valueDisplay = { "${(it * 100).toInt()}%" },
                    valueRange = GridPanelDefaults.MinCardAlpha..GridPanelDefaults.MaxCardAlpha,
                    steps = 13,
                    shape = segmentedShape(1, 2),
                )
            }
        }
    }
}

/** 页数步进行（页数不设上限所以不走滑杆）。下限 1 页。 */
@Composable
private fun PageStepperRow(
    pageCount: Int,
    onSelect: (Int) -> Unit,
    shape: Shape,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.grid_panel_page_count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { if (pageCount > 1) onSelect(pageCount - 1) }) {
                Icon(Icons.Default.Remove, contentDescription = null)
            }
            Text(
                text = "$pageCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            IconButton(onClick = { onSelect(pageCount + 1) }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}
