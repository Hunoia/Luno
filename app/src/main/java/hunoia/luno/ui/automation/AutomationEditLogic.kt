package hunoia.luno.ui.automation

import android.content.Context
import hunoia.luno.R
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleEffectType
import hunoia.luno.config.model.RulePriority
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.ui.actionselect.selectedItemLabel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 自动化规则编辑器的纯逻辑：条件树/步骤树的增删、摘要、枚举文案与节流校验。
 *
 * 从 [AutomationEditScreen] 抽出，屏幕文件只留 Compose 组合；这些函数不依赖 Composition，
 * 可单独阅读/测试。同包内可直接调用，无需 import。
 */

/**
 * 按路径向条件组追加条件：path 为组下标链，空=顶层。非法路径原样返回。
 */
internal fun appendConditionAt(group: WhenGroup, path: List<Int>, item: ConditionItem): WhenGroup {
    if (path.isEmpty()) return group.copy(items = group.items + item)
    val head = path.first()
    if (head !in group.items.indices) return group
    val target = group.items[head]
    val sub = target.group ?: return group
    val items = group.items.toMutableList()
    items[head] = target.copy(group = appendConditionAt(sub, path.drop(1), item))
    return group.copy(items = items)
}

/**
 * 按路径删除条件：path 为条件组内下标链。非法路径原样返回。
 */
internal fun removeConditionAt(group: WhenGroup, path: List<Int>): WhenGroup {
    if (path.isEmpty()) return group
    val head = path.first()
    if (head !in group.items.indices) return group
    val items = group.items.toMutableList()
    if (path.size == 1) {
        items.removeAt(head)
        return group.copy(items = items)
    }
    val target = items[head]
    val sub = target.group ?: return group
    items[head] = target.copy(group = removeConditionAt(sub, path.drop(1)))
    return group.copy(items = items)
}

/**
 * 按路径删除步骤：path 为 appendStep 同款字符串路径（"2" / "2.then.0"）。
 * 非法路径原样返回。
 */
internal fun removeStepAt(effect: RuleEffect, path: String): RuleEffect {
    val tokens = path.split(".").filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return effect
    fun removeFromList(steps: List<RuleStep>, index: Int): List<RuleStep> =
        steps.toMutableList().also { if (index in it.indices) it.removeAt(index) }
    if (tokens.size == 1) {
        val index = tokens[0].toIntOrNull() ?: return effect
        return effect.copy(steps = removeFromList(effect.steps, index))
    }
    val index = tokens.last().toIntOrNull() ?: return effect
    val branch = tokens[tokens.size - 2]
    if (branch != "then" && branch != "else") return effect
    val parentPath = tokens.dropLast(2).joinToString(".")
    if (parentPath.isEmpty()) return effect
    return effect.withStepAt(parentPath) { step ->
        if (step.kind != RuleStepKind.IF) step
        else if (branch == "then") step.copy(thenSteps = removeFromList(step.thenSteps, index))
        else step.copy(elseSteps = removeFromList(step.elseSteps, index))
    }
}

/**
 * 向 prefix 指向的列表追加步骤，返回（新步骤路径，更新后的 effect）。
 * prefix：""=顶层，"2.then." / "2.then.0.else."=分支。非法返回 null。
 */
internal fun appendStep(
    effect: RuleEffect,
    prefix: String,
    created: RuleStep,
): Pair<String, RuleEffect>? {
    if (prefix.isEmpty()) {
        val path = "${effect.steps.size}"
        return path to effect.copy(steps = effect.steps + created)
    }
    val tokens = prefix.split(".").filter { it.isNotEmpty() }
    if (tokens.size < 2) return null
    val branch = tokens.last()
    if (branch != "then" && branch != "else") return null
    val parentPath = tokens.dropLast(1).joinToString(".")
    val parent = effect.stepAt(parentPath) ?: return null
    if (parent.kind != RuleStepKind.IF) return null
    val childIndex = if (branch == "then") parent.thenSteps.size else parent.elseSteps.size
    val updated = effect.withStepAt(parentPath) { step ->
        if (branch == "then") step.copy(thenSteps = step.thenSteps + created)
        else step.copy(elseSteps = step.elseSteps + created)
    }
    return "$parentPath.$branch.$childIndex" to updated
}

internal data class ButtonsToggle(val stored: StoredAction, val hide: Boolean)

/** 步骤是否为触钮显隐动作（内联或库引用），是则带出 stored 供配对恢复用。 */
internal fun stepButtonsVisibility(
    library: NewActionLibrarySettings?,
    step: RuleStep,
): ButtonsToggle? {
    if (step.kind != RuleStepKind.ACTION) return null
    val stored = step.action
        ?: library?.entries?.find { it.id == step.entryId }?.storedAction
    if (stored?.typeId != "internal.buttonsVisibility") return null
    val visible = stored.params["visible"]
        ?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
    return ButtonsToggle(stored, hide = !visible)
}

/** 配对恢复规则：同条件/fireOn EXIT/显示步骤，平铺两条（id 后缀 :show）。 */
internal fun buildShowPairRule(
    context: Context,
    parent: AutomationRule,
    library: NewActionLibrarySettings?,
    hideStep: RuleStep,
): AutomationRule {
    val hide = stepButtonsVisibility(library, hideStep)
    val showStored = StoredAction(
        typeId = "internal.buttonsVisibility",
        params = JsonObject(
            (hide?.stored?.params ?: emptyMap()) + ("visible" to JsonPrimitive(true)),
        ),
    )
    val baseName = parent.name.ifBlank { conditionSummary(context, parent.condition) }
    return AutomationRule(
        id = "${parent.id}:show",
        name = context.getString(R.string.automation_pair_show_name, baseName),
        condition = parent.condition,
        effect = RuleEffect(
            type = RuleEffectType.RUN_ACTION,
            steps = listOf(RuleStep(kind = RuleStepKind.ACTION, action = showStored)),
        ),
        fireOn = FireMode.EXIT,
    )
}

internal fun stepSummary(
    context: Context,
    library: NewActionLibrarySettings?,
    step: RuleStep,
    rules: List<AutomationRule> = emptyList(),
): String = when (step.kind) {
    RuleStepKind.DELAY -> context.getString(R.string.step_delay_summary, step.delayMs / 1000)
    RuleStepKind.IF -> conditionSummary(context, step.condition) + " · " +
        context.getString(R.string.step_if_counts, step.thenSteps.size, step.elseSteps.size)
    else -> {
        val label = step.action?.let {
            context.selectedItemLabel(it, emptyList(), library?.entries ?: emptyList(), rules)
        }.orEmpty()
        label.ifBlank { context.getString(R.string.step_empty_action) }
    }
}

internal fun groupModeLabel(mode: BooleanMode): Int = when (mode) {
    BooleanMode.AND -> R.string.logic_all
    BooleanMode.OR -> R.string.logic_any
}

internal fun nextGroupMode(mode: BooleanMode): BooleanMode = when (mode) {
    BooleanMode.AND -> BooleanMode.OR
    BooleanMode.OR -> BooleanMode.AND
}

internal fun priorityLabel(priority: Int): Int = when (priority) {
    RulePriority.HIGH -> R.string.priority_high
    RulePriority.LOW -> R.string.priority_low
    else -> R.string.priority_medium
}

/** 通用节流校验：负数、半填（次数/秒数只填一边）、窗口不足 1s 判无效。 */
internal fun isInvalidThrottle(throttle: ThrottlePolicy): Boolean {
    if (throttle.cooldownMs < 0 || throttle.maxCount < 0 || throttle.windowMs < 0) return true
    val countSet = throttle.maxCount > 0
    val windowSet = throttle.windowMs > 0
    if (countSet != windowSet) return true
    if (windowSet && throttle.windowMs < 1000L) return true
    return false
}

/** 填了即生效：冷却/窗口各看各的值派生 kind（双填 BOTH，全空 OFF）。 */
internal fun deriveThrottleKind(cooldownMs: Long, maxCount: Int, windowMs: Long): String {
    val cooldownOn = cooldownMs > 0
    val windowOn = maxCount > 0 && windowMs > 0
    return when {
        cooldownOn && windowOn -> ThrottleKind.BOTH
        cooldownOn -> ThrottleKind.COOLDOWN
        windowOn -> ThrottleKind.WINDOW
        else -> ThrottleKind.OFF
    }
}

internal fun conditionUsesNotification(group: WhenGroup): Boolean = group.items.any { item ->
    when (item.type) {
        ConditionType.NOTIFICATION_POSTED -> true
        ConditionType.GROUP -> item.group?.let { conditionUsesNotification(it) } == true
        else -> false
    }
}

/** 步骤树（含 IF 分支）是否引用通知条件：决定是否展示通知权限提示行。 */
internal fun RuleEffect.usesNotificationCondition(): Boolean = steps.any { it.usesNotificationCondition() }

private fun RuleStep.usesNotificationCondition(): Boolean =
    conditionUsesNotification(condition) ||
        thenSteps.any { it.usesNotificationCondition() } ||
        elseSteps.any { it.usesNotificationCondition() }

/**
 * 条件是否未配置完整（求值恒 false / 逻辑恒空转的死配置）：保存闸门与条件组错误徽标用。
 * 空 GROUP、空 IF 条件一定无效，一并拦截，避免保存出永不触发的规则。
 */
internal fun WhenGroup.hasIncompleteCondition(): Boolean = items.any { item ->
    when (item.type) {
        ConditionType.FOREGROUND_APP,
        ConditionType.APP_CHANGED_TO -> item.packageNames.isEmpty()

        ConditionType.SCREEN_STATE -> item.screenType == null
        ConditionType.SCREEN_EVENT -> item.screenEvent == null
        ConditionType.TIME_RANGE -> item.startMinute == null || item.endMinute == null
        ConditionType.VOLUME_RANGE -> item.audioStream == null
        ConditionType.NETWORK_TYPE -> item.networkType == null
        ConditionType.CHARGING_CHANGED -> item.charging == null
        ConditionType.KEYBOARD_EVENT -> item.keyboardEvent == null
        ConditionType.SCHEDULED_TIME -> item.scheduledMinutes.isEmpty()
        ConditionType.BLUETOOTH -> item.expectedBluetoothAdapterOn == null &&
            item.expectedBluetoothAudioConnected == null

        ConditionType.GROUP -> item.group == null || item.group.items.isEmpty() ||
            item.group.hasIncompleteCondition()

        else -> false
    }
}

/** 步骤树里是否有空/未配置完整的 IF 条件（含分支递归）。 */
internal fun List<RuleStep>.hasIncompleteStepCondition(): Boolean = any { step ->
    val self = step.kind == RuleStepKind.IF &&
        (step.condition.items.isEmpty() || step.condition.hasIncompleteCondition())
    self || step.thenSteps.hasIncompleteStepCondition() || step.elseSteps.hasIncompleteStepCondition()
}

/**
 * 条件树是否含状态叶（非脉冲、非 MANUAL）：决定规则级"进入/退出"是否可用。
 * 纯事件树发生即触发、没有持续边沿，编辑器隐藏触发方式（引擎侧同样归一化）。
 */
internal fun WhenGroup.hasStateCondition(): Boolean = AutomationCenter.hasStateConditionOf(this)
