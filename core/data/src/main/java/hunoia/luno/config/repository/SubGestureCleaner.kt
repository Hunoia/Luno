package hunoia.luno.config.repository

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.longPressVariant
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.GlobalGestureTrigger
import hunoia.luno.config.model.SubGesture
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
        // 全局手势（识别区触发）也是同一套动作槽：不摘掉就是点了没反应的触发。
        ConfigProvider.updateGlobalGesture { settings ->
            settings.cleanActions(shouldRemove)
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

/** 清全局手势触发条目里的引用：单动作槽与方向表各自滤。 */
fun GlobalGestureTrigger.cleanActions(
    shouldRemove: (StoredAction) -> Boolean,
): GlobalGestureTrigger = copy(
    action = action.cleanOne(shouldRemove),
    directionActions = directionActions.cleanActions(shouldRemove),
)

/** 清全部识别区里的引用。 */
fun GlobalGestureSettings.cleanActions(
    shouldRemove: (StoredAction) -> Boolean,
): GlobalGestureSettings = copy(
    zones = zones.map { zone ->
        zone.copy(triggers = zone.triggers.map { it.cleanActions(shouldRemove) })
    },
)

/**
 * 过滤本体，顺手清掉面板长按变体里的失效引用。
 *
 * 变体（__longPress）是嵌在 params 里的独立 StoredAction，只滤本体会留下悬空引用：
 * 删条目后短按正常、长按静默无反应（分发器只记 DEBUG 日志）。
 * 本体命中→整个槽置空；只有变体命中→只摘 `__longPress` 键，本体保留。
 *
 * 单值语义：命中即整个槽置空（返回 null = 删键），不是从列表里挑剩下的。
 */
fun StoredAction?.cleanOne(shouldRemove: (StoredAction) -> Boolean): StoredAction? {
    val action = this ?: return null
    if (shouldRemove(action)) return null
    val variant = action.longPressVariant() ?: return action
    if (!shouldRemove(variant)) return action
    return action.withoutVariantKey()
}
