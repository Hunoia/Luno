package hunoia.luno.gesture

import hunoia.luno.core.domain.StoredAction

/**
 * 非手势 Surface（悬浮球、剪贴板胶囊）向手势服务下发命令的注入点。
 *
 * 悬浮球是独立窗口/服务，不能直接拿到 `GestureCoordinator`；由 `SideGestureService`
 * 在创建时注入实现，球侧只表达「按哪个动作开面板 / 转发点击」，不持有宿主引用。
 */
object GestureCommand {
    /** 网格面板直开（球上网格按钮）。 */
    var showGridPanel: ((action: StoredAction, anchorX: Int, anchorY: Int) -> Unit)? = null

    /** 球点击转发（携带按钮颜色，面板/手势链路从这里走同一套分发）。 */
    var forwardBallTap: ((action: StoredAction, anchorX: Int, anchorY: Int, color: Int) -> Unit)? = null
}
