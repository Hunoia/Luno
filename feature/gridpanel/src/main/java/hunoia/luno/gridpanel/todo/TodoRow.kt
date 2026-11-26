package hunoia.luno.gridpanel.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.TodoItem
import hunoia.luno.config.model.TodoReminder
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.TodoReminderFormat
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.row.SectionBadge
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.cardSurfaceColor
import hunoia.luno.ui.theme.pageSurfaceColor
import java.time.ZoneId
import kotlin.math.abs

/*
 * 待办页的行与小节头（页面自有组件，见 ui-guidelines §4.1 例外记录）。
 *
 * 一行 = 一张独立卡（LargeShape、surfaceBright）：左 48dp 命中圆形勾选、文字最多两行、
 * 第二行是提醒；未完成行可滑动（右滑完成 / 左滑删除），已完成行只左滑删除。
 * 点按行为按状态分叉：未完成进编辑弹窗，已完成原地展开 / 收起全文（不改文案）。
 *
 * 页面级例外：行组件不走 LunoGroupItem，底色由 surfaceBright 卡自己提供；
 * 滑动与「点勾即时完成 + 撤销条」是需要记录的交互分叉。
 */

/** 未完成行文案行数上限：两行足够扫读，再长进编辑弹窗看全。 */
private const val PendingMaxLines = 2

/**
 * 滑动结算阈值（占卡宽比例）：拖满这个行程才执行。
 *
 * `SwipeToDismissBox` 的 `positionalThreshold` 是**双向共用**一个比例，完成 / 删除没法分开设；
 * 默认 50% 时纵向滚动的斜向抖动就够，这里收紧到 65%。
 * 注意它只管「拖够距离」这一条结算路径：`AnchoredDraggable` 的 fling 是另一条，
 * 松手速度够快时不看距离直接结算，这部分调不了。
 */
private const val SwipeThresholdFraction = 0.65f

@Composable
fun TodoCard(
    todo: TodoItem,
    nowMillis: Long,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    expanded: Boolean = false,
    onToggleExpanded: (() -> Unit)? = null,
) {
    if (onComplete != null || onDelete != null) {
        SwipeableTodoCard(
            todo = todo,
            nowMillis = nowMillis,
            onToggle = onToggle,
            onEdit = onEdit,
            onComplete = onComplete,
            onDelete = onDelete,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            modifier = modifier,
        )
    } else {
        TodoCardBody(
            todo = todo,
            nowMillis = nowMillis,
            onToggle = onToggle,
            onEdit = onEdit,
            onComplete = null,
            onDelete = null,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            modifier = modifier,
        )
    }
}

@Composable
private fun SwipeableTodoCard(
    todo: TodoItem,
    nowMillis: Long,
    onToggle: () -> Unit,
    onEdit: (() -> Unit)?,
    onComplete: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        // 阈值只调这里：SwipeToDismissBox 的 positionalThreshold 是双向共用一个比例，
        // 完成 / 删除没法分开设。50% 时纵向滚动的斜向抖动就够，取 65% 收紧行程。
        positionalThreshold = { total -> total * SwipeThresholdFraction },
    )
    // SwipeToDismissBoxState 走 rememberSaveable：撤销删除后同一条按同一个 key 回来，
    // 恢复出来的状态是「已滑走」，onDismiss 会立刻再结算一次 → 删 → 撤销 → 再删的死循环。
    // 组合时已 dismissed 就是恢复出来的：重置回原位，并跳过这**第一次**回调（真滑动手势只可能晚于它）。
    val initialSettled by remember(todo.id) { mutableStateOf(state.settledValue) }
    var dismissedCount by remember(todo.id) { mutableStateOf(0) }
    LaunchedEffect(state) {
        if (state.settledValue != SwipeToDismissBoxValue.Settled) state.reset()
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = onComplete != null,
        enableDismissFromEndToStart = onDelete != null,
        onDismiss = { direction ->
            // 恢复出来的 dismissed 状态会先结算一次，且真滑动手势只可能晚于它：
            // 组合时已 dismissed 的话跳过这第一次回调，否则撤销删除会形成「删→撤销→再删」的死循环。
            val spurious = initialSettled != SwipeToDismissBoxValue.Settled && dismissedCount++ == 0
            if (!spurious) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> onComplete?.let {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                    SwipeToDismissBoxValue.EndToStart -> onDelete?.let {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                    SwipeToDismissBoxValue.Settled -> {}
                }
            }
        },
        backgroundContent = { SwipeBackdrop(state) },
    ) {
        TodoCardBody(
            todo = todo,
            nowMillis = nowMillis,
            onToggle = onToggle,
            onEdit = onEdit,
            onComplete = onComplete,
            onDelete = onDelete,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
        )
    }
}

/**
 * 滑动背景：一整块随方向变色的圆角层（右滑完成 = 主色容器，左滑删除 = error 容器），
 * 图标随行程淡入放大；不再用左右两块半宽色硬拼。
 */
@Composable
private fun SwipeBackdrop(state: SwipeToDismissBoxState) {
    val scheme = MaterialTheme.colorScheme
    val completing = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    val progress = abs(state.progress).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(LargeShape)
            .background(
                if (completing) scheme.primaryContainer else scheme.errorContainer,
            ),
        contentAlignment = if (completing) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = if (completing) Icons.Default.Check else Icons.Default.Delete,
            contentDescription = stringResource(
                if (completing) R.string.todo_swipe_complete else R.string.todo_swipe_delete,
            ),
            tint = if (completing) scheme.onPrimaryContainer else scheme.onErrorContainer,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .size(22.dp)
                .graphicsLayer {
                    alpha = 0.35f + 0.65f * progress
                    val scale = 0.8f + 0.2f * progress
                    scaleX = scale
                    scaleY = scale
                        },
        )
    }
}

/** 卡本体（滑动与非滑动共用）：勾选 + 文字（最多两行）+ 提醒 / 完成时间。 */
@Composable
private fun TodoCardBody(
    todo: TodoItem,
    nowMillis: Long,
    onToggle: () -> Unit,
    onEdit: (() -> Unit)?,
    onComplete: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clickLabel = when {
        onToggleExpanded != null ->
            stringResource(if (expanded) R.string.todo_done_collapse else R.string.todo_done_expand)
        else -> null
    }
    // 读屏的替代入口：完成 / 恢复 / 删除不必依赖滑动或展开。
    val completeLabel = stringResource(R.string.todo_action_complete)
    val uncompleteLabel = stringResource(R.string.todo_action_uncomplete)
    val deleteLabel = stringResource(R.string.todo_delete)
    val actions = buildList {
        if (todo.done) {
            add(CustomAccessibilityAction(uncompleteLabel) { onToggle(); true })
        } else if (onComplete != null) {
            add(CustomAccessibilityAction(completeLabel) { onComplete(); true })
        }
        if (onDelete != null) {
            add(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
        }
    }
    val clickModifier = when {
        onEdit != null -> Modifier.pressClickable(onClick = onEdit)
        onToggleExpanded != null -> Modifier.pressClickable(onClickLabel = clickLabel, onClick = onToggleExpanded)
        else -> Modifier
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LargeShape,
        color = cardSurfaceColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (todo.done) MinInteractiveSize else 56.dp)
                .then(clickModifier)
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                .semantics { customActions = actions },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TodoCheckToggle(
                done = todo.done,
                text = todo.text,
                onToggle = onToggle,
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = todo.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (todo.done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                    maxLines = when {
                        !todo.done -> PendingMaxLines
                        expanded -> Int.MAX_VALUE
                        else -> 1
                    },
                    overflow = TextOverflow.Ellipsis,
                )
                if (!todo.done) {
                    todo.reminder?.let { reminder ->
                        ReminderLine(reminder = reminder, nowMillis = nowMillis)
                    }
                } else if (expanded) {
                    todo.completedAt?.let { completedAt ->
                        Text(
                            text = stringResource(
                                R.string.todo_done_completed_at,
                                TodoReminderFormat.completedAt(context, completedAt),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 行内提醒（第二行）：闹钟 + 文案，按紧迫度着色。过期补「已过期 ·」，
 * 与通知正文共用同一套格式，列表和通知不会对不上。
 */
@Composable
private fun ReminderLine(
    reminder: TodoReminder,
    nowMillis: Long,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val label = TodoReminderFormat.label(context, reminder, nowMillis)
    val tone = reminder.tone(nowMillis, ZoneId.systemDefault())
    val color = when (tone) {
        ReminderTone.Overdue -> scheme.error
        ReminderTone.Soon -> scheme.primary
        ReminderTone.Neutral -> scheme.onSurfaceVariant
    }
    val text = if (tone == ReminderTone.Overdue) {
        stringResource(R.string.todo_reminder_overdue_body, label)
    } else {
        label
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 紧迫度 / 已完成分区头：标题 + 计数徽标（>1 才显示，与全局组头徽标 [SectionBadge] 一致）
 * + 可选展开箭头。标题左缩进 [PageGutter]，与全局组标题（`LunoGroup`）对齐；
 * 可点击时整行只做展开 / 收起；吸顶背景用页面底色，滚动时内容从下面穿过。
 */
@Composable
fun TodoSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    onClick: (() -> Unit)? = null,
    expanded: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionSubtle),
        label = "todoSectionChevron",
    )
    val toggleLabel = if (onClick != null) {
        stringResource(if (expanded) R.string.todo_group_collapse else R.string.todo_group_expand)
    } else {
        null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 底色包住上留白：吸顶时是一整块实心，不会漏出下面滚过的行。
            .background(pageSurfaceColor())
            .padding(top = 8.dp, start = PageGutter, end = PageGutter)
            .heightIn(min = if (onClick != null) MinInteractiveSize else 32.dp)
            .then(if (onClick != null) Modifier.pressClickable(onClickLabel = toggleLabel, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = if (error) scheme.error else scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count > 1) {
            // 计数徽标与全局组头一致（PP-OCRv6「已就绪」同款：胶囊 + primaryContainer）。
            SectionBadge(text = count.toString())
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}
