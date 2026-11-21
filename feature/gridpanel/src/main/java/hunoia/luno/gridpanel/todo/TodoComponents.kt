package hunoia.luno.gridpanel.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.feedback.LunoLinearProgress
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.ProgressBarHeight
import hunoia.luno.ui.theme.MotionSubtle

/*
 * 待办页的共享小件：勾选圆点、顶部进度摘要、撤销条。
 * 视觉 token 取自 :ui:design 主题（squircle / 胶囊 / 语义色），页面不另开色板。
 */

/**
 * 完成切换圆点：48dp 命中区（视觉 24dp）。两种状态都是**空心描边**——
 * 未完成 = 中性描边；已完成 = 主色描边 + 主色勾号（勾号 spring 缩放淡入），不画实心圆。
 *
 * 无障碍按「动作 + 目标」发声（`标记完成：买牛奶`），并用 `toggleableState`
 * 报勾选态；不用确认弹层的标题文案当标签——现在点勾即时生效，没有确认层。
 */
@Composable
fun TodoCheckToggle(
    done: Boolean,
    text: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val actionLabel = stringResource(
        if (done) R.string.todo_check_uncomplete else R.string.todo_check_complete,
        text,
    )
    val checkProgress by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "todoCheckProgress",
    )
    val ringColor by animateColorAsState(
        targetValue = if (done) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.55f),
        animationSpec = tween(MotionSubtle),
        label = "todoCheckRing",
    )
    Box(
        modifier = modifier
            .size(MinInteractiveSize)
            .clip(CircleShape)
            .pressClickable(onClickLabel = actionLabel, role = Role.Checkbox, onClick = onToggle)
            .semantics { toggleableState = ToggleableState(done) },
        contentAlignment = Alignment.Center,
    ) {
        // 空心描边：完成时描边转主色，勾号叠在圈内，不填底色。
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(1.5.dp, ringColor, CircleShape),
        )
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    scaleX = checkProgress
                    scaleY = checkProgress
                    alpha = checkProgress
                },
        )
    }
}

/**
 * 顶部进度摘要（无卡片）：一行「N 项待完成」+ 右侧过期 / 已完成计数，下方 3dp 进度线。
 *
 * 取代原来的进度卡：待办页是执行面，首屏空间优先给列表；进度线一条即可扫读。
 * [overdue] > 0 时右侧改为过期的 error 计数（比「已完成」更该被看见）。
 */
@Composable
fun TodoProgressHeader(
    pending: Int,
    done: Int,
    overdue: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val total = pending + done
    val target = if (total == 0) 0f else done.toFloat() / total
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(MotionSubtle),
        label = "todoProgress",
    )
    val trailing = when {
        overdue > 0 -> stringResource(R.string.todo_progress_overdue, overdue)
        done > 0 -> stringResource(R.string.todo_progress_done, done)
        else -> null
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (pending == 0) {
                    stringResource(R.string.todo_progress_all_done)
                } else {
                    stringResource(R.string.todo_progress_remaining, pending)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue > 0) scheme.error else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 进度线：波浪确定进度（M3 1.5 expressive），轨道常驻；组件自带 0 进度态。
        LunoLinearProgress(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(ProgressBarHeight),
        )
    }
}

/**
 * 待办页撤销条：浮在底栏上方（底部留白由调用方按停靠栏高度传入），显示动作结果 + [撤销]。
 *
 * 计时归调用方（页面按「当前动作」起 4s 定时），组件本身只做呈现：
 * 同文案的两次动作也能各自重新计时。
 */
@Composable
fun TodoUndoBar(
    message: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CapsuleShape,
        color = scheme.inverseSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.inverseOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            PressTextButton(
                onClick = onUndo,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.todo_undo_action),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.inversePrimary,
                )
            }
        }
    }
}
