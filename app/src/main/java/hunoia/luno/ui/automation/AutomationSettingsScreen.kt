package hunoia.luno.ui.automation

import android.text.format.DateUtils
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.RulePriority
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.ui.actionlibrary.IconPalette
import hunoia.luno.ui.actionlibrary.IconPickerSheet
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import kotlinx.coroutines.launch

/** 停用规则整体降透明度，仍可读、仍可操作。 */
private const val DisabledRuleAlpha = 0.55f

/**
 * 规则列表本体（Home 自动化 Tab 用）。数据自给：读 [AutomationCenter]，
 * 调用方只给滚动 state、padding 与编辑回调。
 */
@Composable
fun AutomationRulesTabContent(
    listState: LazyListState,
    onNavToEdit: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val rules by AutomationCenter.rules
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val events by AutomationCenter.log
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val library by ActionLibrary.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    fun submit(next: List<AutomationRule>) {
        scope.launch {
            try {
                AutomationCenter.updateRules { next }
            } catch (e: Exception) {
                Log.e("AutomationSettings", "submit failed", e)
            }
        }
    }
    val context = LocalContext.current
    // 展示按优先级降序（与引擎执行顺序一致），同档保持原相对顺序。
    val ordered = rules.sortedByDescending { it.priority }
    // 一次算完各规则的最后触发时间：卡片里逐张扫全量日志会 O(规则 × 事件)。
    val lastRunByRule = remember(events) {
        events.asSequence()
            .filter { it.wallTimeMs > 0L }
            .groupBy { it.ruleId }
            .mapValues { (_, list) -> list.maxOf { it.wallTimeMs } }
    }
    val now = System.currentTimeMillis()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        if (rules.isEmpty()) {
            item(key = "empty") {
                EmptyState(message = context.getString(R.string.automation_empty))
            }
        }
        items(ordered, key = { it.id }) { rule ->
            val conditionText = conditionSummary(context, rule.condition)
            // 预览前两步（延时 + 动作），其余用 +N；只看首步会把「延时 0 秒」当成全部效果。
            val effectText = remember(rule, library, rules) {
                val steps = rule.effect.steps
                if (steps.isEmpty()) return@remember ""
                val shown = steps.take(2).joinToString(" · ") { step ->
                    stepSummary(context, library, step, rules)
                }
                if (steps.size > 2) "$shown  +${steps.size - 2}" else shown
            }
            val lastRunMs = lastRunByRule[rule.id]
            RuleCard(
                rule = rule,
                conditionText = conditionText,
                effectText = effectText,
                lastRunText = lastRunMs?.let {
                    DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString()
                } ?: context.getString(R.string.rule_never_run),
                onEnabledChange = { enabled ->
                    submit(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                },
                onPriorityChange = { priority ->
                    submit(rules.map { if (it.id == rule.id) it.copy(priority = priority) else it })
                },
                onIconChange = { iconKey ->
                    submit(rules.map { if (it.id == rule.id) it.copy(iconKey = iconKey) else it })
                },
                onEdit = { onNavToEdit(rule.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: AutomationRule,
    conditionText: String,
    effectText: String,
    lastRunText: String,
    onEnabledChange: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onIconChange: (String?) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIconPicker by remember { mutableStateOf(false) }
    // 自动命名的规则 name 就是条件摘要，标题已经写了条件，摘要里不再重复一遍。
    val title = rule.name.ifBlank { conditionText }
    Card(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        shape = LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(PageGutter)
                .alpha(if (rule.enabled) 1f else DisabledRuleAlpha),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                RuleIcon(
                    rule = rule,
                    onClick = { showIconPicker = true },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!rule.enabled) {
                        Text(
                            text = stringResource(R.string.rule_disabled),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            // 条件 → 效果：一眼能扫出规则在做什么，名字为空时也靠这行辨识。
            val summary = buildString {
                if (conditionText.isNotBlank() && conditionText != title) append(conditionText)
                if (effectText.isNotBlank()) {
                    if (isNotEmpty()) append(" → ")
                    append(effectText)
                }
            }
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                // 优先级三颗小徽章贴左、上次触发贴右：卡片较宽时右下角不会空一块
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PriorityBadges(
                    selected = rule.priority,
                    onSelect = onPriorityChange,
                )
                Text(
                    text = lastRunText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (showIconPicker) {
        IconPickerSheet(
            onDismissRequest = { showIconPicker = false },
            onPick = { iconKey ->
                onIconChange(iconKey)
                showIconPicker = false
            },
            currentKey = rule.iconKey,
            defaultIcon = Icons.Filled.AccountTree,
        )
    }
}

@Composable
private fun RuleIcon(rule: AutomationRule, onClick: () -> Unit) {
    val paletteVector = IconPalette.byKey(rule.iconKey)
    val changeIconLabel = stringResource(R.string.rule_change_icon)
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, LargeShape)
            .clip(LargeShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = changeIconLabel },
        contentAlignment = Alignment.Center,
    ) {
        if (paletteVector != null) {
            Icon(
                imageVector = paletteVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(RowIconSize),
            )
        } else if (!rule.iconKey.isNullOrBlank()) {
            TextIconGlyph(
                text = rule.iconKey,
                size = RowIconSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(RowIconSize),
            )
        }
    }
}

/** 低/中/高三颗小徽章并列，点哪颗哪颗高亮，省掉原来的下拉菜单。 */
@Composable
private fun PriorityBadges(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(RulePriority.LOW, RulePriority.MEDIUM, RulePriority.HIGH).forEach { priority ->
            PriorityBadge(
                priority = priority,
                selected = priority == selected,
                onClick = { onSelect(priority) },
            )
        }
    }
}

@Composable
private fun PriorityBadge(priority: Int, selected: Boolean, onClick: () -> Unit) {
    val container = when (priority) {
        RulePriority.HIGH -> MaterialTheme.colorScheme.primary
        RulePriority.MEDIUM -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (priority) {
        RulePriority.HIGH -> MaterialTheme.colorScheme.onPrimary
        RulePriority.MEDIUM -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = CircleShape
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) container else Color.Transparent)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = shape,
                    )
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(priorityLabel(priority)),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            maxLines = 1,
        )
    }
}
