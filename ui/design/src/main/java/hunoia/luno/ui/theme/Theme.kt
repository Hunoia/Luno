package hunoia.luno.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

@Composable
fun SideGestureTheme(
    // 默认必须是稳定单例（Unit）：Any() 每次求值都是新实例，key 变化会销毁重建
    // 整个 AppTheme 子树 —— 调用方一旦把状态读出 trailing lambda，这就是隐雷。
    wallpaperChangeTrigger: Any = Unit,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    key(wallpaperChangeTrigger) {
        AppTheme(darkTheme = darkTheme) {
            content()
        }
    }
}
