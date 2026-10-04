package hunoia.luno.quicklaunch.query

import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.StoredAction
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.action.model.SettingsPage

/**
 * 快速搜索结果：一个可被 [ActionDispatcher] 执行的条目。
 * [toConfigAction] 把结果转回 [Action] 交给 [hunoia.luno.runtime.GestureHost.dispatchAction] 执行。
 */
sealed interface QuickSearchResult {
    val title: String
    val subtitle: String

    /** 内置动作（新模型 typeId + 参数） */
    data class ActionDef(val stored: StoredAction, override val title: String, val icon: Any?) : QuickSearchResult {
        override val subtitle: String get() = ""
    }
    /** 动作库条目引用 */
    data class LibraryEntry(val entry: NewActionLibraryEntry, val fallbackTitle: String, val icon: Any?) : QuickSearchResult {
        override val title: String get() = entry.name.ifBlank { fallbackTitle }
        override val subtitle: String get() = fallbackTitle
    }
    /** 启动应用（含禁用应用：点击走启用并启动流程） */
    data class App(val appInfo: AppInfo, val isDisabled: Boolean = false) : QuickSearchResult {
        override val title: String get() = appInfo.label
        override val subtitle: String get() = appInfo.packageName
    }
    /** 启动快捷方式 */
    data class Shortcut(val shortcut: LauncherInfo.ShortcutInfo) : QuickSearchResult {
        override val title: String get() = shortcut.label
        override val subtitle: String get() = shortcut.packageName
    }
    /** 打开系统设置页 */
    data class Settings(val page: SettingsPage, override val title: String) : QuickSearchResult {
        override val subtitle: String get() = ""
    }
    /** 打开网页 */
    data class Url(val url: String) : QuickSearchResult {
        override val title: String get() = url
        override val subtitle: String get() = ""
    }
    /** 搜索引擎兜底：副标题为空（输入框已显示查询内容，行上只留引擎名）。 */
    data class EngineSearch(
        val query: String,
        val engineTitle: String,
        val urlTemplate: String,
        val engineName: String = "",
    ) : QuickSearchResult {
        override val title: String get() = engineTitle
        override val subtitle: String get() = ""
    }
    /** 计算器：标题=结果，副标题=算式；点击由 Activity 直接复制，不进 dispatcher。 */
    data class Calc(val expression: String, val value: String) : QuickSearchResult {
        override val title: String get() = value
        override val subtitle: String get() = expression
    }
    /** 联系人：标题=姓名，副标题=号码；点拨号、长按短信，Activity 直调 intent。 */
    data class Contact(val name: String, val number: String) : QuickSearchResult {
        override val title: String get() = name
        override val subtitle: String get() = number
    }
    /** 音乐：标题=歌名，副标题=艺术家；点直接播（默认播放器），无长按语义。 */
    data class AudioTrack(override val title: String, val artist: String, val uri: String) : QuickSearchResult {
        override val subtitle: String get() = artist
    }
    /** 图片/视频：标题=文件名，副标题=相册名；点开、长按分享。 */
    data class ImageVideo(val name: String, val bucket: String, val uri: String, val mime: String, val modified: Long = 0) : QuickSearchResult {
        override val title: String get() = name
        override val subtitle: String get() = bucket
    }
    /** 文档（非媒体）：标题=文件名，副标题=大小；点开、长按分享。 */
    data class DocFile(val name: String, val sizeText: String, val uri: String, val mime: String, val modified: Long = 0) : QuickSearchResult {
        override val title: String get() = name
        override val subtitle: String get() = sizeText
    }
    /** 开关：标题=开关名，副标题=当前状态（未知则空）；能直切就切、否则跳设置，Activity 直调。 */
    enum class ToggleKind { WIFI, BLUETOOTH, TORCH, DND, LOCATION, HOTSPOT }
    data class PhoneToggle(val kind: ToggleKind, override val title: String, val keywords: String, override val subtitle: String) : QuickSearchResult
    /** 日历事件：标题=事件名，副标题=时间地点；点看事件、长按复制，Activity 直调。 */
    data class CalendarEvent(val title_: String, val timeText: String, val location: String, val eventId: Long, val beginMillis: Long = 0) : QuickSearchResult {
        override val title: String get() = title_
        override val subtitle: String get() = listOf(timeText, location).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    /** 短信：标题=正文首行，副标题=发件人时间；点开会话、长按复制正文，Activity 直调。 */
    data class SmsMsg(val address: String, val body: String, val dateText: String) : QuickSearchResult {
        override val title: String get() = body.lines().firstOrNull()?.trim()?.take(40).orEmpty().ifBlank { address }
        override val subtitle: String get() = listOf(address, dateText).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    /** 通话记录：标题=姓名（无则号码），副标题=号码类型时间；点拨号、长按短信，Activity 直调。 */
    data class CallLogEntry(val name: String, val number: String, val typeText: String, val dateText: String) : QuickSearchResult {
        override val title: String get() = name.ifBlank { number }
        override val subtitle: String get() = listOf(number, typeText, dateText).filter { it.isNotEmpty() }.joinToString(" · ")
    }
}
