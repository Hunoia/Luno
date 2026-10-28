package hunoia.luno.runtime.condition

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.VariableCompareOp
import hunoia.luno.config.model.WhenGroup

/** 间隔触发键：规则 id + 分钟数，允许同一规则里多个不同间隔各自到期。 */
fun intervalDueKey(ruleId: String, item: ConditionItem): String? =
    item.intervalMinutes?.let { "$ruleId:$it" }

fun inTimeWindow(minuteOfDay: Int, start: Int?, end: Int?): Boolean {
    val s = start ?: return false
    val e = end ?: return false
    return if (s <= e) minuteOfDay in s..e else minuteOfDay >= s || minuteOfDay < e
}

fun rangeContains(actual: Int?, min: Int?, max: Int?): Boolean {
    val value = actual ?: return false
    if (value < 0) return false
    return (min == null || value >= min) && (max == null || value <= max)
}

fun AutomationRule.matches(ctx: ConditionContext, ruleId: String? = null): Boolean =
    condition.matches(ctx, ruleId)

fun WhenGroup.matches(ctx: ConditionContext, ruleId: String? = null): Boolean {
    if (items.isEmpty()) return false
    return when (mode) {
        BooleanMode.AND -> items.all { it.matches(ctx, ruleId) }
        BooleanMode.OR -> items.any { it.matches(ctx, ruleId) }
    }
}

fun ConditionItem.matches(ctx: ConditionContext, ruleId: String? = null): Boolean =
    ConditionRegistry.matches(this, ctx, ruleId)

/** 条件变量比较：数值运算优先按 double，文本运算保留原大小写。 */
fun compareValues(left: String, op: String, right: String): Boolean {
    val ln = left.trim().toDoubleOrNull()
    val rn = right.trim().toDoubleOrNull()
    val numeric = ln != null && rn != null
    return when (op) {
        VariableCompareOp.EQUALS -> if (numeric) ln == rn else left == right
        VariableCompareOp.NOT_EQUALS -> !(if (numeric) ln == rn else left == right)
        VariableCompareOp.GREATER -> ln != null && rn != null && ln > rn
        VariableCompareOp.GREATER_OR_EQUALS -> ln != null && rn != null && ln >= rn
        VariableCompareOp.LESS -> ln != null && rn != null && ln < rn
        VariableCompareOp.LESS_OR_EQUALS -> ln != null && rn != null && ln <= rn
        VariableCompareOp.CONTAINS -> left.contains(right)
        VariableCompareOp.STARTS_WITH -> left.startsWith(right)
        VariableCompareOp.ENDS_WITH -> left.endsWith(right)
        VariableCompareOp.IS_EMPTY -> left.isEmpty()
        VariableCompareOp.IS_NOT_EMPTY -> left.isNotEmpty()
        else -> false
    }
}

data class TimeWindow(
    val startMinute: Int,
    val endMinute: Int,
    /** Calendar 常量；空=每天。 */
    val weekdays: Set<Int> = emptySet(),
) {
    fun matches(minuteOfDay: Int, dayOfWeek: Int): Boolean =
        inTimeWindow(minuteOfDay, startMinute, endMinute) &&
            (weekdays.isEmpty() || dayOfWeek in weekdays)
}

fun WhenGroup.timeRanges(): List<TimeWindow> = items.flatMap { item ->
    if (item.type == ConditionType.GROUP) {
        item.group?.timeRanges().orEmpty()
    } else {
        listOfNotNull(ConditionRegistry.timeWindow(item))
    }
}

fun WhenGroup.hasTimeRange(): Boolean = timeRanges().isNotEmpty()

fun WhenGroup.hasVolumeCondition(): Boolean = items.any { item ->
    if (item.type == ConditionType.GROUP) {
        item.group?.hasVolumeCondition() == true
    } else {
        ConditionRegistry.affectsVolume(item.type)
    }
}

/** 音乐条件：决定播放回调的注册/注销（事件驱动，不注册零开销）。 */
fun WhenGroup.hasMusicCondition(): Boolean = items.any { item ->
    if (item.type == ConditionType.GROUP) {
        item.group?.hasMusicCondition() == true
    } else {
        ConditionRegistry.affectsMusic(item.type)
    }
}

/** 手电筒条件：决定 torch 回调的注册/注销。 */
fun WhenGroup.hasTorchCondition(): Boolean = items.any { item ->
    if (item.type == ConditionType.GROUP) {
        item.group?.hasTorchCondition() == true
    } else {
        ConditionRegistry.affectsTorch(item.type)
    }
}

/** 定时触发项（递归进 GROUP；星期由 ticker 保证，此处只收分钟表）。 */
fun WhenGroup.scheduledItems(): List<ConditionItem> = items.flatMap { item ->
    if (item.type == ConditionType.GROUP) {
        item.group?.scheduledItems().orEmpty()
    } else {
        listOfNotNull(item.takeIf { ConditionRegistry.isScheduled(it) })
    }
}

fun WhenGroup.hasScheduledTime(): Boolean = scheduledItems().isNotEmpty()

/** 间隔触发项（递归进 GROUP）：ticker 用 rule.id + 分钟数维护 lastFiredAt。 */
fun WhenGroup.intervalItems(): List<ConditionItem> = items.flatMap { item ->
    if (item.type == ConditionType.GROUP) {
        item.group?.intervalItems().orEmpty()
    } else {
        listOfNotNull(item.takeIf { ConditionRegistry.intervalMinutes(it) != null })
    }
}

fun WhenGroup.hasIntervalCondition(): Boolean = intervalItems().isNotEmpty()

fun WhenGroup.hasLeaf(): Boolean = items.any { item ->
    if (item.type == ConditionType.GROUP) item.group?.hasLeaf() == true else true
}
