package hunoia.luno.quickaccess.query

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.core.domain.ActionSearchEntry
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.TextIcon
import hunoia.luno.search.R
import hunoia.luno.config.model.SRC_ACTION
import hunoia.luno.config.model.SRC_APP
import hunoia.luno.config.model.SRC_CALENDAR
import hunoia.luno.config.model.SRC_CALL
import hunoia.luno.config.model.SRC_CLIP
import hunoia.luno.config.model.SRC_CONTACT
import hunoia.luno.config.model.SRC_FILE
import hunoia.luno.config.model.SRC_MUSIC
import hunoia.luno.config.model.SRC_SHORTCUT
import hunoia.luno.config.model.SRC_SMS

/**
 * 各搜索来源的行类型与到展示模型 [SearchResult] 的唯一投影。
 *
 * 行类型只承载召回与打分需要的原始字段（联系人姓名/号码、短信正文/地址等——
 * [SearchResult] 只有 title/subtitle，直接拿它打分会改匹配语义）；投影函数把
 * key / 图标 / 动作一次性定死，不再有「原始模型 → 展示模型」的二级 when 桥。
 * 新来源直接构造 [SearchResult]，不必经过这里。
 *
 * key 的派生与旧 `stableId()` 逐字节一致：Pin 键落在用户配置里，改派生等于清空置顶。
 */

// ---------- ContentProvider 源的行类型（打分字段在此） ----------

internal data class ContactRow(val name: String, val number: String)

internal data class AudioTrackRow(val title: String, val artist: String, val uri: String)

internal data class MediaFileRow(
    val name: String,
    val bucket: String,
    val uri: String,
    val mime: String,
    val modified: Long = 0,
)

internal data class DocFileRow(
    val name: String,
    val sizeText: String,
    val uri: String,
    val mime: String,
    val modified: Long = 0,
)

internal data class CalendarRow(
    val title: String,
    val timeText: String,
    val location: String,
    val eventId: Long,
    val beginMillis: Long = 0,
)

internal data class SmsRow(val address: String, val body: String, val dateText: String)

internal data class CallRow(
    val name: String,
    val number: String,
    val typeText: String,
    val dateText: String,
)

// ---------- 投影 ----------

/** 动作条目行（索引层 `ensureLoaded` 的 actions）。 */
internal fun ActionSearchEntry.toResult(): SearchResult = SearchResult(
    key = "a#${action.typeId}|${action.params}",
    source = SRC_ACTION,
    title = label,
    subtitle = "",
    icon = iconOf(icon),
    primary = SearchAction.Run(action),
)

/** 动作库条目行：标题 = 条目名（空则类型名兜底），副标题恒为类型名。 */
internal fun libraryResult(
    entry: ActionLibraryEntry,
    fallback: String,
    icon: Any?,
): SearchResult = SearchResult(
    key = "l#${entry.id}",
    source = SRC_ACTION,
    title = entry.name.ifBlank { fallback },
    subtitle = fallback,
    icon = iconOf(icon),
    primary = SearchAction.Run(StoredAction.of("internal.libraryRef", "entryId" to entry.id)),
)

/** 应用行：长按恒进应用详情（停用应用也保留该入口）。 */
internal fun AppInfo.toResult(disabled: Boolean): SearchResult {
    // 分身详情必须带上 userId：系统设置页按 package: 只认主用户实例，
    // 不带 userId 长按分身会打开主应用详情（分身与主应用包名相同）。
    val details = StoredAction.of(
        "app.details",
        "packageName" to packageName,
        "userId" to userId.takeIf { it != 0 }?.toString(),
    )
    return SearchResult(
        key = "app#$packageName/$className@$userId",
        source = SRC_APP,
        title = label,
        subtitle = packageName,
        icon = SearchIcon.AppIcon(packageName, userId = userId),
        primary = SearchAction.Run(
            hunoia.luno.core.domain.AppLaunchAction.stored(
                packageName = packageName,
                className = className,
                miniWindow = miniWindow,
                userId = userId,
            ),
        ),
        actions = listOf(
            SearchActionItem(
                SearchActionLabel.Res(R.string.search_action_app_details),
                SearchAction.Run(details),
            ),
        ),
        badge = if (disabled) SearchBadge.DisabledApp else null,
    )
}

/** 快捷方式行：自带图标（含动态导入的 PNG）；没有文件才回退应用图标。 */
internal fun LauncherInfo.ShortcutInfo.toResult(): SearchResult {
    val base = "sc#$packageName/$className|${intents.joinToString("\u0001")}"
    // 静态快捷方式没有独立 id（XML 解析时 currentId 就丢了），intents 是唯一能区分
    // 同包同 activity 多个快捷方式的字段。动态入口（userSerial >= 0）className/intents
    // 都为空，同包主/分身会撞，必须补 shortcutId + userSerial 才能区分。
    val key = if (userSerial >= 0) "$base#${shortcutId.orEmpty()}@$userSerial" else base
    return SearchResult(
        key = key,
        source = SRC_SHORTCUT,
        title = label,
        subtitle = packageName,
        icon = iconPath?.let { SearchIcon.Request(it) }
            ?: SearchIcon.AppIcon(packageName, iconBitmap?.asImageBitmap()),
        primary = SearchAction.Run(
            StoredAction.of(
                "app.launchShortcut",
                "data" to hunoia.luno.core.domain.JsonSerializer.encodeToString(this),
            ),
        ),
        actions = listOf(
            SearchActionItem(
                SearchActionLabel.Res(R.string.search_action_app_details),
                SearchAction.Run(
                    StoredAction.of("app.details", "packageName" to packageName),
                ),
            ),
        ),
    )
}

internal fun ContactRow.toResult(): SearchResult = SearchResult(
    key = "ct#$name\u0001$number",
    source = SRC_CONTACT,
    title = name,
    subtitle = number,
    icon = name.firstOrNull()?.toString()?.let(SearchIcon::Text) ?: SearchIcon.None,
    primary = SearchAction.Open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_send_sms),
            SearchAction.Open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))),
        ),
    ),
)

internal fun AudioTrackRow.toResult(): SearchResult = SearchResult(
    key = "mu#$uri",
    source = SRC_MUSIC,
    title = title,
    subtitle = artist,
    icon = SearchIcon.Vector(Icons.Default.MusicNote),
    primary = viewIntent(uri, "audio/*"),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_share),
            SearchAction.Share(uri, "audio/*"),
        ),
    ),
)

internal fun MediaFileRow.toResult(): SearchResult = SearchResult(
    key = "iv#$uri",
    source = SRC_FILE,
    title = name,
    subtitle = bucket,
    icon = SearchIcon.Vector(if (mime.startsWith("video")) Icons.Default.Movie else Icons.Default.Image),
    primary = viewIntent(uri, mime),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_share),
            SearchAction.Share(uri, mime),
        ),
    ),
)

internal fun DocFileRow.toResult(): SearchResult = SearchResult(
    key = "doc#$uri",
    source = SRC_FILE,
    title = name,
    subtitle = sizeText,
    icon = SearchIcon.Vector(Icons.Default.Description),
    primary = viewIntent(uri, mime),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_share),
            SearchAction.Share(uri, mime),
        ),
    ),
)

internal fun CalendarRow.toResult(): SearchResult = SearchResult(
    key = "ce#$eventId\u0001$timeText",
    source = SRC_CALENDAR,
    title = title,
    subtitle = listOf(timeText, location).filter { it.isNotEmpty() }.joinToString(" · "),
    icon = SearchIcon.Vector(Icons.Default.DateRange),
    primary = SearchAction.Open(
        Intent(
            Intent.ACTION_VIEW,
            android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        ),
    ),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_copy),
            SearchAction.Copy(listOf(title, timeText, location).filter { it.isNotEmpty() }.joinToString(" ")),
        ),
    ),
)

internal fun SmsRow.toResult(): SearchResult = SearchResult(
    key = "sms#$address\u0001$dateText\u0001${body.hashCode()}",
    source = SRC_SMS,
    title = body.lines().firstOrNull()?.trim()?.take(40).orEmpty().ifBlank { address },
    subtitle = listOf(address, dateText).filter { it.isNotEmpty() }.joinToString(" · "),
    icon = SearchIcon.Vector(Icons.AutoMirrored.Filled.Message),
    primary = SearchAction.Open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$address"))),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_copy),
            SearchAction.Copy(body),
        ),
    ),
)

internal fun CallRow.toResult(): SearchResult = SearchResult(
    key = "cl#$name\u0001$number\u0001$typeText\u0001$dateText",
    source = SRC_CALL,
    title = name.ifBlank { number },
    subtitle = listOf(number, typeText, dateText).filter { it.isNotEmpty() }.joinToString(" · "),
    icon = SearchIcon.Vector(Icons.Default.Call),
    primary = SearchAction.Open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_send_sms),
            SearchAction.Open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))),
        ),
    ),
)

/** 剪贴记录行：点回填搜索框、长按写回系统剪贴板。key 只取哈希+长度，全文不进 key。 */
fun clipResult(text: String): SearchResult = SearchResult(
    key = "clip#${text.hashCode()}\u0001${text.length}",
    source = SRC_CLIP,
    title = text,
    subtitle = "",
    icon = SearchIcon.Vector(Icons.Default.ContentPaste),
    primary = SearchAction.SetQuery(text),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_copy),
            SearchAction.Copy(text),
        ),
    ),
)

/** 裸 URL 行：恒置顶语义由 search 的 rank 3 给出；长按复制链接。 */
internal fun urlResultRow(url: String): SearchResult = SearchResult(
    key = "url#$url",
    source = SOURCE_URL,
    title = url,
    subtitle = "",
    icon = SearchIcon.Vector(Icons.AutoMirrored.Filled.OpenInNew),
    primary = SearchAction.OpenUrl(url),
    actions = listOf(
        SearchActionItem(
            SearchActionLabel.Res(R.string.search_action_copy_link),
            SearchAction.Copy(url),
        ),
    ),
)

/** 计算器行：标题 = 结果，副标题 = 算式；点击由面板直接复制。 */
internal fun calcResultRow(expression: String, value: String): SearchResult = SearchResult(
    key = "calc#$expression",
    source = SOURCE_CALC,
    title = value,
    subtitle = expression,
    icon = SearchIcon.Text("="),
    primary = SearchAction.Copy(value),
)

// ---------- 共享小工具 ----------

private fun viewIntent(uri: String, mime: String): SearchAction.Open =
    SearchAction.Open(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), mime.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )

private fun iconOf(icon: Any?): SearchIcon = when (icon) {
    null -> SearchIcon.None
    is ImageVector -> SearchIcon.Vector(icon)
    is ImageBitmap -> SearchIcon.Bitmap(icon)
    is TextIcon -> SearchIcon.Text(icon.text)
    else -> SearchIcon.Request(icon)
}
