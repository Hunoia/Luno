package hunoia.luno.annotate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.annotate.R
import hunoia.luno.ui.theme.CompactControlIconSize
import hunoia.luno.ui.theme.SubMinInteractiveSize

/**
 * 两个宿主共用的工具条上方 chrome：确认卡与导出动作按钮。
 *
 * 浮层宿主与图片编辑宿主的工具条容器不同（独立窗口 vs 页内 Box），
 * 但卡内排版/触达区一致，放在这里避免两份实现漂移。
 */

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
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
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
    IconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
    }
}
