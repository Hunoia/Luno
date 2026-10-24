package hunoia.luno.quicklaunch.query

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.action.model.StoredAction
import hunoia.luno.R
import hunoia.luno.config.model.SRC_ACTION
import hunoia.luno.config.model.SRC_APP
import hunoia.luno.config.model.SRC_CALENDAR
import hunoia.luno.config.model.SRC_CALL
import hunoia.luno.config.model.SRC_CLIP
import hunoia.luno.config.model.SRC_CONTACT
import hunoia.luno.config.model.SRC_FILE
import hunoia.luno.config.model.SRC_MUSIC
import hunoia.luno.config.model.SRC_SETTINGS
import hunoia.luno.config.model.SRC_SHORTCUT
import hunoia.luno.config.model.SRC_SMS
import hunoia.luno.config.model.SRC_TOGGLE
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.SearchLaunch.Action as SearchLaunchAction
import hunoia.luno.config.model.SearchLaunch.Chooser
import hunoia.luno.config.model.SearchLaunch.Share as SearchLaunchShare
import hunoia.luno.config.model.SearchLaunch.ShareImage as SearchLaunchShareImage
import hunoia.luno.config.model.SearchLaunch.Url as SearchLaunchUrl
import hunoia.luno.config.model.copyUrl
import hunoia.luno.config.model.fillSearchQuery
import hunoia.luno.ui.component.TextIcon

/**
 * 旧结果模型 → 展示/动作模型。这里是唯一还认识业务类型的桥；
 * 新来源可以直接构造 [SearchResult]，不必再经过这个 when。
 */
fun QuickSearchResult.toSearchResult(): SearchResult {
    val key = stableId()
    return when (this) {
        is QuickSearchResult.ActionDef -> SearchResult(
            key = key,
            source = SRC_ACTION,
            title = title,
            subtitle = subtitle,
            icon = iconOf(icon),
            primary = SearchAction.Run(stored),
        )

        is QuickSearchResult.LibraryEntry -> SearchResult(
            key = key,
            source = SRC_ACTION,
            title = title,
            subtitle = subtitle,
            icon = iconOf(icon),
            primary = SearchAction.Run(StoredAction.of("internal.libraryRef", "entryId" to entry.id)),
        )

        is QuickSearchResult.App -> {
            val details = StoredAction.of("app.details", "packageName" to appInfo.packageName)
            SearchResult(
                key = key,
                source = SRC_APP,
                title = title,
                subtitle = subtitle,
                icon = SearchIcon.AppIcon(appInfo.packageName),
                primary = if (isDisabled) {
                    SearchAction.Run(details)
                } else {
                    SearchAction.Run(
                        StoredAction.of(
                            "app.launch",
                            "packageName" to appInfo.packageName,
                            "className" to appInfo.className,
                            "miniWindow" to appInfo.miniWindow.toString(),
                        ),
                    )
                },
                // 长按始终进应用详情（停用应用也保留该入口）。
                actions = listOf(
                    SearchActionItem(
                        SearchActionLabel.Res(R.string.search_action_app_details),
                        SearchAction.Run(details),
                    ),
                ),
                badge = if (isDisabled) SearchBadge.DisabledApp else null,
            )
        }

        is QuickSearchResult.Shortcut -> SearchResult(
            key = key,
            source = SRC_SHORTCUT,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.AppIcon(shortcut.packageName, shortcut.iconBitmap?.asImageBitmap()),
            primary = SearchAction.Run(
                StoredAction.of(
                    "app.launchShortcut",
                    "data" to hunoia.luno.core.JsonSerializer.encodeToString(shortcut),
                ),
            ),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_app_details),
                    SearchAction.Run(
                        StoredAction.of("app.details", "packageName" to shortcut.packageName),
                    ),
                ),
            ),
        )

        is QuickSearchResult.Settings -> SearchResult(
            key = key,
            source = SRC_SETTINGS,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(Icons.Default.Search),
            primary = SearchAction.Run(toConfigAction()),
        )

        is QuickSearchResult.Url -> SearchResult(
            key = key,
            source = SOURCE_URL,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(Icons.AutoMirrored.Filled.OpenInNew),
            primary = SearchAction.OpenUrl(url),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_copy_link),
                    SearchAction.Copy(url),
                ),
            ),
        )

        is QuickSearchResult.EngineSearch -> engineResultRow(
            title = title,
            query = query,
            engine = engine,
        )

        is QuickSearchResult.Calc -> SearchResult(
            key = key,
            source = SOURCE_CALC,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Text("="),
            primary = SearchAction.Copy(value),
        )

        is QuickSearchResult.Contact -> SearchResult(
            key = key,
            source = SRC_CONTACT,
            title = title,
            subtitle = subtitle,
            icon = name.firstOrNull()?.toString()?.let(SearchIcon::Text) ?: SearchIcon.None,
            primary = SearchAction.Open(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number}")),
            ),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_send_sms),
                    SearchAction.Open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))),
                ),
            ),
        )

        is QuickSearchResult.AudioTrack -> SearchResult(
            key = key,
            source = SRC_MUSIC,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(Icons.Default.MusicNote),
            primary = viewIntent(uri, "audio/*"),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_share),
                    SearchAction.Share(uri, "audio/*"),
                ),
            ),
        )

        is QuickSearchResult.ImageVideo -> SearchResult(
            key = key,
            source = SRC_FILE,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(
                if (mime.startsWith("video")) Icons.Default.Movie else Icons.Default.Image,
            ),
            primary = viewIntent(uri, mime),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_share),
                    SearchAction.Share(uri, mime),
                ),
            ),
        )

        is QuickSearchResult.DocFile -> SearchResult(
            key = key,
            source = SRC_FILE,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(Icons.Default.Description),
            primary = viewIntent(uri, mime),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_share),
                    SearchAction.Share(uri, mime),
                ),
            ),
        )

        is QuickSearchResult.CalendarEvent -> SearchResult(
            key = key,
            source = SRC_CALENDAR,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(Icons.Default.DateRange),
            primary = SearchAction.Open(
                Intent(
                    Intent.ACTION_VIEW,
                    android.content.ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI,
                        eventId,
                    ),
                ),
            ),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_copy),
                    SearchAction.Copy(
                        listOf(title_, timeText, location)
                            .filter { it.isNotEmpty() }
                            .joinToString(" "),
                    ),
                ),
            ),
        )

        is QuickSearchResult.SmsMsg -> SearchResult(
            key = key,
            source = SRC_SMS,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(Icons.AutoMirrored.Filled.Message),
            primary = SearchAction.Open(
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$address")),
            ),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_copy),
                    SearchAction.Copy(body),
                ),
            ),
        )

        is QuickSearchResult.CallLogEntry -> SearchResult(
            key = key,
            source = SRC_CALL,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(Icons.Default.Call),
            primary = SearchAction.Open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_send_sms),
                    SearchAction.Open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))),
                ),
            ),
        )

        is QuickSearchResult.PhoneToggle -> SearchResult(
            key = key,
            source = SRC_TOGGLE,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(toggleIcon(kind)),
            primary = SearchAction.SystemToggle(kind.toActionKind()),
        )

        is QuickSearchResult.ClipEntry -> SearchResult(
            key = key,
            source = SRC_CLIP,
            title = title,
            subtitle = subtitle,
            icon = SearchIcon.Vector(Icons.Default.ContentPaste),
            primary = SearchAction.SetQuery(text),
            actions = listOf(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_copy),
                    SearchAction.Copy(text),
                ),
            ),
        )
    }
}

internal fun SearchLaunch.toSearchAction(query: String): SearchAction = when (this) {
    is SearchLaunchUrl -> SearchAction.OpenUrl(
        url = template.fillSearchQuery(query),
        packageName = packageName,
        fallbackUrl = fallbackTemplate.takeIf { it.isNotBlank() }
            ?.fillSearchQuery(query)
            .orEmpty(),
    )

    is Chooser -> SearchAction.OpenUrl(
        url = template.fillSearchQuery(query),
        forceChooser = true,
    )

    is SearchLaunchShare -> SearchAction.Open(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textTemplate.fillSearchQuery(query))
            if (packageName.isNotBlank()) setPackage(packageName)
        },
    )

    is SearchLaunchShareImage -> SearchAction.PickImage(packageName)

    is SearchLaunchAction -> SearchAction.Run(action.fillSearchQuery(query))
}

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

private fun toggleIcon(kind: QuickSearchResult.ToggleKind): ImageVector = when (kind) {
    QuickSearchResult.ToggleKind.WIFI -> Icons.Default.Wifi
    QuickSearchResult.ToggleKind.BLUETOOTH -> Icons.Default.Bluetooth
    QuickSearchResult.ToggleKind.TORCH -> Icons.Default.FlashlightOn
    QuickSearchResult.ToggleKind.DND -> Icons.Default.DoNotDisturbOn
    QuickSearchResult.ToggleKind.LOCATION -> Icons.Default.LocationOn
    QuickSearchResult.ToggleKind.HOTSPOT -> Icons.Default.WifiTethering
}

private fun QuickSearchResult.ToggleKind.toActionKind(): SearchToggleKind = when (this) {
    QuickSearchResult.ToggleKind.WIFI -> SearchToggleKind.WIFI
    QuickSearchResult.ToggleKind.BLUETOOTH -> SearchToggleKind.BLUETOOTH
    QuickSearchResult.ToggleKind.TORCH -> SearchToggleKind.TORCH
    QuickSearchResult.ToggleKind.DND -> SearchToggleKind.DND
    QuickSearchResult.ToggleKind.LOCATION -> SearchToggleKind.LOCATION
    QuickSearchResult.ToggleKind.HOTSPOT -> SearchToggleKind.HOTSPOT
}
