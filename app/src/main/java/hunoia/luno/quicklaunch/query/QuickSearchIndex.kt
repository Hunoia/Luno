package hunoia.luno.quicklaunch.query

import android.content.Context
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryRefData
import hunoia.luno.config.model.NewActionData
import hunoia.luno.config.model.VALUE_LIBRARY_REF
import hunoia.luno.config.model.VALUE_NEW_ACTION
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import java.net.URLEncoder

/**
 * 快速搜索索引：懒加载各数据源，按 query 的单字 token 做拼音/首字母/原文 tier 匹配。
 *
 * 加载分两层：[ensureLoaded] 只做无 IO 的定义表；[refreshApps] 查包（含禁用应用）
 * 必须在后台线程调，由 overlay 的 show() 触发，[search] 内保留兜底。
 */
class QuickSearchIndex(internal val context: Context) {

    internal var actions: List<QuickSearchResult.ActionDef> = emptyList()
    internal var settingsPages: List<QuickSearchResult.Settings> = emptyList()
    internal var apps: List<AppInfo> = emptyList()
    internal var disabledPkgs: Set<String> = emptySet()
    internal var shortcuts: List<LauncherInfo.ShortcutInfo> = emptyList()
    /** 联系人缓存：面板打开后在 IO 线程一次性加载，打字时只查内存。 */
    internal var contacts: List<QuickSearchResult.Contact> = emptyList()
    @Volatile internal var defsLoaded = false
    @Volatile internal var appsLoaded = false
    @Volatile internal var contactsLoaded = false

    // ---- P3 媒体/文件：与联系人同一策略（面板打开 IO 一次加载，打字只查内存） ----

    internal var tracks: List<QuickSearchResult.AudioTrack> = emptyList()
    internal var mediaFiles: List<QuickSearchResult.ImageVideo> = emptyList()
    internal var docs: List<QuickSearchResult.DocFile> = emptyList()
    // 分源 loaded：后授权回来要补装，没试过的源才试，已试过的不重复查库。
    @Volatile internal var audioTried = false
    @Volatile internal var galleryTried = false
    @Volatile internal var docsTried = false

    // ---- 日历/短信/通话：与联系人同一策略（IO 一次加载，打字只查内存） ----

    internal var events: List<QuickSearchResult.CalendarEvent> = emptyList()
    internal var smsList: List<QuickSearchResult.SmsMsg> = emptyList()
    internal var callList: List<QuickSearchResult.CallLogEntry> = emptyList()
    @Volatile internal var calendarTried = false
    @Volatile internal var smsTried = false
    @Volatile internal var callsTried = false
    /** 手电状态是 Activity 翻的（CameraManager 无 getter），存在 index 供开关行副标题用。 */
    @Volatile var torchOn: Boolean = false

    fun ensureLoaded() {
        if (defsLoaded) return
        defsLoaded = true
        actions = ActionDefinitions.pickerDefinitions().map { def ->
            QuickSearchResult.ActionDef(
                StoredAction(def.typeId),
                def.name,
                def.icon,
            )
        }
        settingsPages = systemSettingsPages()
    }

    fun refreshApps() {
        val combined = DisabledAppQuery.queryQuickAppLauncherApps(context)
        apps = combined.apps
        disabledPkgs = combined.disabledPkgs
        shortcuts = QuickLaunchFacade.queryShortcuts(context).flatMap { it.shortcuts }
        AppSearch.warmIndexes(apps)
        // 图标不在这里预热：flow 在发射结果前按需 resolve（SearchIconCache），
        // 只解码露出的行；全量 enqueue 又慢又跟首搜 race。
        appsLoaded = true
    }
}

/** 把搜索结果转回可执行的 [Action]。 */
fun QuickSearchResult.toConfigAction(): Action = when (this) {
    is QuickSearchResult.ActionDef -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(NewActionData(stored)),
    )
    is QuickSearchResult.LibraryEntry -> Action(
        value = VALUE_LIBRARY_REF,
        data = JsonSerializer.encodeToString(ActionLibraryRefData(entry.id)),
    )
    is QuickSearchResult.App -> Action(
        value = ActionFacade.EXTRA_LAUNCH_APP,
        data = JsonSerializer.encodeToString(appInfo),
    )
    is QuickSearchResult.Shortcut -> Action(
        value = ActionFacade.EXTRA_LAUNCH_SHORTCUT,
        data = JsonSerializer.encodeToString(shortcut),
    )
    is QuickSearchResult.Settings -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(NewActionData(StoredAction.of("system.openSettings", "page" to page.name))),
    )
    is QuickSearchResult.Url -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(NewActionData(StoredAction.of("intent.openUrl", "url" to url))),
    )
    is QuickSearchResult.EngineSearch -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(
            NewActionData(
                StoredAction.of(
                    "intent.openUrl",
                    "url" to resolvedUrl(),
                ),
            ),
        ),
    )
    // Calc/联系人/媒体/日历/开关/短信/通话 由 Activity 直调 intent/剪贴板，
    // 正常走不到这里（when 穷尽要求必须有分支）。
    is QuickSearchResult.Calc -> error("Calc is handled by QuickSearchActivity")
    is QuickSearchResult.Contact -> error("Contact is handled by QuickSearchActivity")
    is QuickSearchResult.AudioTrack -> error("AudioTrack is handled by QuickSearchActivity")
    is QuickSearchResult.ImageVideo -> error("ImageVideo is handled by QuickSearchActivity")
    is QuickSearchResult.DocFile -> error("DocFile is handled by QuickSearchActivity")
    is QuickSearchResult.PhoneToggle -> error("PhoneToggle is handled by QuickSearchActivity")
    is QuickSearchResult.CalendarEvent -> error("CalendarEvent is handled by QuickSearchActivity")
    is QuickSearchResult.SmsMsg -> error("SmsMsg is handled by QuickSearchActivity")
    is QuickSearchResult.CallLogEntry -> error("CallLogEntry is handled by QuickSearchActivity")
}

/** 引擎行拼出的最终链接：长按复制与 toConfigAction 共用。 */
fun QuickSearchResult.EngineSearch.resolvedUrl(): String =
    urlTemplate.replace("%s", URLEncoder.encode(query, Charsets.UTF_8))

/**
 * 搜索行长按：应用/快捷方式进应用详情（app.details，ActionMapper 现成认 packageName）；
 * 网址/引擎走复制（调用方直调剪贴板）；其余返回 null 表示无长按语义。
 */
fun QuickSearchResult.longPressAction(): Action? = when (this) {
    is QuickSearchResult.App -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(
            NewActionData(StoredAction.of("app.details", "packageName" to appInfo.packageName)),
        ),
    )
    is QuickSearchResult.Shortcut -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(
            NewActionData(StoredAction.of("app.details", "packageName" to shortcut.packageName)),
        ),
    )
    else -> null
}
