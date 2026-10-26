package hunoia.luno.gridpanel.internal

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.R
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.quicklaunch.icon.rememberAppDrawable
import hunoia.luno.quicklaunch.icon.rememberAppLabel

/**
 * 最近应用：一排图标（旧 → 新，最多 6 个，不含当前前台），点按直达。
 * 数据来自 RecentAppsHub（a11y TYPE_WINDOW_STATE_CHANGED 维护，无额外权限）。
 */
@Composable
fun RecentAppsCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 1,
    /** 编辑态=false：图标点按禁掉，事件让给层根拖拽。 */
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val closePanel = LocalGridPanelClose.current
    val history by hunoia.luno.runtime.environment.RecentAppsHub.history.collectAsState()
    val foreground by hunoia.luno.runtime.environment.RecentAppsHub.foreground.collectAsState()
    // 容量 = 格数；一行一格，多行往下排。
    val capacity = (spanX * spanY).coerceAtLeast(1)
    // 过滤当前前台与本应用自身：前台不算「最近」（当前正在用它），本应用包名理论上
    // 已在源头过滤，这里再拦一道防御（面板窗口事件时序竞态下避免闪现本应用图标）。
    val pkgs = remember(history, foreground, capacity) {
        history.asSequence()
            .filter { it != foreground && it != context.packageName }
            .toList()
            .takeLast(capacity)
    }
    val onCard = gridPanelOnCardColor
    if (pkgs.isEmpty()) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .alpha(0.45f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.grid_panel_recents_empty),
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
            )
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        for (row in 0 until spanY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (col in 0 until spanX) {
                    val index = row * spanX + col
                    if (index >= pkgs.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val pkg = pkgs[index]
                    // 跨进程 PM 查询：缓存 + IO 异步，组合期不再同步阻塞（见 rememberAppDrawable）。
                    val icon = rememberAppDrawable("app/$pkg") {
                        context.packageManager.getApplicationIcon(pkg)
                    }
                    // 磁贴没有文字：无障碍名用应用标签，首帧/查询失败回落包名。
                    val appLabel = rememberAppLabel("label/$pkg", pkg) {
                        context.packageManager.getApplicationInfo(pkg, 0)
                            .let { context.packageManager.getApplicationLabel(it).toString() }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(ItemCorner * 0.5f))
                            .semantics { contentDescription = appLabel }
                            .then(
                                if (enabled) {
                                    Modifier.clickable {
                                        val launched = runCatching {
                                            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(it)
                                                true
                                            } ?: false
                                        }.getOrDefault(false)
                                        if (launched) closePanel()
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) {
                            AsyncImage(
                                model = icon,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                imageLoader = context.imageLoader,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = onCard,
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
