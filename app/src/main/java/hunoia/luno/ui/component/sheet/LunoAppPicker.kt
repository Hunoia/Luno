package hunoia.luno.ui.component.sheet

import androidx.compose.runtime.Composable

/**
 * `AppPickerSlot` 的唯一实现：把请求翻译成 [AppPickerSheet]。
 *
 * 页面只声明「要选应用」，插槽形状在 `:ui:design`，具体选择器在 `:app`。
 * 之前 4 个页面各写一遍同样的接线，参数差异（单选/多选、分身）散落在调用方。
 */
@Composable
fun LunoAppPicker(request: AppPickerRequest) {
    AppPickerSheet(
        onDismissRequest = request.onDismiss,
        selectedPackageNames = request.selectedPackageNames,
        confirmOnSelect = request.confirmOnSelect,
        onSelectApp = request.onSelect?.let { select ->
            { app -> select(app.packageName, app.userId) }
        },
        selectedUserId = request.selectedUserId,
        onConfirm = request.onConfirm,
    )
}
