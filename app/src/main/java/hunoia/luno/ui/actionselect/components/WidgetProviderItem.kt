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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.gridpanel.widget.WidgetProviderGroup
import hunoia.luno.gridpanel.widget.WidgetSpan
import hunoia.luno.gridpanel.widget.applicationIconOf
import hunoia.luno.gridpanel.widget.widgetPreviewOf
import hunoia.luno.ui.theme.SmallShape

/**
 * 小部件选择器组件（一墨 `ComponentWidgetPickerOverlay` 的 Luno 版，对齐记录见
 * docs/grid-panel-moting-compare.md §10⑤）：
 * **每 app 一个分区，分区内横向滚动的预览卡**，取代原「chip + 纵向行列表」。
 *
 * 一墨的量化参数照搬：
 * - 分区头 = 24dp 应用图标 + app 名 + 数量 badge；
 * - 预览卡 130×120dp、图高 60dp（无 preview 回落 40dp 应用图标，再无就不画）；
 * - 名称最多 2 行 + `<spanX>x<spanY>` 占格数角标 —— 用户加之前就能看到会占多大；
 * - 卡间距 12dp。
 * 差异一处：一墨的卡片点进多选，这里恒单选（点卡 → 直接进 bind/配置流收页）。
 *
 * 预览图**不能省**：没有预览时一屏几十张全是同一个应用图标，用户分不出哪个是
 * 哪只 widget，只能一只只加进去试（原行列表版吃过这亏，见旧 WidgetProviderItem 注释）。
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
        modifier = modifier
            .width(130.dp)
            .height(120.dp)
            .clickable(onClick = onSelect),
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
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
