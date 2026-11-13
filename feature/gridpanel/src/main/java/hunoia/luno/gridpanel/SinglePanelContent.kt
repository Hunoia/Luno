package hunoia.luno.gridpanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.SinglePanel
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.internal.PanelWidgetContentScope
import hunoia.luno.gridpanel.internal.PanelWidgetRegistry

/** 单功能面板内容区的外边距：留一圈空白给「长按进编辑」与面板缩放手柄。 */
private val SinglePanelInset = 8.dp

/**
 * 单功能面板整页内容：固定渲染该功能，不再有「选择 / 切换功能」入口。
 * 功能清单由注册表决定，配置只提供几何与 params；编辑态禁用内容交互，把手势让给缩放。
 */
@Composable
internal fun SinglePanelContent(
    panel: SinglePanel,
    metrics: GridMetrics,
    editMode: Boolean,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(SinglePanelInset),
    ) {
        val widget = PanelWidgetRegistry.byId(panel.widgetId)
            ?.takeIf { it.supportsSinglePanel }
        if (widget == null) {
            Text(
                text = stringResource(R.string.single_panel_function_missing),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        widget.content(
            PanelWidgetContentScope(
                modifier = Modifier.fillMaxSize(),
                spanX = metrics.columns,
                spanY = metrics.rows,
                itemSpanX = metrics.columns,
                itemSpanY = metrics.rows,
                // 编辑态禁用内容交互：此时手势归面板缩放。
                enabled = !editMode,
                params = panel.params,
                onReconfigure = null,
            ),
        )
    }
}
