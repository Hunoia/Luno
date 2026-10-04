package hunoia.luno.quicklaunch

import android.content.Context
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.quicklaunch.query.QuickSearchResult
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.TextIcon

/**
 * 列表上下羽化：顶部 28dp / 底部 24dp，溢出截断 melts 进背景。
 * 底部罩的是钉住的首行——贴紧输入框后它 melts 进框，顺滑但首行底部是虚的，
 * 这是内容羽化的税。离屏层多一笔绘制，行多时装机看帧率。
 */
internal fun Modifier.listFade(
    top: androidx.compose.ui.unit.Dp = 28.dp,
    bottom: androidx.compose.ui.unit.Dp = 12.dp,
): Modifier = this
    .graphicsLayer {
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val topF = (top.toPx() / h).coerceIn(0f, 0.5f)
        val bottomF = (bottom.toPx() / h).coerceIn(0f, (1f - topF).coerceAtLeast(0f))
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to ComposeColor.Transparent,
                topF to ComposeColor.Black,
                (1f - bottomF) to ComposeColor.Black,
                1f to ComposeColor.Transparent,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

/** 剪贴板行：标题=剪贴内容（单行省略），无副标题（跟历史行只差图标）；点即回填，无长按语义。 */@Composable
internal fun ClipboardRow(text: String, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    SelectableListItem(
        title = text,
        subtitle = "",
        selected = false,
        onSelect = { onClick() },
        shape = shape,
        icon = Icons.Default.ContentPaste,
        verticalGap = 0.dp,
        horizontalGap = 0.dp,
        containerColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.65f),
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
    )
}
@Composable
internal fun HistoryRow(
    text: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    icon: ImageVector = Icons.Default.Search,
    textIcon: TextIcon? = null,
) {
    SelectableListItem(
        title = text,
        subtitle = "",
        selected = false,
        onSelect = { onClick() },
        shape = shape,
        icon = icon,
        textIcon = textIcon,
        verticalGap = 0.dp,
        horizontalGap = 0.dp,
        containerColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.65f),
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        onLongClick = onLongClick,
    )
}

@Composable
internal fun ResultRow(
    result: QuickSearchResult,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val subtitle = if (result is QuickSearchResult.App && result.isDisabled) {
        result.subtitle + " · " + stringResource(R.string.quick_search_app_disabled)
    } else {
        result.subtitle
    }
    SelectableListItem(
        title = result.title,
        subtitle = subtitle,
        selected = false,
        onSelect = { onClick() },
        shape = shape,        iconModel = iconModel(result, context),
        icon = iconVector(result),
        textIcon = textIcon(result),
        verticalGap = 0.dp,
        horizontalGap = 0.dp,
        // 半透明底色：透出窗口背后的系统模糊，不支持的设备直接呈现半透明。
        containerColor = colorScheme.surfaceBright.copy(alpha = 0.65f),
        selectedContainerColor = colorScheme.primaryContainer.copy(alpha = 0.65f),
        onLongClick = onLongClick,
    )
}

@Composable
internal fun iconVector(result: QuickSearchResult): ImageVector? = when (result) {
    is QuickSearchResult.Url -> Icons.AutoMirrored.Filled.OpenInNew
    is QuickSearchResult.EngineSearch -> null
    is QuickSearchResult.Calc -> null
    is QuickSearchResult.Settings -> Icons.Default.Search
    is QuickSearchResult.ActionDef -> result.icon as? ImageVector
    is QuickSearchResult.LibraryEntry -> result.icon as? ImageVector
    is QuickSearchResult.App -> null
    is QuickSearchResult.Shortcut -> null
    is QuickSearchResult.Contact -> null
    is QuickSearchResult.AudioTrack -> Icons.Default.MusicNote
    is QuickSearchResult.ImageVideo ->
        if (result.mime.startsWith("video")) Icons.Default.Movie else Icons.Default.Image
    is QuickSearchResult.DocFile -> Icons.Default.Description
    is QuickSearchResult.CalendarEvent -> Icons.Default.DateRange
    is QuickSearchResult.SmsMsg -> Icons.AutoMirrored.Filled.Message
    is QuickSearchResult.CallLogEntry -> Icons.Default.Call
    is QuickSearchResult.PhoneToggle -> when (result.kind) {
        QuickSearchResult.ToggleKind.WIFI -> Icons.Default.Wifi
        QuickSearchResult.ToggleKind.BLUETOOTH -> Icons.Default.Bluetooth
        QuickSearchResult.ToggleKind.TORCH -> Icons.Default.FlashlightOn
        QuickSearchResult.ToggleKind.DND -> Icons.Default.DoNotDisturbOn
        QuickSearchResult.ToggleKind.LOCATION -> Icons.Default.LocationOn
        QuickSearchResult.ToggleKind.HOTSPOT -> Icons.Default.WifiTethering
    }
}

@Composable
internal fun iconModel(result: QuickSearchResult, context: android.content.Context): Any? {
    val sizePx = with(LocalDensity.current) { 24.dp.toPx().toInt() }
    return when (result) {
        is QuickSearchResult.App ->
            QuickLaunchFacade.appIconRequest(context, result.appInfo.packageName, sizePx)
        // 快捷方式自带图标优先（行为不变），取不到才回落来源应用图标（之前行首空着）。
        is QuickSearchResult.Shortcut -> result.shortcut.iconBitmap
            ?: QuickLaunchFacade.appIconRequest(context, result.shortcut.packageName, sizePx)
        else -> null
    }
}

@Composable
internal fun textIcon(result: QuickSearchResult): TextIcon? = when (result) {
    is QuickSearchResult.LibraryEntry -> result.icon as? TextIcon
    is QuickSearchResult.ActionDef -> result.icon as? TextIcon
    // 引擎行用首字图标（B/G/D…），计算器行用 =，联系人用姓名首字，替代通用图标。
    is QuickSearchResult.EngineSearch -> result.engineName.firstOrNull()?.toString()?.let { TextIcon(it) }
    is QuickSearchResult.Calc -> TextIcon("=")
    is QuickSearchResult.Contact -> result.name.firstOrNull()?.toString()?.let { TextIcon(it) }
    else -> null
}
