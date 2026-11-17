package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import hunoia.luno.R
import hunoia.luno.ui.settings.action.RecentAppsBlocklistScreen

/**
 * 应用选择全屏页（多选）：与最近应用黑名单页同款交互——拼音分节 + 底部玻璃胶囊
 * [字母索引条 + 搜索 + 保存]，点保存才落盘，返回即取消。
 *
 * 多选调用方在 `:feature` 模块、无 navController，无法导航到独立路由；这里用边到边
 * 全屏 [Dialog]（[AnchoredDialogProperties]）承载 [RecentAppsBlocklistScreen]，视觉与
 * 交互和黑名单页一致，且对任意调用位置（sheet 槽位 / 顶层组合）统一生效。
 *
 * 单选走 [AppPickerSheet]，由 [LunoAppPicker] 按 [AppPickerRequest.confirmOnSelect] 分派。
 */
@Composable
fun AppPickerPage(request: AppPickerRequest) {
    Dialog(
        onDismissRequest = request.onDismiss,
        properties = AnchoredDialogProperties,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            RecentAppsBlocklistScreen(
                title = stringResource(R.string.app_picker_select_title),
                current = request.selectedPackageNames,
                onSave = { ordered ->
                    request.onConfirm(ordered)
                    request.onDismiss()
                },
                onBack = request.onDismiss,
            )
        }
    }
}
