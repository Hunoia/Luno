package hunoia.luno.annotate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.annotate.R
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.render.ExportKind
import hunoia.luno.ui.theme.CompactControlIconSize
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SubMinInteractiveSize
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.theme.raisedSurfaceColor

/**
 * 两个宿主共用的工具条上方 chrome（审计 P1c 收敛）：
 * 面板种类枚举、样式卡/导出卡/确认卡容器、确认卡与导出动作按钮。
 *
 * 浮层宿主与图片编辑宿主的工具条容器不同（独立窗口 vs 页内 Box），
 * 但面板内容/卡内排版/触达区一致，放在这里避免两份实现漂移。
 */

/**
 * 工具条上方面板的种类。样式卡不计入枚举：两个宿主都用
 * `stylePanelTool: ToolKind?` 单独管理（与面板互斥的语义两边一致）。
 */
enum class AnnotatePanelKind { None, Export, ConfirmExit }

/**
 * 面板内容统一入口：样式二层卡 / 导出动作行 / 退出确认卡。
 *
 * 容器由宿主决定（浮层独立窗口带淡入淡出 / 图片编辑页内联卡），
 * 这里只保证**内容与排版只有一份**。调用方约定：
 * - [onExport] 由宿主各自接线（浮层：收面板后导出；图片编辑：先关弹出卡再导出）；
 * - 取消一律回 [AnnotatePanelKind.None]。
 */
@Composable
fun AnnotatePanelContent(
    document: AnnotateDocument,
    panel: AnnotatePanelKind,
    stylePanelTool: ToolKind?,
    onPanel: (AnnotatePanelKind) -> Unit,
    onExport: (ExportKind) -> Unit,
    onConfirmExit: () -> Unit,
    onCropReset: (() -> Unit)? = null,
    /** 形状子类型选中后宿主收起样式卡（P3 手感）。 */
    onShapeSelected: (() -> Unit)? = null,
) {
    when {
        stylePanelTool != null -> {
            // 点工具弹样式二层卡（B）：按工具展示颜色/大小/橡皮/选区操作。
            StylePanel(
                tool = stylePanelTool,
                document = document,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                onCropReset = onCropReset,
                onShapeSelected = onShapeSelected,
            )
        }

        panel == AnnotatePanelKind.Export -> Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ExportAction(
                icon = Icons.Default.SaveAlt,
                label = stringResource(R.string.annotate_save),
                onClick = { onExport(ExportKind.Save) },
            )
            ExportAction(
                icon = Icons.Default.ContentCopy,
                label = stringResource(R.string.annotate_copy),
                onClick = { onExport(ExportKind.Copy) },
            )
            ExportAction(
                icon = Icons.Default.Share,
                label = stringResource(R.string.annotate_share),
                onClick = { onExport(ExportKind.Share) },
            )
            PressTextButton(onClick = { onPanel(AnnotatePanelKind.None) }) {
                Text(stringResource(R.string.annotate_cancel))
            }
        }

        panel == AnnotatePanelKind.ConfirmExit -> ConfirmPanel(
            title = stringResource(R.string.annotate_discard_title),
            onCancel = { onPanel(AnnotatePanelKind.None) },
        ) {
            PressTextButton(
                onClick = onConfirmExit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.annotate_discard_confirm))
            }
        }
    }
}

/**
 * 面板卡片容器：surfaceContainerHigh 底 + 容器角色形状，无阴影
 * （弹出卡与工具卡同一视觉层次，层次靠色差）。宽度填满所在容器，与工具卡同宽。
 */
@Composable
fun AnnotatePanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LargeShape,
        color = raisedSurfaceColor(),
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

/** 确认卡：标题 + 动作区 + 「取消」。纵向堆叠，避免窄屏把按钮挤成两行。 */
@Composable
fun ConfirmPanel(
    title: String,
    onCancel: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 180.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        actions()
        PressTextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.annotate_cancel))
        }
    }
}

/** 导出动作图标按钮：内容描述即标签，触达区/图标尺寸用紧凑常量。 */
@Composable
fun ExportAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    PressIconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
    }
}
