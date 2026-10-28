package hunoia.luno.gridpanel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

/**
 * 网格面板内「打开外部应用/页面」后请求关闭面板。自绘小部件自己处理点击，
 * 不经过 `ActionItemCell` 的 `execute → closeWithAnim()`；不补这次关闭，
 * 启动的应用会被全屏面板盖住，看起来像「点了没反应」。
 */
val LocalGridPanelClose = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 面板内「拿起一个内容拖到别的应用」的拖拽幽灵状态：内容区（部件）写入，面板根绘制。
 *
 * 位置是**窗口坐标**（面板根 Box 的 local 坐标）：内容区用
 * `LayoutCoordinates.positionInWindow()` 换算，落点再补窗口原点到屏幕坐标。
 * 幽灵必须画在根层 —— 格子里的内容被裁剪，拖到面板外就看不见了。
 */
class GridPanelDragGhost {
    var text by mutableStateOf("")
        private set

    var position by mutableStateOf(Offset.Zero)
        private set

    /** 拖过阈值后把面板本体淡出：窗口留着接手势，视觉上「面板关了」能看见下面的应用。 */
    var chromeHidden by mutableStateOf(false)
        private set

    val active: Boolean get() = text.isNotEmpty()

    fun begin(text: String, windowPosition: Offset) {
        if (text.isEmpty()) return
        this.text = text
        position = windowPosition
        chromeHidden = false
    }

    fun move(windowPosition: Offset) {
        if (active) position = windowPosition
    }

    fun hideChrome() {
        if (active) chromeHidden = true
    }

    fun showChrome() {
        chromeHidden = false
    }

    /** 只清幽灵，**不**恢复卡面：拖拽落点后要接着播关闭动画，提前恢复会闪一下。 */
    fun end() {
        text = ""
    }
}

/** 当前面板的拖拽幽灵；不在网格面板内（或不需要该能力）时为 null。 */
val LocalGridPanelDragGhost = staticCompositionLocalOf<GridPanelDragGhost?> { null }

/**
 * 网格面板「加项 / 换动作」请求。只带面板自身的参数，选择页路由由宿主映射，
 * 面板模块不认识 `:app` 的导航类型。
 */
data class GridPanelItemPick(
    val gridPanelId: String,
    val pageIndex: Int,
)

/**
 * 宿主注入的选择页入口（`:app` 在 service 初始化时挂上）。
 * 返回 false = 未能启动，面板会恢复自身显示。
 */
object GridPanelCommand {
    var openItemPicker: ((GridPanelItemPick) -> Boolean)? = null
}
