package hunoia.luno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 语义化颜色令牌：颜色本身来自 M3 `ColorScheme`（`AppTheme` 强制动态取色），
 * 这里只放跨组件复用的固定色值。
 */

/** 禁用态整体透明度。所有组件的 `enabled = false` 走这一个值。 */
const val DisabledAlpha: Float = 0.36f

/** 面板内可点方块底色（动作格 / 内置部件底 / 键盘键）的不透明度。 */
const val PanelTileAlpha: Float = 0.55f

/**
 * 面板内可点方块的统一底色：动作格、内置部件底、快捷启动键盘键必须是同一块色，
 * 不各自写一份 `surfaceVariant.copy(alpha = …)`（写两份就会各自漂移）。
 */
@Composable
fun panelTileContainer(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PanelTileAlpha)
