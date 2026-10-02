package hunoia.luno.ui.component

import android.graphics.Color as AndroidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 手势按钮/子手势的标记色约定：`colorInt` 为透明时表示「跟随主题主色」。
 *
 * 这个约定散落在 4 个屏幕里各写一遍 `when`，改约定时要同时改 4 处，收到这里。
 * 注意与 `SideGestureContainer` 区分 —— 那里需要的是 null（表示未设置）而不是主题色。
 */
fun gestureButtonMarkColor(colorInt: Int, primary: Color, alpha: Float = 1f): Color =
    if (colorInt == AndroidColor.TRANSPARENT) {
        primary.copy(alpha = alpha)
    } else {
        Color(colorInt).copy(alpha = alpha)
    }

@Composable
fun gestureButtonMarkColor(colorInt: Int, alpha: Float = 1f): Color =
    gestureButtonMarkColor(colorInt, MaterialTheme.colorScheme.primary, alpha)
