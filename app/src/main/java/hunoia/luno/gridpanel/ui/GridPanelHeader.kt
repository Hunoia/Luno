package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.gridpanel.geometry.HeaderHeight
import hunoia.luno.gridpanel.geometry.IndicatorHeight
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 页眉：编辑态显示「编辑中」+ 面板名 + 时间日期；多页时标题下方铺页指示点；
 * 非编辑态只显示面板名（进编辑入口是**长按空白**）。
 *
 * 时间日期**不进网格**：它是纯 Compose 自绘的，AppWidget 却要常驻宿主、用户授权、
 * 跨进程存 id，塞进同一个模型会让每格都处理「有没有 appWidgetId」的分支。
 */
@Composable
fun GridPanelHeader(
    editMode: Boolean,
    panelName: String,
    pageCount: Int,
    activePage: Int,
    onSelectPage: (Int) -> Unit,
    onDoneEdit: () -> Unit,
    onAdd: () -> Unit,
) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Calendar.getInstance()
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time) }
    val date = remember(now) {
        SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault()).format(now.time)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when {
                    editMode -> stringResource(R.string.grid_panel_edit_mode)
                    panelName.isNotBlank() -> panelName
                    else -> stringResource(R.string.grid_panel_unnamed)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (editMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (editMode) {
            IconButton(onClick = onDoneEdit, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.grid_panel_edit_done),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.grid_panel_add_item),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (pageCount > 1) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IndicatorHeight)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                if (index > 0) {
                    Box(Modifier.width(6.dp))
                }
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index == activePage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                        )
                        .clickable { onSelectPage(index) },
                )
            }
        }
    }
}
