package hunoia.luno.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow

/**
 * 主页权限状态卡：**一行摘要 + 右侧一键授权**，无分组标题，逐项解释留给授权页。
 *
 * - 只数缺口，不分类：`missing` 里「必须」与「降级」不分开，主页不承担解释成本（授权页才列明细）。
 * - 点行去授权页；点右侧按钮在本页就直接补当前缺失项（只补被跟踪的这几项，不静默扩权）。
 * - Shizuku 不可用时按钮降级为「需 Shizuku」并跳授权页：那里才有修 Shizuku 的入口，
 *   主页不做「点了没反应」的授权尝试。
 * - 无缺失时调用方不组合本卡（不占空间）。
 */
@Composable
fun HomePermissionStatusCard(
    missingCount: Int,
    shizukuReady: Boolean,
    isGranting: Boolean,
    onOpen: () -> Unit,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LunoGroup(modifier = modifier) {
        LunoSettingsRow(
            title = pluralStringResource(R.plurals.home_permission_pending, missingCount, missingCount),
            // 副标题只说标题没说的（怎么进去），不重复“未授权”这个状态。
            subtitle = stringResource(R.string.home_permission_pending_hint),
            icon = Icons.Filled.Warning,
            onClick = onOpen,
            trailing = {
                PressTextButton(onClick = onGrant, enabled = !isGranting) {
                    Text(
                        text = stringResource(
                            when {
                                isGranting -> R.string.privileged_granting
                                shizukuReady -> R.string.home_permission_grant
                                else -> R.string.home_permission_grant_needs_shizuku
                            },
                        ),
                    )
                }
            },
        )
    }
}
