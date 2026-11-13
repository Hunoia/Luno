package hunoia.luno.gridpanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.SinglePanel
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.internal.PanelWidgetContentScope
import hunoia.luno.gridpanel.internal.PanelWidgetRegistry

/**
 * 单功能面板整页内容：固定渲染该功能，不再有「选择 / 切换功能」入口。
 * 功能清单由注册表决定，配置只提供几何与 params；编辑态禁用内容交互，把手势让给缩放。
 *
 * **不自带外边距**：各功能内容本来就带自己的内边距（媒体 / 剪贴板 / 系统监控 …），
 * 这里再包一层会让同一块内容比网格页更"缩"（快捷启动曾是 8 + 12/10 = 20/18dp）。
 * 长按进编辑由面板根手势负责，不靠留白。
 */
@Composable
internal fun SinglePanelContent(
    panel: SinglePanel,
    metrics: GridMetrics,
    editMode: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
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
