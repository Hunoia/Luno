package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import hunoia.luno.config.model.PanelMode
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.withOrientation
import hunoia.luno.gridpanel.SinglePanelFunctionOptions
import hunoia.luno.gridpanel.internal.PanelWidgetRegistry
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SurfacePlacementSliders
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.OptimizedScrollState
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 面板属性编辑：名称 / 类型 / 透明度 / 背景模糊 / 离屏边距。
 * 网格面板的项与列数在面板内管理；单功能面板选功能在这里，运行态只换功能与缩放。
 * 页数是派生值（编辑态滑到末尾虚页落项即建页）；列数只在编辑态双指调整；
 * 卡片宽高只在面板内的缩放手柄调整，不在这里重复给数值入口。
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
    var showFunctionPicker by remember { mutableStateOf(false) }
    var pendingModeSwitch by remember { mutableStateOf<PanelMode?>(null) }
    LaunchedEffect(panelId) {
        TuneConfig.action.collect { settings ->
            panel = settings.actionGridPanels.panelById(panelId)
            loaded = true
        }
    }

    val current = panel
    if (current == null) {
        if (loaded) {
            LunoScreen(title = stringResource(hunoia.luno.actions.R.string.grid_panel_unnamed), onBack = onBack) {
                EmptyState(message = stringResource(hunoia.luno.gridpanel.R.string.action_panel_missing))
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

    fun switchMode(target: PanelMode) {
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panelId) ?: return@updateAction settings
                // 模式切换是破坏性的：两种模式的数据互不相干，留着只会形成「看不见但还在」的双轨。
                val next = latest.copy(
                    mode = target,
                    singleWidgetId = null,
                    singleParams = null,
                    // 单功能模式没有列数入口，内部列数固定回默认 4：避免从 6 列网格切过来后
                    // 应用抽屉图标列数继承旧值，又无处可改。
                    portrait = latest.portrait.copy(
                        items = emptyList(),
                        geometry = latest.portrait.geometry.copy(columnCount = GridPanelDefaults.ColumnCount),
                    ),
                    landscape = latest.landscape.copy(
                        items = emptyList(),
                        geometry = latest.landscape.geometry.copy(columnCount = GridPanelDefaults.ColumnCount),
                    ),
                )
                settings.copy(actionGridPanels = settings.actionGridPanels.withPanel(next))
            }
            hunoia.luno.gridpanel.PanelResources.cleanup()
        }
    }

    LunoScreenLazy(
        title = current.name.ifBlank { stringResource(hunoia.luno.actions.R.string.grid_panel_unnamed) },
        onBack = onBack,
        state = rememberLazyListState(),
        actions = {
            // 删除从列表页移到编辑页右上角：带确认，删后返回。
            LunoIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                onClick = { showDeleteConfirm = true },
            )
        },
        dialogs = {
            if (showDeleteConfirm) {
                // 引用数在弹窗打开时现查：引用可能刚在别的页面改过。
                var referenceCount by remember { mutableStateOf(0) }
                LaunchedEffect(showDeleteConfirm) {
                    if (showDeleteConfirm) referenceCount = TuneConfig.countPanelReferences(panelId)
                }
                val deleteDesc = stringResource(
                    R.string.action_panel_delete_desc,
                    current.name.ifBlank { stringResource(hunoia.luno.actions.R.string.grid_panel_unnamed) },
                )
                ConfirmDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.grid_panel_delete_title),
                    text = if (referenceCount > 0) {
                        deleteDesc + "\n" + stringResource(R.string.action_panel_delete_refs, referenceCount)
                    } else {
                        deleteDesc
                    },
                    destructive = true,
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            TuneConfig.cleanPanelReferences(panelId)
                            TuneConfig.updateAction { settings ->
                                settings.copy(actionGridPanels = settings.actionGridPanels.withoutPanel(panelId))
                            }
                            hunoia.luno.gridpanel.PanelResources.cleanup()
                            onBack()
                        }
                    },
                )
            }
            pendingModeSwitch?.let { target ->
                val toSingle = target == PanelMode.Single
                ConfirmDialog(
                    onDismissRequest = { pendingModeSwitch = null },
                    title = stringResource(
                        if (toSingle) hunoia.luno.gridpanel.R.string.single_panel_to_single_title
                        else hunoia.luno.gridpanel.R.string.single_panel_to_grid_title,
                    ),
                    text = stringResource(
                        if (toSingle) hunoia.luno.gridpanel.R.string.single_panel_to_single_message
                        else hunoia.luno.gridpanel.R.string.single_panel_to_grid_message,
                    ),
                    confirmLabel = stringResource(hunoia.luno.gridpanel.R.string.single_panel_switch),
                    destructive = true,
                    onConfirm = {
                        pendingModeSwitch = null
                        switchMode(target)
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
                LunoSettingsRow(
                    title = stringResource(hunoia.luno.gridpanel.R.string.single_panel_mode),
                    subtitle = if (current.isSingle) {
                        stringResource(hunoia.luno.gridpanel.R.string.single_panel_mode_single)
                    } else {
                        stringResource(hunoia.luno.gridpanel.R.string.single_panel_mode_grid)
                    },
                    shape = if (current.isSingle) segmentedShape(0, 2) else segmentedShape(0, 1),
                    onClick = {
                        pendingModeSwitch = if (current.isSingle) PanelMode.Grid else PanelMode.Single
                    },
                )
                if (current.isSingle) {
                    LunoSettingsRow(
                        title = stringResource(hunoia.luno.gridpanel.R.string.single_panel_function),
                        subtitle = singleFunctionLabel(current.singleWidgetId),
                        shape = segmentedShape(1, 2),
                        onClick = { showFunctionPicker = true },
                    )
                }
            }
        }
        item {
            LunoGroup {
                var localAlpha by remember(current.cardAlpha) { mutableFloatStateOf(current.cardAlpha) }
                LunoTextSlider(
                    value = localAlpha,
                    onValueChange = { value -> localAlpha = value.percentStep() },
                    onValueChangeFinished = { update { it.copy(cardAlpha = localAlpha) } },
                    text = stringResource(R.string.grid_panel_card_alpha),
                    valueDisplay = { "${(it * 100).roundToInt()}%" },
                    valueRange = GridPanelDefaults.MinCardAlpha..GridPanelDefaults.MaxCardAlpha,
                    steps = 0,
                    shape = segmentedShape(0, 5),
                )
                // 背景模糊：0 = 关闭；无级拖动、按 1dp 落盘。
                var localBlur by remember(current.blurRadiusDp) {
                    mutableFloatStateOf(current.blurRadiusDp)
                }
                val blurOffLabel = stringResource(R.string.grid_panel_blur_off)
                LunoTextSlider(
                    value = localBlur,
                    onValueChange = { value -> localBlur = value.roundToInt().toFloat() },
                    onValueChangeFinished = { update { it.copy(blurRadiusDp = localBlur) } },
                    text = stringResource(R.string.grid_panel_blur_radius),
                    valueDisplay = { v ->
                        if (v <= 0f) blurOffLabel else "${v.roundToInt()}dp"
                    },
                    valueRange = GridPanelDefaults.MinBlurRadiusDp..GridPanelDefaults.MaxBlurRadiusDp,
                    steps = 0,
                    shape = segmentedShape(1, 5),
                )
                // 跟随触发点的三个边距（横竖屏各存一份，这里编辑当前朝向）。
                SurfacePlacementSliders(
                    placement = current.orientation(isLandscape()).geometry.placement,
                    shapeAt = { index -> segmentedShape(index + 2, 5) },
                    onCommit = { next ->
                        update {
                            it.withOrientation(isLandscape()) { o ->
                                o.copy(geometry = o.geometry.copy(placement = next))
                            }
                        }
                    },
                )
            }
        }
    }

    if (showFunctionPicker) {
        val scrollState = rememberScrollState()
        LunoSheet(
            onDismissRequest = { showFunctionPicker = false },
            showDragHandle = true,
            scrollState = OptimizedScrollState.Scroll(scrollState),
        ) {
            Text(
                text = stringResource(hunoia.luno.gridpanel.R.string.single_panel_pick_title),
                modifier = Modifier.padding(horizontal = PageGutter, vertical = 12.dp),
            )
            SinglePanelFunctionOptions(
                currentWidgetId = current.singleWidgetId,
                onPick = { widgetId ->
                    showFunctionPicker = false
                    update { it.copy(singleWidgetId = widgetId, singleParams = null) }
                },
            )
        }
    }
}

@Composable
private fun singleFunctionLabel(widgetId: String?): String {
    val widget = widgetId?.takeIf { it.isNotBlank() }?.let(PanelWidgetRegistry::byId)
    return when {
        widget != null -> stringResource(widget.labelRes)
        widgetId.isNullOrBlank() -> stringResource(hunoia.luno.gridpanel.R.string.single_panel_function_unset)
        else -> stringResource(hunoia.luno.gridpanel.R.string.single_panel_function_missing)
    }
}

/** 百分比滑杆：连续拖动、按 1% 量化后落盘。 */
private fun Float.percentStep(): Float = (this * 100f).roundToInt() / 100f
