package hunoia.luno.core

import hunoia.luno.action.model.StoredAction

/**
 * 「悬浮球点按」请求打开动作面板的事件。
 *
 * 球（`FloatBallService`，`:service` 进程）与手势容器（`SideGestureContainer`，
 * 挂在无障碍主 overlay 里）不是同一份代码持有的，走 Events 总线解耦：
 * 容器在组合期订阅本事件，收到后按一个外部触发点打开 ArcPanel 待选 ——
 * 与侧滑手势里点按动作面板走**同一条**路径（`runGestureActions` 的面板分支）。
 *
 * 待选面板的选中由 [hunoia.luno.runtime.gesturepanel.DragGestureHandler]
 * 的全局拖动流接管：下一次任意拖动点亮高亮、松手执行。
 */
data class BallPanelRevealEvent(
    /** 面板引用动作（internal.actionPanel）。 */
    val action: StoredAction,
    /** 待选锚点，屏幕绝对坐标（px）。 */
    val anchorX: Int,
    val anchorY: Int,
    /** 面板主题色（球的颜色），无主题色传 0。 */
    val color: Int,
)
