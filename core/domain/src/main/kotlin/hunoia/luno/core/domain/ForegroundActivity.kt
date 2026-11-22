package hunoia.luno.core.domain

/**
 * 当前前台界面的活动信息（a11y `TYPE_WINDOW_STATE_CHANGED` 事件直出）。
 *
 * [activityClass] 是事件 `className`：通常就是新窗口所属的 Activity 类全名，
 * 个别应用可能投其他窗口类名——按「最近一次新窗口」原样展示，不做二次猜测。
 */
data class ForegroundActivity(
    val packageName: String,
    val activityClass: String,
) {
    /** 完整组件名（`pkg/pkg.Activity`）：复制与第二行展示都用它。 */
    val componentName: String get() = "$packageName/$activityClass"

    /** Activity 类短名（去包前缀）：第一行与「应用名」拼用。 */
    val shortClassName: String
        get() = activityClass.substringAfterLast('.')
}