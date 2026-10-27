package hunoia.luno.quicklaunch.query

import android.content.Context
import android.util.Log
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.config.model.fillSearchQuery
import hunoia.luno.domain.action.ActionCatalog
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import hunoia.luno.action.model.Action

/**
 * 应用/快捷方式索引快照：一次 refresh 内三个关联字段同时发布，
 * search 侧不会看到「新 apps + 旧 shortcuts/disabledPkgs」的跨代组合。
 */
internal data class AppIndexData(
    val apps: List<AppInfo> = emptyList(),
    val disabledPkgs: Set<String> = emptySet(),
    val shortcuts: List<LauncherInfo.ShortcutInfo> = emptyList(),
)

/**
 * 快速搜索索引：懒加载各数据源，按 query 的单字 token 做拼音/首字母/原文 tier 匹配。
 * 加载分两层：[ensureLoaded] 只做无 IO 的定义表；[refreshApps] 查包（含禁用应用）必须在后台线程调，
 * 由 overlay 的 show() 触发，[search] 内保留兜底。
 */
class QuickSearchIndex(internal val context: Context) {

    internal var actions: List<QuickSearchResult.ActionDef> = emptyList()
    internal var settingsPages: List<QuickSearchResult.Settings> = emptyList()
    /** 应用/快捷方式/禁用包同代发布，避免读者拼出跨代数据。 */
    @Volatile internal var appData = AppIndexData()
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

    /** 加载互斥：各源"查一次就够"的闸门靠它保证只跑一遍。 */
    private val loadLock = Any()
    @Volatile private var refreshingApps = false
    @Volatile private var invalidateRequested = false

    /**
     * 索引版本：ensureLoaded / refreshApps 发布新数据后自增。
     * Compose 侧不能直接观察 @Volatile 字段，用 StateFlow 把「数据变了」带进重组。
     */
    private val _indexVersion = MutableStateFlow(0)
    val indexVersion: StateFlow<Int> = _indexVersion.asStateFlow()

    /**
     * 关闭搜索面板后释放体积大、且下次可重新扫描的会话数据（联系人/媒体/文件/
     * 日历/短信/通话）。应用与快捷方式索引保留，避免再次全量查 PackageManager。
     */
    fun trimSessionSources() {
        contacts = emptyList()
        contactsLoaded = false
        tracks = emptyList()
        mediaFiles = emptyList()
        docs = emptyList()
        audioTried = false
        galleryTried = false
        docsTried = false
        events = emptyList()
        smsList = emptyList()
        callList = emptyList()
        calendarTried = false
        smsTried = false
        callsTried = false
    }

    fun ensureLoaded() {
        if (defsLoaded) return
        // 先在局部变量里建好，再一次性发布：并发下不能出现「已加载但 actions 还是空」，
        // 否则搜应用名直接整表丢失。
        val builtActions = ActionCatalog.searchDefinitions().map { def ->
            QuickSearchResult.ActionDef(
                StoredAction(def.typeId),
                def.name,
                def.icon,
            )
        }
        val builtPages = systemSettingsPages()
        synchronized(loadLock) {
            if (defsLoaded) return
            actions = builtActions
            settingsPages = builtPages
            defsLoaded = true
            _indexVersion.value++
        }
    }

    fun refreshApps() {
        // 已加载就跳过：每次调用都全量查包 + 查快捷方式（PackageManager 几百毫秒），
        // 面板重开一次就白跑一次。需要重刷（装卸应用）由调用方先失效后调。
        if (appsLoaded) return
        synchronized(loadLock) {
            if (appsLoaded || refreshingApps) return
            refreshingApps = true
        }
        try {
            val combined = DisabledAppQuery.queryQuickAppLauncherApps(context)
            val builtShortcuts = QuickLaunchFacade.queryShortcuts(context).flatMap { it.shortcuts }
            AppSearch.warmIndexes(combined.apps)
            AppSearch.warmShortcutIndexes(builtShortcuts)
            // 图标不在这里预热：flow 在发射结果前按需 resolve（SearchIconCache），
            // 只解码露出的行；全量 enqueue 又慢又跟首搜 race。
            synchronized(loadLock) {
                appData = AppIndexData(
                    apps = combined.apps,
                    disabledPkgs = combined.disabledPkgs,
                    shortcuts = builtShortcuts,
                )
                appsLoaded = true
                _indexVersion.value++
            }
        } catch (e: Exception) {
            // PackageManager/快捷方式查询在个别 ROM 上会抛；保留旧数据并留待下次重试，
            // 不让异常冒泡杀掉 Activity 或 Compose 协程。
            Log.e("LunoSearch", "refreshApps failed", e)
        } finally {
            val rerun = synchronized(loadLock) {
                refreshingApps = false
                val rerun = invalidateRequested
                invalidateRequested = false
                if (rerun) appsLoaded = false
                rerun
            }
            // 扫描期间又收到装卸应用：本轮数据已过期，立即重扫一次。
            if (rerun) refreshApps()
        }
    }

    /** 强制重刷应用索引（装卸应用后）：清闸门再走 [refreshApps]。 */
    fun invalidateApps() {
        val duringRefresh = synchronized(loadLock) {
            appsLoaded = false
            if (refreshingApps) {
                invalidateRequested = true
                true
            } else {
                false
            }
        }
        if (!duringRefresh) refreshApps()
    }

    /**
     * 只标记应用索引过期，不立刻重扫。用于进程级缓存的后台包广播：面板没开时装卸应用
     * 不应触发 PackageManager 全量扫描（耗电），等下次 [refreshApps] 再补。
     */
    fun markAppsStale() {
        synchronized(loadLock) {
            appsLoaded = false
            if (refreshingApps) invalidateRequested = true
        }
    }
}

/**
 * 把结果转回可交给 dispatcher 的 [hunoia.luno.action.model.StoredAction]。
 * 展示/直开类结果（联系人、媒体、开关等）由 [toSearchResult] 转成
 * [SearchAction]，不经过这里；这些分支仅为 when 穷尽保留。
 */
fun QuickSearchResult.toConfigAction(): StoredAction = when (this) {
    is QuickSearchResult.ActionDef -> stored
    is QuickSearchResult.LibraryEntry -> StoredAction.of("internal.libraryRef", "entryId" to entry.id)
    is QuickSearchResult.App -> StoredAction.of(
        "app.launch",
        "packageName" to appInfo.packageName,
        "className" to appInfo.className,
        "miniWindow" to appInfo.miniWindow.toString(),
    )
    is QuickSearchResult.Shortcut -> StoredAction.of(
        "app.launchShortcut",
        "data" to JsonSerializer.encodeToString(shortcut),
    )
    is QuickSearchResult.Settings -> StoredAction.of("system.openSettings", "page" to page.name)
    is QuickSearchResult.Url -> StoredAction.of("intent.openUrl", "url" to url)
    is QuickSearchResult.EngineSearch -> when (val launch = engine.effectiveLaunch()) {
        is SearchLaunch.Url -> StoredAction.of(
            "intent.openUrl",
            "url" to launch.template.fillSearchQuery(query),
        )
        is SearchLaunch.Chooser -> StoredAction.of(
            "intent.openUrl",
            "url" to launch.template.fillSearchQuery(query),
        )
        is SearchLaunch.Share -> StoredAction.of(
            "intent.shareText",
            "text" to launch.textTemplate.fillSearchQuery(query),
        )
        is SearchLaunch.ShareImage -> StoredAction.of(Action.NONE_TYPE_ID)
        is SearchLaunch.Action -> launch.action.fillSearchQuery(query)
    }
    // Calc/联系人/媒体/日历/开关/短信/通话 在 toSearchResult 里转成
    // SearchAction.Open/Copy/SystemToggle，由 Activity 直接执行。
    is QuickSearchResult.Calc -> error("Calc is handled by QuickSearchActivity")
    is QuickSearchResult.Contact -> error("Contact is handled by QuickSearchActivity")
    is QuickSearchResult.AudioTrack -> error("AudioTrack is handled by QuickSearchActivity")
    is QuickSearchResult.ImageVideo -> error("ImageVideo is handled by QuickSearchActivity")
    is QuickSearchResult.DocFile -> error("DocFile is handled by QuickSearchActivity")
    is QuickSearchResult.PhoneToggle -> error("PhoneToggle is handled by QuickSearchActivity")
    is QuickSearchResult.CalendarEvent -> error("CalendarEvent is handled by QuickSearchActivity")
    is QuickSearchResult.SmsMsg -> error("SmsMsg is handled by QuickSearchActivity")
    is QuickSearchResult.CallLogEntry -> error("CallLogEntry is handled by QuickSearchActivity")
    is QuickSearchResult.ClipEntry -> error("ClipEntry is handled by QuickSearchActivity")
}
