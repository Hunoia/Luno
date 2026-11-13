package hunoia.luno.gesture

import hunoia.luno.config.model.ColorSpec
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

    /** 球点击转发（携带按钮颜色来源，面板/手势链路从这里走同一套分发）。 */
    var forwardBallTap: ((action: StoredAction, anchorX: Int, anchorY: Int, colorSpec: ColorSpec) -> Unit)? = null

    /**
     * 面板点选转发（返回是否已接管；网格面板据此决定先退场）。
     * [foregroundHint] 是面板打开时的前台应用快照：面板这层全屏浮层会让无障碍
     * `windows` 变空，执行「当前应用」类动作时必须用快照而不是重新解析。
     */
    var forwardPanelTap: ((
        action: StoredAction,
        anchorX: Int,
        anchorY: Int,
        colorSpec: ColorSpec,
        foregroundHint: String?,
    ) -> Boolean)? = null

    /**
     * 快照「当前前台应用」：返回空字符串 = 桌面/无应用，null = 无法确定（执行时再实时解析）。
     * 面板必须在自身窗口挂上之前调用，否则全屏浮层会把下层应用窗口挡成不可见。
     */
    var snapshotForegroundApp: (() -> String?)? = null

    /** 在屏幕坐标处注入文本（剪贴板部件拖入输入框）。 */
    var insertText: (suspend (text: String, x: Int, y: Int) -> hunoia.luno.action.controller.TextInserter.Result)? = null

    /** 在无障碍焦点输入框里注入文本（悬浮窗「输入到输入框」）。 */
    var insertTextFocused: (suspend (text: String) -> hunoia.luno.action.controller.TextInserter.Result)? = null

    /** 把剪贴板里已有的图片粘进焦点输入框（调用方先把图片写进剪贴板）。 */
    var pasteImageFocused: (suspend () -> hunoia.luno.action.controller.ImageInserter.Result)? = null


    /**
     * 把本地图片副本写进剪贴板，再在屏幕坐标处粘贴。
     * 图片没有 `ACTION_SET_TEXT`，只能依赖目标编辑器支持 `ACTION_PASTE`。
     */
    var pasteImageAt: (suspend (
        imagePath: String,
        mime: String?,
        x: Int,
        y: Int,
    ) -> hunoia.luno.action.controller.ImageInserter.Result)? = null
}
