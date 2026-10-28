package hunoia.luno.config.repository

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.longPressVariant
import hunoia.luno.core.domain.withLongPressVariant
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.SubGesture
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 递归清理步骤树里命中 [predicate] 的动作（含 if 分支）：被清步骤变空跳，其余照跑。 */
fun List<RuleStep>.cleanStepRefs(predicate: (StoredAction) -> Boolean): List<RuleStep> = map { step ->
    step.copy(
        action = step.action?.takeUnless(predicate),
        thenSteps = step.thenSteps.cleanStepRefs(predicate),
        elseSteps = step.elseSteps.cleanStepRefs(predicate),
    )
}

object SubGestureCleaner {

    suspend fun cleanSubGestureReferences(
        deletedId: String,
    ) {
        val shouldRemove: (StoredAction) -> Boolean = { matchesDeletedSubGesture(it, deletedId) }
        ConfigProvider.updateGestureButtons { buttons ->
            buttons.map { it.cleanActions(shouldRemove) }
        }
        ConfigProvider.updateSubGestureSettings { settings ->
            settings.copy(
                subGestures = settings.subGestures.map { it.cleanActions(shouldRemove) }
            )
        }
    }

    private fun isSubGestureAction(action: StoredAction): Boolean =
        action.typeId == "internal.subGesture"

    private fun matchesDeletedSubGesture(action: StoredAction, deletedId: String): Boolean {
        if (!isSubGestureAction(action)) return false
        val id = (action.params["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return id == deletedId
    }
}

fun GestureButton.cleanActions(
    shouldRemove: (StoredAction) -> Boolean,
): GestureButton = copy(
    slideActions = slideActions.cleanActions(shouldRemove),
    slideHoldActions = slideHoldActions.cleanActions(shouldRemove),
    longSlideActions = longSlideActions.cleanActions(shouldRemove),
    longSlideHoldActions = longSlideHoldActions.cleanActions(shouldRemove),
    tapActions = tapActions.cleanOne(shouldRemove),
    doubleTapActions = doubleTapActions.cleanOne(shouldRemove),
    longPressActions = longPressActions.cleanOne(shouldRemove),
)

fun SubGesture.cleanActions(
    shouldRemove: (StoredAction) -> Boolean,
): SubGesture = copy(
    slideActions = slideActions.cleanActions(shouldRemove),
    slideHoldActions = slideHoldActions.cleanActions(shouldRemove),
    longSlideActions = longSlideActions.cleanActions(shouldRemove),
    longSlideHoldActions = longSlideHoldActions.cleanActions(shouldRemove),
)

/** 清洗方向表：被清掉的方向直接删键（值类型不可空，见 [DirectionActions.actions]）。 */
fun DirectionActions.cleanActions(shouldRemove: (StoredAction) -> Boolean): DirectionActions =
    copy(
        actions = actions.mapNotNull { (dir, action) ->
            action.cleanOne(shouldRemove)?.let { dir to it }
        }.toMap()
    )

/**
 * 过滤本体，顺手清掉面板长按变体里的失效引用。
 *
 * 变体（__longPress）是嵌在 params 里的独立 StoredAction，只滤本体会留下悬空引用：
 * 删条目后短按正常、长按静默无反应（分发器只记 DEBUG 日志）。
 *
 * 单值语义：命中即整个槽置空（返回 null = 删键），不是从列表里挑剩下的。
 */
fun StoredAction?.cleanOne(shouldRemove: (StoredAction) -> Boolean): StoredAction? {
    val action = this ?: return null
    if (shouldRemove(action)) return null
    val variant = action.longPressVariant() ?: return action
    val kept = variant.takeUnless(shouldRemove) ?: return action
    return action.withLongPressVariant(kept)
}
