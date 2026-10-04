package hunoia.luno.quicklaunch.query

import android.content.Context
import hunoia.luno.R
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.SettingsPage
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryRefData
import hunoia.luno.config.model.DefaultSourceTriggers
import hunoia.luno.config.model.NewActionData
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SRC_ACTION
import hunoia.luno.config.model.SRC_APP
import hunoia.luno.config.model.SRC_CALENDAR
import hunoia.luno.config.model.SRC_CALL
import hunoia.luno.config.model.SRC_CONTACT
import hunoia.luno.config.model.SRC_FILE
import hunoia.luno.config.model.SRC_MUSIC
import hunoia.luno.config.model.SRC_SETTINGS
import hunoia.luno.config.model.SRC_SHORTCUT
import hunoia.luno.config.model.SRC_SMS
import hunoia.luno.config.model.SRC_TOGGLE
import hunoia.luno.config.model.VALUE_LIBRARY_REF
import hunoia.luno.config.model.VALUE_NEW_ACTION
import hunoia.luno.config.model.defaultEngine
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.query.AppSearch.key
import hunoia.luno.ui.component.resolvedIcon
import java.net.URLEncoder

/** 裸标识列出该类近项的上限（应用走常用 8，不用这个）。 */
private const val BARE_LIST_LIMIT = 20

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

/**
 * 快速搜索索引：懒加载各数据源，按 query 的单字 token 做拼音/首字母/原文 tier 匹配。
 *
 * 加载分两层：[ensureLoaded] 只做无 IO 的定义表；[refreshApps] 查包（含禁用应用）
 * 必须在后台线程调，由 overlay 的 show() 触发，[search] 内保留兜底。
 */
class QuickSearchIndex(private val context: Context) {

    private var actions: List<QuickSearchResult.ActionDef> = emptyList()
    private var settingsPages: List<QuickSearchResult.Settings> = emptyList()
    private var apps: List<AppInfo> = emptyList()
    private var disabledPkgs: Set<String> = emptySet()
    private var shortcuts: List<LauncherInfo.ShortcutInfo> = emptyList()
    /** 联系人缓存：面板打开后在 IO 线程一次性加载，打字时只查内存。 */
    private var contacts: List<QuickSearchResult.Contact> = emptyList()
    @Volatile private var defsLoaded = false
    @Volatile private var appsLoaded = false
    @Volatile private var contactsLoaded = false

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
        appsLoaded = true
    }

    fun hasContactsPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * 联系人加载：必须在后台线程调（调用方 LaunchedEffect + IO）。
     * 无权限直接记空（调用方展示授权行）；按人聚合去重，cap 2000 防大通讯录卡顿。
     */
    fun ensureContacts() {
        if (contactsLoaded) return
        contactsLoaded = true
        if (!hasContactsPermission()) return
        val list = mutableListOf<QuickSearchResult.Contact>()
        runCatching {
            val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                )
                val numIdx = cursor.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                )
                while (cursor.moveToNext() && list.size < 2000) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        list.add(QuickSearchResult.Contact(name, number))
                    }
                }
            }
        }
        contacts = list.distinctBy { it.name to it.number }
        for (c in contacts) {
            AppSearch.buildIndex(c.name)
            AppSearch.buildIndex(c.number)
        }
    }

    /** 联系人是否已就绪（权限 + 加载完成）：search 只查内存，不在主线程碰库。 */
    fun contactsReady(): Boolean = contactsLoaded && hasContactsPermission()

    // ---- P3 媒体/文件：与联系人同一策略（面板打开 IO 一次加载，打字只查内存） ----

    private var tracks: List<QuickSearchResult.AudioTrack> = emptyList()
    private var mediaFiles: List<QuickSearchResult.ImageVideo> = emptyList()
    private var docs: List<QuickSearchResult.DocFile> = emptyList()
    // 分源 loaded：后授权回来要补装，没试过的源才试，已试过的不重复查库。
    @Volatile private var audioTried = false
    @Volatile private var galleryTried = false
    @Volatile private var docsTried = false

    fun hasMediaPermission(kind: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(context, kind) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hasAllFilesAccess(): Boolean = android.os.Environment.isExternalStorageManager()

    /**
     * 音乐 + 图片视频 + 文档加载：必须在后台线程调。
     * 有啥权限装啥（部分授权也认）；文档只取非媒体 mime，避开跟图库/音乐重复行。
     */
    fun ensureMedia() {
        val audioOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
        val imageOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
        val videoOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
        if (audioOk && !audioTried) {
            audioTried = true
            val list = mutableListOf<QuickSearchResult.AudioTrack>()
            runCatching {
                val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST,
                )
                val sort = "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
                context.contentResolver.query(uri, projection, null, null, sort)?.use { c ->
                    val idIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media._ID)
                    val titleIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
                    val artistIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
                    while (c.moveToNext() && list.size < 3000) {
                        val title = c.getString(titleIdx)?.trim().orEmpty()
                        if (title.isEmpty()) continue
                        val id = c.getLong(idIdx)
                        val artist = c.getString(artistIdx)?.trim().orEmpty()
                        val contentUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                        list.add(QuickSearchResult.AudioTrack(title, artist, contentUri))
                    }
                }
            }
            tracks = list.distinctBy { it.title to it.artist }
            for (t in tracks) {
                AppSearch.buildIndex(t.title)
                AppSearch.buildIndex(t.artist)
            }
        }
        if ((imageOk || videoOk) && !galleryTried) {
            galleryTried = true
            val list = mutableListOf<QuickSearchResult.ImageVideo>()
            runCatching {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    android.provider.MediaStore.Files.FileColumns._ID,
                    android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                    android.provider.MediaStore.Files.FileColumns.MIME_TYPE,
                    android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
                )
                // 图库只取图片/视频（音频归音乐，文档归 DocFile，三方互斥不重复）。
                val sel = StringBuilder("(")
                if (imageOk) sel.append("${android.provider.MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%'")
                if (imageOk && videoOk) sel.append(" OR ")
                if (videoOk) sel.append("${android.provider.MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%'")
                sel.append(")")
                val sort = "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                context.contentResolver.query(uri, projection, sel.toString(), null, sort)?.use { c ->
                    val idIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns._ID)
                    val nameIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.MIME_TYPE)
                    val bucketIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val modIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (c.moveToNext() && list.size < 5000) {
                        val name = c.getString(nameIdx)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        val id = c.getLong(idIdx)
                        val mime = c.getString(mimeIdx).orEmpty()
                        val bucket = c.getString(bucketIdx)?.trim().orEmpty()
                        val contentUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                        list.add(QuickSearchResult.ImageVideo(name, bucket, contentUri, mime, c.getLong(modIdx)))
                    }
                }
            }
            mediaFiles = list.distinctBy { it.uri }
            for (f in mediaFiles) AppSearch.buildIndex(f.name)
        }
        if (hasAllFilesAccess() && !docsTried) {
            docsTried = true
            val list = mutableListOf<QuickSearchResult.DocFile>()
            runCatching {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    android.provider.MediaStore.Files.FileColumns._ID,
                    android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                    android.provider.MediaStore.Files.FileColumns.MIME_TYPE,
                    android.provider.MediaStore.Files.FileColumns.SIZE,
                    android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
                )
                val mt = android.provider.MediaStore.Files.FileColumns.MIME_TYPE
                val sel = "($mt NOT LIKE 'image/%' AND $mt NOT LIKE 'video/%' AND $mt NOT LIKE 'audio/%')"
                val sort = "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                context.contentResolver.query(uri, projection, sel, null, sort)?.use { c ->
                    val idIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns._ID)
                    val nameIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.MIME_TYPE)
                    val sizeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.SIZE)
                    val modIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (c.moveToNext() && list.size < 5000) {
                        val name = c.getString(nameIdx)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        val id = c.getLong(idIdx)
                        val mime = c.getString(mimeIdx).orEmpty()
                        val sizeText = formatSize(c.getLong(sizeIdx))
                        val contentUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                        list.add(QuickSearchResult.DocFile(name, sizeText, contentUri, mime, c.getLong(modIdx)))
                    }
                }
            }
            docs = list.distinctBy { it.uri }
            for (d in docs) AppSearch.buildIndex(d.name)
        }
    }

    /** 媒体是否已就绪（任一源试过）：search 只查内存，权限实时复查。 */
    fun mediaReady(): Boolean = audioTried || galleryTried || docsTried

    // ---- 日历/短信/通话：与联系人同一策略（IO 一次加载，打字只查内存） ----

    private var events: List<QuickSearchResult.CalendarEvent> = emptyList()
    private var smsList: List<QuickSearchResult.SmsMsg> = emptyList()
    private var callList: List<QuickSearchResult.CallLogEntry> = emptyList()
    @Volatile private var calendarTried = false
    @Volatile private var smsTried = false
    @Volatile private var callsTried = false
    /** 手电状态是 Activity 翻的（CameraManager 无 getter），存在 index 供开关行副标题用。 */
    @Volatile var torchOn: Boolean = false

    fun calendarReady(): Boolean = calendarTried && hasMediaPermission(android.Manifest.permission.READ_CALENDAR)
    fun smsReady(): Boolean = smsTried && hasMediaPermission(android.Manifest.permission.READ_SMS)
    fun callsReady(): Boolean = callsTried && hasMediaPermission(android.Manifest.permission.READ_CALL_LOG)

    /** 日历前后各 30 天：必须在后台线程调。 */
    fun ensureCalendar() {
        if (calendarTried) return
        calendarTried = true
        if (!hasMediaPermission(android.Manifest.permission.READ_CALENDAR)) return
        val list = mutableListOf<QuickSearchResult.CalendarEvent>()
        runCatching {
            val now = System.currentTimeMillis()
            val day = 86400000L
            val builder = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, now - 30 * day)
            android.content.ContentUris.appendId(builder, now + 30 * day)
            val proj = arrayOf(
                android.provider.CalendarContract.Instances.TITLE,
                android.provider.CalendarContract.Instances.EVENT_LOCATION,
                android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.EVENT_ID,
            )
            context.contentResolver.query(
                builder.build(), proj, null, null,
                "${android.provider.CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                val tIdx = c.getColumnIndex(android.provider.CalendarContract.Instances.TITLE)
                val lIdx = c.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_LOCATION)
                val bIdx = c.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                val eIdx = c.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_ID)
                while (c.moveToNext() && list.size < 500) {
                    val title = c.getString(tIdx)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    val begin = c.getLong(bIdx)
                    list.add(
                        QuickSearchResult.CalendarEvent(
                            title,
                            formatTime(begin),
                            c.getString(lIdx)?.trim().orEmpty(),
                            c.getLong(eIdx),
                            begin,
                        ),
                    )
                }
            }
        }
        events = list.distinctBy { it.eventId to it.timeText }
        for (e in events) {
            AppSearch.buildIndex(e.title_)
            AppSearch.buildIndex(e.location)
        }
    }

    /** 收件箱 500 条：必须在后台线程调。 */
    fun ensureSms() {
        if (smsTried) return
        smsTried = true
        if (!hasMediaPermission(android.Manifest.permission.READ_SMS)) return
        val list = mutableListOf<QuickSearchResult.SmsMsg>()
        runCatching {
            val uri = android.provider.Telephony.Sms.Inbox.CONTENT_URI
            val proj = arrayOf(
                android.provider.Telephony.Sms.ADDRESS,
                android.provider.Telephony.Sms.BODY,
                android.provider.Telephony.Sms.DATE,
            )
            context.contentResolver.query(
                uri, proj, null, null, "${android.provider.Telephony.Sms.DATE} DESC",
            )?.use { c ->
                val aIdx = c.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                val bIdx = c.getColumnIndex(android.provider.Telephony.Sms.BODY)
                val dIdx = c.getColumnIndex(android.provider.Telephony.Sms.DATE)
                while (c.moveToNext() && list.size < 500) {
                    val body = c.getString(bIdx)?.trim().orEmpty()
                    if (body.isEmpty()) continue
                    list.add(
                        QuickSearchResult.SmsMsg(
                            c.getString(aIdx)?.trim().orEmpty(),
                            body,
                            formatTime(c.getLong(dIdx)),
                        ),
                    )
                }
            }
        }
        smsList = list
        for (m in smsList) {
            AppSearch.buildIndex(m.address)
            AppSearch.buildIndex(m.body)
        }
    }

    /** 通话记录 200 条：必须在后台线程调。 */
    fun ensureCalls() {
        if (callsTried) return
        callsTried = true
        if (!hasMediaPermission(android.Manifest.permission.READ_CALL_LOG)) return
        val list = mutableListOf<QuickSearchResult.CallLogEntry>()
        runCatching {
            val uri = android.provider.CallLog.Calls.CONTENT_URI
            val proj = arrayOf(
                android.provider.CallLog.Calls.CACHED_NAME,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.TYPE,
                android.provider.CallLog.Calls.DATE,
            )
            context.contentResolver.query(
                uri, proj, null, null, "${android.provider.CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val nIdx = c.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                val numIdx = c.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                val tIdx = c.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                val dIdx = c.getColumnIndex(android.provider.CallLog.Calls.DATE)
                while (c.moveToNext() && list.size < 200) {
                    val number = c.getString(numIdx)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val typeText = when (c.getInt(tIdx)) {
                        android.provider.CallLog.Calls.INCOMING_TYPE -> "呼入"
                        android.provider.CallLog.Calls.OUTGOING_TYPE -> "呼出"
                        android.provider.CallLog.Calls.MISSED_TYPE -> "未接"
                        android.provider.CallLog.Calls.REJECTED_TYPE -> "拒接"
                        else -> ""
                    }
                    list.add(
                        QuickSearchResult.CallLogEntry(
                            c.getString(nIdx)?.trim().orEmpty(),
                            number,
                            typeText,
                            formatTime(c.getLong(dIdx)),
                        ),
                    )
                }
            }
        }
        callList = list
        for (c in callList) {
            AppSearch.buildIndex(c.name)
            AppSearch.buildIndex(c.number)
        }
    }

    private fun formatTime(millis: Long): String = runCatching {
        java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }.getOrDefault("")

    /**
     * 最近文件（空态段）：图库 + 文档按修改时间倒序取前 N。
     * 权限实时复查：关掉权限后 stale 缓存不再参展；调用方只在 searchFiles 开时调。
     */
    fun recentFiles(limit: Int = 10): List<QuickSearchResult> {
        val out = mutableListOf<Pair<Long, QuickSearchResult>>()
        val galleryOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ||
            hasMediaPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
        if (galleryOk) for (f in mediaFiles) out.add(f.modified to f)
        if (hasAllFilesAccess()) for (d in docs) out.add(d.modified to d)
        return out.sortedByDescending { it.first }.take(limit).map { it.second }
    }

    /**
     * 快捷开关行：读状态全是便宜 binder/IPC（异常直接 null），每次 search 现算，
     * 副标题诚实显示开/关/去设置。关键词跟标题一起匹配。
     */
    fun toggleRows(): List<QuickSearchResult.PhoneToggle> {
        val on = context.getString(R.string.quick_search_state_on)
        val off = context.getString(R.string.quick_search_state_off)
        val rows = mutableListOf<QuickSearchResult.PhoneToggle>()
        val wifi = runCatching {
            val wm = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            if (wm?.isWifiEnabled == true) on else off
        }.getOrNull().orEmpty()
        rows.add(QuickSearchResult.PhoneToggle(QuickSearchResult.ToggleKind.WIFI, "Wi-Fi", "wifi 无线 局域网 wlan", wifi))
        val bt = runCatching {
            val bm = context.applicationContext.getSystemService(android.bluetooth.BluetoothManager::class.java)
            if (bm?.adapter?.isEnabled == true) on else off
        }.getOrNull().orEmpty()
        rows.add(QuickSearchResult.PhoneToggle(QuickSearchResult.ToggleKind.BLUETOOTH, "蓝牙", "蓝牙 bluetooth", bt))
        rows.add(
            QuickSearchResult.PhoneToggle(
                QuickSearchResult.ToggleKind.TORCH, "手电筒", "手电 闪光灯 torch", if (torchOn) on else off,
            ),
        )
        val dnd = runCatching {
            val nm = context.applicationContext.getSystemService(android.app.NotificationManager::class.java)
            if (nm?.isNotificationPolicyAccessGranted != true) {
                context.getString(R.string.quick_search_open_settings)
            } else if (nm.currentInterruptionFilter ==
                android.app.NotificationManager.INTERRUPTION_FILTER_NONE
            ) {
                on
            } else {
                off
            }
        }.getOrNull().orEmpty()
        rows.add(QuickSearchResult.PhoneToggle(QuickSearchResult.ToggleKind.DND, "勿扰", "勿扰 免打扰 dnd", dnd))
        val loc = runCatching {
            val lm = context.applicationContext.getSystemService(android.location.LocationManager::class.java)
            if (lm != null && (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER))
            ) {
                on
            } else {
                off
            }
        }.getOrNull().orEmpty()
        rows.add(QuickSearchResult.PhoneToggle(QuickSearchResult.ToggleKind.LOCATION, "定位", "定位 位置 gps", loc))
        rows.add(
            QuickSearchResult.PhoneToggle(
                QuickSearchResult.ToggleKind.HOTSPOT,
                "热点",
                "热点 hotspot 共享 网络",
                context.getString(R.string.quick_search_open_settings),
            ),
        )
        return rows
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        if (kb < 1024) return "${if (kb < 10) String.format("%.1f", kb) else kb.toLong()} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${if (mb < 10) String.format("%.1f", mb) else mb.toLong()} MB"
        return "${String.format("%.1f", mb / 1024.0)} GB"
    }

    /** 设置页 label 与动作选择页同源：从 system.openSettings 的 Enum options 动态取。 */
    private fun systemSettingsPages(): List<QuickSearchResult.Settings> {
        val def = ActionDefinitions.byTypeId("system.openSettings") ?: return emptyList()
        val enum = def.parameters.filterIsInstance<ParameterDefinition.Enum>()
            .firstOrNull { it.key == "page" } ?: return emptyList()
        return enum.options.mapNotNull { opt ->
            runCatching { SettingsPage.valueOf(opt.value) }.getOrNull()?.let {
                QuickSearchResult.Settings(it, opt.label)
            }
        }
    }

    /** 把 query 拆成单字 token（只保留字母数字），复用 AppSearch 拼音引擎。 */
    private fun tokenize(s: String): List<String> =
        s.lowercase().filter { it.isLetterOrDigit() }.map { it.toString() }

    private fun tierOf(text: String, tokens: List<String>, foldedTokens: List<String>): TieredMatch =
        AppSearch.matchTextTier(text, tokens, foldedTokens)

    /** 取最优命中（tier 大者胜，再比 offset 小者胜；连续块 span 恒定不参比）。 */
    private fun bestOf(a: TieredMatch, b: TieredMatch): TieredMatch =
        maxOf(a, b, compareBy({ it.tier }, { -it.offset }))

    /** 空输入时的常用：按衰减使用分取前 N 个（禁用应用一并返回，由调用方标注）。 */
    fun topUsed(settings: QuickAppLauncherSettings, limit: Int = 8): List<QuickSearchResult.App> {
        ensureLoaded()
        if (!appsLoaded) refreshApps()
        val now = System.currentTimeMillis()
        return apps
            .map { app -> app to usageScore(app.key(), settings, now) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (app, _) -> QuickSearchResult.App(app, app.packageName in disabledPkgs) }
    }

    /** 与 AppSearch.sortApps 同款的使用频率分（对数压制 + 3 天半衰），key 与记录侧一致。 */
    private fun usageScore(key: String, settings: QuickAppLauncherSettings, now: Long): Double {
        val count = settings.launchCount[key] ?: 0L
        val recent = settings.recentLaunchTime[key] ?: 0L
        if (count <= 0L || recent <= 0L) return 0.0
        return AppSearch.decayedUsage(count, (now - recent) / 86400000.0)
    }

    fun search(
        query: String,
        libraryEntries: List<NewActionLibraryEntry>,
        launcherSettings: QuickAppLauncherSettings = QuickAppLauncherSettings(),
        searchSettings: QuickSearchSettings = QuickSearchSettings(),
        torchOn: Boolean = false,
    ): List<QuickSearchResult> {
        ensureLoaded()
        if (!appsLoaded) refreshApps()
        this.torchOn = torchOn
        // 标识指令优先（bb 新闻 / sms 138 / 裸 sms）：命中只回目标类，
        // 纯目标结果（无引擎兜底、无计算器、无历史参展）。null = 无指令，走全源。
        parseDirective(query.trim(), libraryEntries, launcherSettings, searchSettings)?.let { return it }
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        // 模糊音输入：分词前先整体折叠（adjacency 丢了就没法折），降档用。
        val foldedTokens = tokenize(AppSearch.foldPinyin(query))
        val now = System.currentTimeMillis()
        // 快捷方式按所属包聚合该包的最大分，一次算好供 O(1) 查询。
        val pkgScore = pkgScores(launcherSettings, now)

        val results = mutableListOf<RankedHit>()
        results.addAll(matchActions(tokens, foldedTokens))
        results.addAll(matchLibrary(libraryEntries, tokens, foldedTokens))
        results.addAll(matchApps(tokens, foldedTokens, launcherSettings, now))
        results.addAll(matchShortcuts(tokens, foldedTokens, pkgScore))
        // 联系人：姓名拼音免费（matchTextTier 内建），号码数字直匹配；只查内存。
        results.addAll(matchContacts(tokens, foldedTokens, searchSettings))
        // P3：音乐搜标题/艺术家，文件搜文件名；只查内存，无 usage；权限每次实时复查
        // （设置里关掉权限后 stale 缓存不再参展）。
        results.addAll(matchMedia(tokens, foldedTokens, searchSettings))
        // 快捷开关：标题 + 关键词一起匹配（如 wifi/蓝牙/手电），设置页同权重。
        results.addAll(matchToggles(tokens, foldedTokens, searchSettings))
        // 日历：标题/地点；只查内存。
        results.addAll(matchCalendar(tokens, foldedTokens, searchSettings))
        // 短信：发件人/正文；只查内存。
        results.addAll(matchSms(tokens, foldedTokens, searchSettings))
        // 通话记录：姓名/号码；只查内存。
        results.addAll(matchCalls(tokens, foldedTokens, searchSettings))
        results.addAll(matchSettingsPages(tokens, foldedTokens))

        val urlResult = urlResult(query)
        if (urlResult != null) results.add(RankedHit(urlResult, 3, 0, 0, 0.0))

        // 搜索引擎兜底：tier -1 进同一管线自然垫底（不再追加特例）。
        // 只有非 URL 关键词才有；标识直达优先选引擎（如 bb 新闻），否则默认引擎。
        if (urlResult == null) {
            results.add(engineTriple(query.trim(), searchSettings))
        }

        val sorted = sortTriples(results)

        // 计算器排表头（精确意图，回车直达）。
        // 引擎兜底已由 engineTriple 进管线自然垫底，这里不再二次计算（之前重复算过一次）。
        val calc = calculatorResult(query.trim())
        return if (calc != null) listOf<QuickSearchResult>(calc) + sorted else sorted
    }

    /** 排序单元：结果 + 档位 + 紧密度 + 使用分。 */
    private data class RankedHit(
        val result: QuickSearchResult,
        val tier: Int,
        val span: Int,
        val offset: Int,
        val usage: Double,
    )

    /** 同一套排序：连续块语义下 tier → offset → 使用分 → 长度 → 字典序。
     * 类型不再分档（typerank 已删）：不同类型只比匹配度；稀疏子序列不出局外无命中。 */
    private fun sortTriples(results: List<RankedHit>): List<QuickSearchResult> =
        results.sortedWith(
            compareByDescending<RankedHit> { it.tier }
                .thenBy { it.offset }
                .thenByDescending { it.usage }
                .thenBy { it.result.title.length }
                .thenBy { it.result.title },
        ).map { it.result }

    private fun matchActions(
        tokens: List<String>,
        foldedTokens: List<String>,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (a in actions) {
            val m = tierOf(a.title, tokens, foldedTokens)
            if (m.tier >= 0) out.add(RankedHit(a, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    private fun matchLibrary(
        libraryEntries: List<NewActionLibraryEntry>,
        tokens: List<String>,
        foldedTokens: List<String>,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (entry in libraryEntries) {
            val def = ActionDefinitions.byTypeId(entry.typeId)
            val fallback = def?.name ?: entry.typeId
            val m = bestOf(tierOf(entry.name, tokens, foldedTokens), tierOf(fallback, tokens, foldedTokens))
            if (m.tier >= 0) {
                out.add(
                    RankedHit(
                        QuickSearchResult.LibraryEntry(entry, fallback, entry.resolvedIcon()),
                        m.tier,
                        m.span,
                        m.offset,
                        0.0,
                    ),
                )
            }
        }
        return out
    }

    private fun matchApps(
        tokens: List<String>,
        foldedTokens: List<String>,
        launcherSettings: QuickAppLauncherSettings,
        now: Long,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (app in apps) {
            // 包名也参与匹配：行副标题本来就显示包名，搜 com.tencent 应该有结果。
            val m = bestOf(tierOf(app.label, tokens, foldedTokens), tierOf(app.packageName, tokens, foldedTokens))
            if (m.tier >= 0) {
                out.add(
                    RankedHit(
                        QuickSearchResult.App(app, app.packageName in disabledPkgs),
                        m.tier,
                        m.span,
                        m.offset,
                        usageScore(app.key(), launcherSettings, now),
                    ),
                )
            }
        }
        return out
    }

    private fun matchShortcuts(
        tokens: List<String>,
        foldedTokens: List<String>,
        pkgScore: Map<String, Double>,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (sc in shortcuts) {
            val m = tierOf(sc.label, tokens, foldedTokens)
            if (m.tier >= 0) {
                out.add(RankedHit(QuickSearchResult.Shortcut(sc), m.tier, m.span, m.offset, pkgScore[sc.packageName] ?: 0.0))
            }
        }
        return out
    }

    private fun matchContacts(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchContacts || !contactsReady()) return out
        for (c in contacts) {
            val m = bestOf(tierOf(c.name, tokens, foldedTokens), tierOf(c.number, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(c, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    private fun matchMedia(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        if (!mediaReady()) return emptyList()
        return matchTracks(tokens, foldedTokens, searchSettings) +
            matchGallery(tokens, foldedTokens, searchSettings) +
            matchDocs(tokens, foldedTokens, searchSettings)
    }

    private fun matchTracks(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchMusic ||
            !hasMediaPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
        ) {
            return out
        }
        for (t in tracks) {
            val m = bestOf(tierOf(t.title, tokens, foldedTokens), tierOf(t.artist, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(t, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    private fun matchGallery(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchFiles) return out
        val galleryOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ||
            hasMediaPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
        if (!galleryOk) return out
        for (f in mediaFiles) {
            val m = tierOf(f.name, tokens, foldedTokens)
            if (m.tier >= 0) out.add(RankedHit(f, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    private fun matchDocs(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchFiles || !hasAllFilesAccess()) return out
        for (d in docs) {
            val m = tierOf(d.name, tokens, foldedTokens)
            if (m.tier >= 0) out.add(RankedHit(d, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    private fun matchToggles(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchToggles) return out
        for (t in toggleRows()) {
            val m = bestOf(tierOf(t.title, tokens, foldedTokens), tierOf(t.keywords, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(t, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    private fun matchCalendar(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchCalendar || !calendarReady()) return out
        for (e in events) {
            val m = bestOf(tierOf(e.title_, tokens, foldedTokens), tierOf(e.location, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(e, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    private fun matchSms(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchSms || !smsReady()) return out
        for (m in smsList) {
            val t = bestOf(tierOf(m.address, tokens, foldedTokens), tierOf(m.body, tokens, foldedTokens))
            if (t.tier >= 0) out.add(RankedHit(m, t.tier, t.span, t.offset, 0.0))
        }
        return out
    }

    private fun matchCalls(
        tokens: List<String>,
        foldedTokens: List<String>,
        searchSettings: QuickSearchSettings,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        if (!searchSettings.searchCalls || !callsReady()) return out
        for (c in callList) {
            val m = bestOf(tierOf(c.name, tokens, foldedTokens), tierOf(c.number, tokens, foldedTokens))
            if (m.tier >= 0) out.add(RankedHit(c, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    private fun matchSettingsPages(
        tokens: List<String>,
        foldedTokens: List<String>,
    ): List<RankedHit> {
        val out = mutableListOf<RankedHit>()
        for (sp in settingsPages) {
            val m = tierOf(sp.title, tokens, foldedTokens)
            if (m.tier >= 0) out.add(RankedHit(sp, m.tier, m.span, m.offset, 0.0))
        }
        return out
    }

    /** 面板用：该 query 是否命中标识指令（命中则历史匹配不参展，保持纯目标结果）。 */
    fun isDirective(
        query: String,
        settings: QuickSearchSettings,
        launcherSettings: QuickAppLauncherSettings = QuickAppLauncherSettings(),
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val hasSpace = trimmed.contains(' ')
        val head = (if (hasSpace) trimmed.substringBefore(' ') else trimmed).lowercase()
        if (hasSpace && trimmed.substringAfter(' ').isNotBlank()) {
            if (directedEngine(trimmed, settings) != null) return true
        }
        val source = sourceKeyFor(head, settings) ?: return false
        if (!sourceEnabled(source, settings)) return false
        // 裸标识无数据会回落全源（parseDirective 同逻辑），此时历史照常参展，前后一致。
        if (!hasSpace || trimmed.substringAfter(' ').isBlank()) {
            return bareSource(source, launcherSettings, settings) != null
        }
        return true
    }

    /**
     * 标识指令解析（bb 新闻 / sms 138 / 裸 sms）：命中返回纯目标结果，
     * 无指令返回 null 走全源。大小写不敏感；标识未知、无内容、有名无实（裸）按规则办。
     *
     * 三种出口：引擎标识 → 只回引擎行；来源标识+内容 → 只在该源内搜；
     * 裸标识（无空格或内容空）→ 列出该类近项。开关关闭/无数据 → null 回落全源。
     */
    private fun parseDirective(
        trimmed: String,
        libraryEntries: List<NewActionLibraryEntry>,
        launcherSettings: QuickAppLauncherSettings,
        searchSettings: QuickSearchSettings,
    ): List<QuickSearchResult>? {
        if (trimmed.isEmpty()) return null
        val hasSpace = trimmed.contains(' ')
        val head = if (hasSpace) trimmed.substringBefore(' ') else trimmed
        val rest = if (hasSpace) trimmed.substringAfter(' ').trim() else ""
        val headLower = head.lowercase()
        // 引擎标识 + 内容：只回引擎行（纯目标结果，不跑全源）。
        if (hasSpace && rest.isNotEmpty()) {
            directedEngine(trimmed, searchSettings)?.let { return listOf(it) }
        }
        // 来源标识：开关关了、标识被清空（关闭）都回落全源。
        val source = sourceKeyFor(headLower, searchSettings) ?: return null
        if (!sourceEnabled(source, searchSettings)) return null
        if (rest.isEmpty()) return bareSource(source, launcherSettings, searchSettings)
        return searchInSource(source, rest, libraryEntries, launcherSettings, searchSettings)
    }

    /** 标识头 → 来源 key：自定义优先、默认补齐，大小写不敏感，空串=关闭跳过。 */
    private fun sourceKeyFor(headLower: String, settings: QuickSearchSettings): String? {
        if (headLower.isEmpty()) return null
        for ((k, v) in DefaultSourceTriggers) {
            val t = settings.effectiveTrigger(k).trim().lowercase()
            if (t.isNotEmpty() && t == headLower) return k
        }
        return null
    }

    /** 来源总开关：应用/快捷方式/动作/设置页无开关，常开。 */
    private fun sourceEnabled(source: String, settings: QuickSearchSettings): Boolean = when (source) {
        SRC_CONTACT -> settings.searchContacts
        SRC_MUSIC -> settings.searchMusic
        SRC_FILE -> settings.searchFiles
        SRC_CALENDAR -> settings.searchCalendar
        SRC_SMS -> settings.searchSms
        SRC_CALL -> settings.searchCalls
        SRC_TOGGLE -> settings.searchToggles
        SRC_APP, SRC_SHORTCUT, SRC_ACTION, SRC_SETTINGS -> true
        else -> false
    }

    /** 定向搜：只在该源内匹配内容，排序与全源同一套。 */
    private fun searchInSource(
        source: String,
        rest: String,
        libraryEntries: List<NewActionLibraryEntry>,
        launcherSettings: QuickAppLauncherSettings,
        searchSettings: QuickSearchSettings,
    ): List<QuickSearchResult> {
        val tokens = tokenize(rest)
        if (tokens.isEmpty()) return bareSource(source, launcherSettings, searchSettings) ?: emptyList()
        val folded = tokenize(AppSearch.foldPinyin(rest))
        val now = System.currentTimeMillis()
        val triples = when (source) {
            SRC_APP -> matchApps(tokens, folded, launcherSettings, now)
            SRC_SHORTCUT -> matchShortcuts(tokens, folded, pkgScores(launcherSettings, now))
            SRC_ACTION -> matchActions(tokens, folded)
            SRC_SETTINGS -> matchSettingsPages(tokens, folded)
            SRC_CONTACT -> matchContacts(tokens, folded, searchSettings)
            SRC_MUSIC -> matchTracks(tokens, folded, searchSettings)
            SRC_FILE -> matchGallery(tokens, folded, searchSettings) +
                matchDocs(tokens, folded, searchSettings)
            SRC_CALENDAR -> matchCalendar(tokens, folded, searchSettings)
            SRC_SMS -> matchSms(tokens, folded, searchSettings)
            SRC_CALL -> matchCalls(tokens, folded, searchSettings)
            SRC_TOGGLE -> matchToggles(tokens, folded, searchSettings)
            else -> emptyList()
        }
        // 开关关了但标识还在（极少）：匹配函数内已 gate，空结果直接回（不回落全源，
        // 用户明确点了类，结果为空就是空）。
        return sortTriples(triples)
    }

    /** 裸标识：列出该类近项（cap 20，应用走常用 8）。无数据回 null → 全源。 */
    private fun bareSource(
        source: String,
        launcherSettings: QuickAppLauncherSettings,
        searchSettings: QuickSearchSettings,
    ): List<QuickSearchResult>? {
        val out: List<QuickSearchResult> = when (source) {
            SRC_APP -> topUsed(launcherSettings)
            SRC_SHORTCUT -> shortcuts.map { QuickSearchResult.Shortcut(it) }.take(BARE_LIST_LIMIT)
            SRC_ACTION -> actions.take(BARE_LIST_LIMIT)
            SRC_SETTINGS -> settingsPages.take(BARE_LIST_LIMIT)
            SRC_CONTACT -> if (contactsReady()) contacts.take(BARE_LIST_LIMIT) else return null
            SRC_MUSIC -> if (hasMediaPermission(android.Manifest.permission.READ_MEDIA_AUDIO)) {
                tracks.take(BARE_LIST_LIMIT)
            } else {
                return null
            }
            SRC_FILE -> recentFiles(BARE_LIST_LIMIT).takeIf { it.isNotEmpty() } ?: return null
            SRC_CALENDAR -> {
                if (!calendarReady() || events.isEmpty()) return null
                val now = System.currentTimeMillis()
                val (upcoming, past) = events.partition { it.beginMillis >= now - 86400000L }
                (upcoming + past).take(BARE_LIST_LIMIT)
            }
            SRC_SMS -> if (smsReady() && smsList.isNotEmpty()) smsList.take(BARE_LIST_LIMIT) else return null
            SRC_CALL -> if (callsReady() && callList.isNotEmpty()) callList.take(BARE_LIST_LIMIT) else return null
            SRC_TOGGLE -> toggleRows()
            else -> return null
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** 快捷方式包分聚合：全源与定向共用，抽出来。 */
    private fun pkgScores(
        launcherSettings: QuickAppLauncherSettings,
        now: Long,
    ): Map<String, Double> {
        val pkgScore = mutableMapOf<String, Double>()
        for ((key, count) in launcherSettings.launchCount) {
            val pkg = key.substringBefore('/')
            val recent = launcherSettings.recentLaunchTime[key] ?: 0L
            if (count <= 0L || recent <= 0L) continue
            val score = AppSearch.decayedUsage(count, (now - recent) / 86400000.0)
            if (score > (pkgScore[pkg] ?: 0.0)) pkgScore[pkg] = score
        }
        return pkgScore
    }

    /**
     * 搜索引擎 Triple：tier -1 保证在同一排序管线里垫底。
     * 标识直达优先选引擎，否则默认引擎（选择优先，位置不特殊）。
     */
    private fun engineTriple(
        query: String,
        settings: QuickSearchSettings,
    ): RankedHit {
        val directed = directedEngine(query, settings)
        if (directed != null) return RankedHit(directed, -1, 0, 0, 0.0)
        val default = settings.defaultEngine()
        return RankedHit(
            QuickSearchResult.EngineSearch(
                query,
                context.getString(R.string.quick_search_engine_search, default.name),
                default.urlTemplate,
                default.name,
            ),
            -1,
            0,
            0,
            0.0,
        )
    }

    /**
     * 标识直达：“标识 + 空格 + 内容”（如 bing 新闻）用标识对应的引擎。
     * 大小写不敏感；无空格分隔、标识未知、有名无实（内容空）都返回 null 走默认兜底。
     */
    private fun directedEngine(query: String, settings: QuickSearchSettings): QuickSearchResult.EngineSearch? {
        val rest = query.substringAfter(' ', "")
        if (rest.isBlank()) return null
        val head = query.substringBefore(' ')
        val target = settings.effectiveEngines()
            .firstOrNull { it.trigger.isNotBlank() && it.trigger.equals(head, ignoreCase = true) }
            ?: return null
        return QuickSearchResult.EngineSearch(
            rest.trim(),
            context.getString(R.string.quick_search_engine_search, target.name),
            target.urlTemplate,
            target.name,
        )
    }

    /**
     * 计算器：纯算式（数字加减乘除取余括号空格）即算即显。要求至少一个数字和一个
     * 运算符，裸数字不触发；解析失败、除零无穷都返回 null（无行）。
     */
    private val calcChars = Regex("^[0-9+\\-*/().%\\s]+$")

    private fun calculatorResult(query: String): QuickSearchResult.Calc? {
        if (query.length < 3 || !calcChars.matches(query)) return null
        if (!query.any { it.isDigit() } || !query.any { it in "+-*/%" }) return null
        val value = runCatching { evalExpr(query) }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        val text = if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        return QuickSearchResult.Calc(query, text)
    }

    /** 递归下降四则运算：expr → term → factor（成员函数互递归，用小类装位置状态）。 */
    private fun evalExpr(s: String): Double = ExprParser(s.filterNot { it.isWhitespace() }).parse()

    private class ExprParser(val t: String) {
        var pos = 0

        fun parse(): Double {
            val v = parseExpr()
            require(pos == t.length)
            return v
        }

        fun parseExpr(): Double {
            var v = parseTerm()
            while (pos < t.length) {
                val c = t[pos]
                if (c != '+' && c != '-') break
                pos++
                val r = parseTerm()
                v = if (c == '+') v + r else v - r
            }
            return v
        }

        fun parseTerm(): Double {
            var v = parseFactor()
            while (pos < t.length) {
                val c = t[pos]
                if (c != '*' && c != '/' && c != '%') break
                pos++
                val r = parseFactor()
                v = when (c) {
                    '*' -> v * r
                    '/' -> v / r
                    else -> v % r
                }
            }
            return v
        }

        fun parseFactor(): Double {
            if (pos < t.length && (t[pos] == '+' || t[pos] == '-')) {
                val neg = t[pos] == '-'
                pos++
                val v = parseFactor()
                return if (neg) -v else v
            }
            if (pos < t.length && t[pos] == '(') {
                pos++
                val v = parseExpr()
                require(pos < t.length && t[pos] == ')')
                pos++
                return v
            }
            val start = pos
            while (pos < t.length && (t[pos].isDigit() || t[pos] == '.')) pos++
            require(start < pos)
            return t.substring(start, pos).toDouble()
        }
    }

    private fun urlResult(query: String): QuickSearchResult.Url? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val hasScheme = trimmed.contains("://")
        val looksLikeDomain = trimmed.contains('.') && !trimmed.contains(' ')
            && trimmed.matches(Regex("^[^\\s]+\\.[^\\s]{2,}.*$"))
        return if (hasScheme || looksLikeDomain) QuickSearchResult.Url(trimmed) else null
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
