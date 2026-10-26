package hunoia.luno.link

/**
 * 链接分发链路的跨组件常量（Intent extras 与组件名）。
 *
 * 放这里而不是各自写死：`LinkDispatcher`（胶囊所在 `:service` 进程，也能从动作/规则调用）
 * 需要直接拉起 `ui.link` 的页面，但 link 层不该 import ui 层；组件名与 extra key
 * 统一收口于此，两边引用同一份常量，避免字符串漂移。
 */
object LinkExtras {

    /** 多链接列表（`ArrayList<String>`）。 */
    const val EXTRA_URLS = "hunoia.luno.extra.LINK_URLS"

    /** 选应用模式的单个链接。 */
    const val EXTRA_PICK_URL = "hunoia.luno.extra.LINK_PICK_URL"

    /** 多链接选择 / 选应用页。 */
    const val ACTIVITY_SELECT = "hunoia.luno.ui.link.LinkSelectionActivity"

    /** 外部链接接收口（默认浏览器 / 分享到 Luno）。 */
    const val ACTIVITY_DISPATCH = "hunoia.luno.ui.link.LinkDispatchActivity"
}
