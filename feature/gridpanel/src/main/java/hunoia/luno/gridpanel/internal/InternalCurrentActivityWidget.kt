package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.runtime.environment.ForegroundActivityHub
import hunoia.luno.runtime.surface.ActivityFloatDispatch
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.pressCombinedClickable
import hunoia.luno.ui.icon.rememberAppLabel
import hunoia.luno.ui.theme.containerShape
import kotlinx.coroutines.launch

/**
 * 当前界面：两行显示前台 Activity（行1「应用名 · 短名」，行2 完整组件名小字）。
 * 数据来自 [ForegroundActivityHub]（a11y TYPE_WINDOW_STATE_CHANGED 维护，
 * 自家窗口事件不入，面板打开时显示的是面板背后的应用）。
 *
 * 点按 = 复制完整组件名（成功收面板，与剪贴板条目复制同反馈口径）；
 * 长按 = 菜单（复制 / 钉悬浮）。「钉悬浮」经 [ActivityFloatDispatch] 拉起
 * FloatBallService 的实时悬浮卡，钉完即收面板（剪贴板 FLOAT 同构）。
 */
@Composable
fun CurrentActivityCell(
    modifier: Modifier = Modifier,
    spanX: Int = 2,
    spanY: Int = 1,
    /** 编辑态=false：点按/长按禁掉，事件让给层根拖拽。 */
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val closePanel = LocalGridPanelClose.current
    val scope = rememberCoroutineScope()
    val activity by ForegroundActivityHub.current.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }

    fun copyComponent() {
        val current = activity ?: return
        scope.launch {
            if (ClipboardCenter.copy(current.componentName)) {
                closePanel()
            } else {
                showToast(R.string.grid_panel_activity_copy_failed)
            }
        }
    }

    fun pinFloat() {
        ActivityFloatDispatch.show?.invoke(context)
        closePanel()
    }

    val current = activity
    val onCard = gridPanelOnCardColor
    Box(modifier = modifier.fillMaxSize()) {
        val cellModifier = if (enabled) {
            Modifier
                .fillMaxSize()
                .clip(containerShape(ItemCorner))
                .pressCombinedClickable(
                    onClick = { copyComponent() },
                    onLongClick = { menuOpen = true },
                    onClickLabel = stringResource(R.string.grid_panel_activity_copy),
                    onLongClickLabel = stringResource(R.string.grid_panel_activity_pin_float),
                )
        } else {
            Modifier
                .fillMaxSize()
                .clip(containerShape(ItemCorner))
        }
        Column(
            modifier = cellModifier.padding(WidgetInnerPadding * 2),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start,
        ) {
            if (current == null) {
                Text(
                    text = stringResource(R.string.grid_panel_activity_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = onCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                val appLabel = rememberAppLabel(current.packageName, current.packageName) {
                    context.packageManager.getApplicationInfo(current.packageName, 0)
                        .let { context.packageManager.getApplicationLabel(it).toString() }
                }
                Text(
                    text = "$appLabel · ${current.shortClassName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = current.componentName,
                    style = MaterialTheme.typography.labelSmall,
                    color = onCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        LunoDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            LunoDropdownMenuItem(
                text = stringResource(R.string.grid_panel_activity_copy),
                onClick = { menuOpen = false; copyComponent() },
            )
            LunoDropdownMenuItem(
                text = stringResource(R.string.grid_panel_activity_pin_float),
                onClick = { menuOpen = false; pinFloat() },
            )
        }
    }
}
