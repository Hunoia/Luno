package hunoia.luno.gridpanel

/**
 * 代办面板部件 → 主应用跳转链路的跨层常量。
 *
 * 网格面板是独立窗口（服务侧 overlay），部件不能直接拿 NavController；复用链接分发的
 * `LinkExtras` 模式：部件经 `LocalContext` 发一个显式 `Intent`（带打开代办列表标记）拉起
 * [hunoia.luno.ui.MainActivity]，Activity 写入 `PendingTodoList`，导航图消费一次即进列表页。
 * 添加 / 编辑代办都在列表页弹窗完成（overlay 内不能安全弹系统 Dialog）。
 * 组件名与 extra key 收口于此，避免 feature 层 import app 层。
 */
object TodoExtras {

    /** 主界面 Activity（显式组件名，跨进程可用）。 */
    const val ACTIVITY_MAIN = "hunoia.luno.ui.MainActivity"

    /** 打开代办列表页（`Boolean`，存在即触发）。 */
    const val EXTRA_OPEN_TODO_LIST = "hunoia.luno.extra.OPEN_TODO_LIST"

    /** 目标代办 id（`String`）：存在 = 编辑该条；不存在 = 新增。 */
    const val EXTRA_TODO_ID = "hunoia.luno.extra.TODO_ID"
}
