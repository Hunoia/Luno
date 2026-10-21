package hunoia.luno.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.ui.theme.PageGutter

private fun statusIcon(level: HomeRuntimeStatusLevel): ImageVector = when (level) {
    HomeRuntimeStatusLevel.Running -> Icons.Filled.TaskAlt
    HomeRuntimeStatusLevel.NeedsAttention -> Icons.Filled.Warning
    HomeRuntimeStatusLevel.Unavailable -> Icons.Filled.Cancel
}

private fun statusTitleRes(level: HomeRuntimeStatusLevel): Int = when (level) {
    HomeRuntimeStatusLevel.Running -> R.string.home_status_running
    HomeRuntimeStatusLevel.NeedsAttention -> R.string.home_status_needs_attention
    HomeRuntimeStatusLevel.Unavailable -> R.string.home_status_unavailable
}

private val HomePrimaryIssue.descRes: Int?
    get() = when (this) {
        HomePrimaryIssue.AccessibilityDisabled -> R.string.home_status_accessibility_desc
        HomePrimaryIssue.GestureDisabled -> R.string.home_status_gesture_desc
        HomePrimaryIssue.ShizukuNotRunning -> R.string.home_status_shizuku_not_running_desc
        HomePrimaryIssue.ShizukuNotAuthorized -> R.string.home_status_shizuku_not_authorized_desc
        HomePrimaryIssue.KeepAliveDisabled -> R.string.home_status_keep_alive_desc
        HomePrimaryIssue.None -> null
    }

@Composable
fun HomeRuntimeStatusCard(
    runtimeStatus: HomeRuntimeStatus,
    isGestureSwitchEnabled: Boolean,
    onGestureSwitchEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    // 颜色渐变：切状态不再硬切闪屏。
    val containerColor by animateColorAsState(
        targetValue = when (runtimeStatus.level) {
            HomeRuntimeStatusLevel.Running -> colorScheme.primaryContainer
            HomeRuntimeStatusLevel.NeedsAttention -> colorScheme.secondaryContainer
            HomeRuntimeStatusLevel.Unavailable -> colorScheme.errorContainer
        },
        animationSpec = tween(300),
        label = "statusContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when (runtimeStatus.level) {
            HomeRuntimeStatusLevel.Running -> colorScheme.onPrimaryContainer
            HomeRuntimeStatusLevel.NeedsAttention -> colorScheme.onSecondaryContainer
            HomeRuntimeStatusLevel.Unavailable -> colorScheme.onErrorContainer
        },
        animationSpec = tween(300),
        label = "statusContent",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = hunoia.luno.ui.theme.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PageGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态图标徽章：40dp 圆底 + 24dp 图标，底色用 contentColor 淡化；随状态淡入淡出+缩放。
            AnimatedContent(
                targetState = runtimeStatus.level,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200))) togetherWith
                        fadeOut(tween(150))
                },
                label = "statusIcon",
            ) { level ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color = contentColor.copy(alpha = 0.12f), shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = statusIcon(level),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            // 标题/描述随状态淡入淡出：primaryIssue 在同 level 内变化（如 Shizuku 子状态）也能刷新。
            AnimatedContent(
                targetState = runtimeStatus,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "statusText",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = PageGutter),
            ) { status ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(statusTitleRes(status.level)),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(status.primaryIssue.descRes ?: R.string.home_status_running_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                modifier = Modifier.padding(start = PageGutter),
                checked = isGestureSwitchEnabled,
                onCheckedChange = onGestureSwitchEnabledChange,
            )
        }
    }
}
