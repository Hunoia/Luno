package hunoia.luno.ui.actionselect

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.gridpanel.widget.WidgetProviderGroup
import hunoia.luno.gridpanel.widget.WidgetSpan
import hunoia.luno.gridpanel.widget.applicationIconOf
import hunoia.luno.gridpanel.widget.widgetPreviewOf
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.SmallShape

/**
 * 小部件选择器：每 app 一个分区，分区内横向滚动预览卡（恒单选）。
 * 预览图不能省：没有预览时一屏几十张全是同一应用图标，用户分不出哪只是哪个 widget。
 */

/** 分区头：应用图标 + 应用名 + 该应用下的小部件数量。 */
@Composable
internal fun WidgetGroupHeaderRow(
    group: WidgetProviderGroup,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val icon = remember(group.providers.firstOrNull()?.provider) {
        group.providers.firstOrNull()?.let { applicationIconOf(it, context.packageManager) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(SmallShape),
                imageLoader = context.imageLoader,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = group.appLabel,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = group.providers.size.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(SmallShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** 内置小部件的选择卡：图标 + 名字 + 占格数（无 provider，无预览图可加载）。 */
@Composable
internal fun InternalWidgetCard(
    def: hunoia.luno.gridpanel.internal.InternalWidgetDef,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // heightIn 而不是 height：大字体下「图标 + 两行名称 + 格数」总高会超过 120dp，
        // 固定高会被 Surface 裁掉底部；卡片跟着文字长高，行内其它卡由 LazyRow 自适应。
        modifier = modifier
            .width(130.dp)
            .heightIn(min = 120.dp)
            .clickable(onClick = onSelect),
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = CardInnerSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = when (def.id) {
                    hunoia.luno.gridpanel.internal.InternalWidgetIds.MEDIA -> Icons.Filled.MusicNote
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(def.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${def.spanX}x${def.spanY}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 横向滚动区里的单张预览卡（130×120dp）：预览图 → 名称 → 占格数。 */
@Composable
internal fun WidgetPreviewCard(
    info: AppWidgetProviderInfo,
    span: WidgetSpan,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preview = remember(info.previewImage, info.provider) { widgetPreviewOf(info, context) }
    val appIcon = remember(info.provider) { applicationIconOf(info, context.packageManager) }
    val label = remember(info.provider) {
        runCatching { info.loadLabel(context.packageManager).toString() }
            .getOrDefault(info.provider?.className ?: "")
    }
    Surface(
        // 同上：预览图 + 两行名称 + 格数在 fontScale 1.4 下已超过 120dp，不能再锁死高度。
        modifier = modifier
            .width(130.dp)
            .heightIn(min = 120.dp)
            .clickable(onClick = onSelect),
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = CardInnerSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                preview != null -> AsyncImage(
                    model = preview,
                    contentDescription = null,
                    modifier = Modifier
                        .height(60.dp)
                        .clip(SmallShape),
                    imageLoader = context.imageLoader,
                )
                appIcon != null -> AsyncImage(
                    model = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    imageLoader = context.imageLoader,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${span.spanX}x${span.spanY}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
