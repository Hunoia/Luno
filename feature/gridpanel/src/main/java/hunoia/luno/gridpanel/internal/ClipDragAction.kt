package hunoia.luno.gridpanel.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.gridpanel.R

/**
 * 剪贴板条目拖拽动作条的动作。与识屏工具条同思路：拖动时屏幕边缘浮出动作条，
 * 松手落在哪一格执行哪个动作；**落空（条体范围外松手）= 默认插入到落点**。
 * 直接松手就是插入，所以工具条里不再放「插入」格；[CANCEL] 给的是「拖出去了又反悔」的出路。
 *
 * 动作条的几何与渲染是共享实现（`:ui:design` 的 `EdgeBarLayout` / `EdgeActionBar`，
 * 与识屏工具条同一份），这里只声明「有哪些动作、各自的图标与文案」。
 */
internal enum class ClipDragAction {
    /** 取消本次拖拽：幽灵消失、面板恢复，不向目标应用写入任何内容。 */
    CANCEL,

    /** 写回系统剪贴板。 */
    COPY,

    /** 钉到剪贴板悬浮窗。 */
    FLOAT,

    /** 系统分享面板。 */
    SHARE,
    ;

    val icon: ImageVector
        get() = when (this) {
            CANCEL -> Icons.Default.Close
            COPY -> Icons.Default.ContentCopy
            FLOAT -> Icons.Default.PushPin
            SHARE -> Icons.Default.Share
        }

    val labelRes: Int
        get() = when (this) {
            CANCEL -> R.string.grid_panel_clipboard_cancel
            COPY -> R.string.grid_panel_clipboard_copy
            FLOAT -> R.string.grid_panel_clipboard_float
            SHARE -> R.string.grid_panel_clipboard_share
        }
}
