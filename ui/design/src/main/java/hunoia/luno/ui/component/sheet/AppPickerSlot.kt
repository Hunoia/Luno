package hunoia.luno.ui.component.sheet

import androidx.compose.runtime.Composable

/**
 * 应用选择器的注入点。
 *
 * 动作库不依赖搜索 / 权限模块：选择器由 `:app` 提供，这里只声明它需要什么。
 * 用类而不直接传多个 lambda，避免参数顺序成为隐式契约。
 */
class AppPickerRequest(
    val selectedPackageNames: List<String>,
    val selectedUserId: Int?,
    val onDismiss: () -> Unit,
    val onConfirm: (List<String>) -> Unit,
    /** 单选（含分身 userId）：点击即确认。 */
    val onSelect: ((packageName: String, userId: Int) -> Unit)?,
)

typealias AppPickerSlot = @Composable (AppPickerRequest) -> Unit
