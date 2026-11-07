package hunoia.luno.gridpanel

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 代办面板部件 → 主应用跳转链路的跨层常量。
 *
 * 网格面板是独立窗口（服务侧 overlay），部件不能直接拿 NavController；复用链接分发的
 * `LinkExtras` 模式：部件经 `LocalContext` 发一个显式 `Intent`（带打开代办列表标记）拉起
 * [hunoia.luno.ui.MainActivity]，Activity 写入 `TodoListRequests`，导航图消费一次即进列表页。
 * 列表页只负责展示；某条 → 编辑时再由列表页开弹窗。
 * 组件名与 extra key 收口于此，避免 feature 层 import app 层。
 */
object TodoExtras {

    /** 主界面 Activity（显式组件名，跨进程可用）。 */
    const val ACTIVITY_MAIN = "hunoia.luno.ui.MainActivity"

    /**
     * 待办编辑弹窗 Activity（Glance 部件「+」/点行直达，不进应用页面）。
     * 显式组件名，跨进程可用；`EXTRA_TODO_ID` 空 = 新增，非空 = 编辑。
     */
    const val ACTIVITY_TODO_EDIT = "hunoia.luno.widget.TodoEditDialogActivity"

    /** 打开代办列表页（`Boolean`，存在即触发；不自动开新建/编辑弹窗）。 */
    const val EXTRA_OPEN_TODO_LIST = "hunoia.luno.extra.OPEN_TODO_LIST"

    /** 目标代办 id（`String`）：存在 = 编辑该条；不存在 = 只打开列表页。 */
    const val EXTRA_TODO_ID = "hunoia.luno.extra.TODO_ID"

    /** 待办列表页（不自动开弹窗）；内置部件标题与桌面部件标题共用。 */
    fun openListIntent(context: Context): Intent = Intent().apply {
        component = ComponentName(context.packageName, ACTIVITY_MAIN)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_OPEN_TODO_LIST, true)
    }

    /**
     * 待办编辑弹窗：内置部件 / 桌面部件共用。
     *
     * 刻意不带 `SINGLE_TOP`：Activity 只在 `onCreate` 读 `todoId`，复用已有实例会把
     * 「编辑 A」变成复用「新增」实例（表现为点哪都是新建）。
     */
    fun editIntent(context: Context, todoId: String? = null): Intent = Intent().apply {
        component = ComponentName(context.packageName, ACTIVITY_TODO_EDIT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (todoId != null) putExtra(EXTRA_TODO_ID, todoId)
    }
}
