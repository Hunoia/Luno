package hunoia.luno.ui.component.sheet

import androidx.compose.runtime.Composable

/**
 * `AppPickerSlot` 的唯一实现：把请求翻译成应用选择器。
 *
 * 页面只声明「要选应用」，插槽形状在 `:ui:design`，具体选择器在 `:app`。
 * 按语义分派：`confirmOnSelect == false`（多选）走全屏页 [AppPickerPage]（最近应用
 * 黑名单同款），其余（单选）走 [AppPickerSheet]（点行即确认，无确认/取消）。
 */
@Composable
fun LunoAppPicker(request: AppPickerRequest) {
    if (request.confirmOnSelect == false) {
        AppPickerPage(request)
    } else {
        AppPickerSheet(
            onDismissRequest = request.onDismiss,
            selectedPackageNames = request.selectedPackageNames,
            onSelectApp = request.onSelect?.let { select ->
                { app -> select(app.packageName, app.userId) }
            },
            selectedUserId = request.selectedUserId,
            onConfirm = request.onConfirm,
        )
    }
}
