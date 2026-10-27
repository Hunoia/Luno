package hunoia.luno.ui.component

import android.graphics.Color as AndroidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 手势按钮/子手势的标记色：`colorInt` 为透明时跟随主题主色。
 *
 * 与 `SideGestureContainer` 区分——那里需要 null（未设置），不是主题色。
 */
fun gestureButtonMarkColor(colorInt: Int, primary: Color, alpha: Float = 1f): Color =
    if (colorInt == AndroidColor.TRANSPARENT) {
        primary.copy(alpha = alpha)
    } else {
        // alpha 直用颜色选择页定的值，不被调用方覆盖。
        Color(colorInt)
    }

@Composable
fun gestureButtonMarkColor(colorInt: Int, alpha: Float = 1f): Color =
    gestureButtonMarkColor(colorInt, MaterialTheme.colorScheme.primary, alpha)
