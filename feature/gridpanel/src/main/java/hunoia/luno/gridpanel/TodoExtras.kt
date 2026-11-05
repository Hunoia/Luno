package hunoia.luno.gridpanel

/**
 * 代办面板部件 → 主应用跳转链路的跨层常量。
 *
 * 网格面板是独立窗口（服务侧 overlay），部件不能直接拿 NavController；复用链接分发的
 * `LinkExtras` 模式：部件经 `LocalContext` 发一个显式 `Intent`（带 todoId extra）拉起
 * [hunoia.luno.ui.MainActivity]，Activity 写入 `PendingTodoEdit`，导航图消费一次即进编辑页。
 * 组件名与 extra key 收口于此，避免 feature 层 import app 层。
 */
object TodoExtras {

    /** 主界面 Activity（显式组件名，跨进程可用）。 */
    const val ACTIVITY_MAIN = "hunoia.luno.ui.MainActivity"

    /** 要打开的编辑页代办 id（`String`）。 */
    const val EXTRA_EDIT_TODO_ID = "hunoia.luno.extra.EDIT_TODO_ID"
}
