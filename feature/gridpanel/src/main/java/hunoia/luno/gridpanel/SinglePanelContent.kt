package hunoia.luno.gridpanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.GridPanel
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.internal.PanelWidgetContentScope
import hunoia.luno.gridpanel.internal.PanelWidgetRegistry
import hunoia.luno.ui.component.PressFilterChip
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow

/** 单功能面板内容区的外边距：留一圈空白给「长按进编辑」与面板缩放手柄。 */
private val SinglePanelInset = 8.dp

/**
 * 单功能面板整页内容。
 *
 * 不复用网格的 pager / 层 / 加项 / 页点：这里只有「一个功能」或「选择功能」。
 * 内容自己持有滚动（剪贴板 / 应用抽屉都是 `fillMaxSize`），宿主不再套外层滚动。
 *
 * 功能选择走卡内清单而不是 `ActionSelectHostActivity`：后者跑在 `:service`，
 * 从设置页启动会在关闭时唤醒面板浮窗；而整页面板目前只有两个白名单功能，
 * 卡内选择更轻，也让设置页与浮窗共用同一份 [SinglePanelFunctionOptions]。
 */
@Composable
internal fun SinglePanelContent(
    panel: GridPanel,
    metrics: GridMetrics,
    editMode: Boolean,
    pickerVisible: Boolean,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onPickFunction: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(SinglePanelInset),
    ) {
        when {
            pickerVisible -> SinglePanelFunctionPicker(
                currentWidgetId = panel.singleWidgetId,
                onPick = onPickFunction,
                onDismiss = onClosePicker,
            )

            else -> {
                val widget = panel.singleWidgetId?.let(PanelWidgetRegistry::byId)
                    ?.takeIf { it.supportsSinglePanel }
                if (widget == null) {
                    SinglePanelEmptyState(onChoose = onOpenPicker)
                } else {
                    widget.content(
                        PanelWidgetContentScope(
                            modifier = Modifier.fillMaxSize(),
                            spanX = metrics.columns,
                            spanY = metrics.rows,
                            itemSpanX = metrics.columns,
                            itemSpanY = metrics.rows,
                            // 编辑态禁用内容交互：此时手势归面板缩放 / 换功能。
                            enabled = !editMode,
                            params = panel.singleParams,
                            onReconfigure = null,
                        ),
                    )
                }
                if (editMode) {
                    SinglePanelEditBar(
                        onSwitchFunction = onOpenPicker,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SinglePanelEmptyState(onChoose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.single_panel_function_unset),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        PressFilterChip(
            selected = false,
            onClick = onChoose,
            label = { Text(stringResource(R.string.single_panel_choose_function)) },
        )
    }
}

@Composable
private fun SinglePanelEditBar(
    onSwitchFunction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressFilterChip(
        selected = true,
        onClick = onSwitchFunction,
        modifier = modifier,
        label = { Text(stringResource(R.string.single_panel_change_function)) },
        leadingIcon = {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = null,
            )
        },
    )
}

/** 单功能面板的功能清单：只列声明了 [hunoia.luno.gridpanel.internal.PanelWidget.supportsSinglePanel] 的部件。 */
@Composable
internal fun SinglePanelFunctionPicker(
    currentWidgetId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val widgets = PanelWidgetRegistry.singlePanelWidgets
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.single_panel_pick_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        if (widgets.isEmpty()) {
            Text(
                text = stringResource(R.string.single_panel_pick_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
        } else {
            SinglePanelFunctionOptions(
                currentWidgetId = currentWidgetId,
                onPick = onPick,
            )
        }
        Spacer(Modifier.height(8.dp))
        PressFilterChip(
            selected = false,
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            label = { Text(stringResource(R.string.single_panel_cancel)) },
        )
    }
}

/** 功能选择列表本体：面板内与设置页共用，避免两处各写一份白名单/文案。 */
@Composable
fun SinglePanelFunctionOptions(
    currentWidgetId: String?,
    onPick: (String) -> Unit,
) {
    val widgets = PanelWidgetRegistry.singlePanelWidgets
    LunoSegmentedGroup {
        widgets.forEach { widget ->
            item { shape ->
                val selected = widget.id == currentWidgetId
                LunoSettingsRow(
                    title = stringResource(widget.labelRes),
                    shape = shape,
                    trailing = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.single_panel_function),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = { onPick(widget.id) },
                )
            }
        }
    }
}
