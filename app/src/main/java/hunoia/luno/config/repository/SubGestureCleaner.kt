package hunoia.luno.config.repository

import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.action.model.withLongPressVariant
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object SubGestureCleaner {

    suspend fun cleanSubGestureReferences(
        deletedId: String,
        shouldRemove: (StoredAction) -> Boolean,
    ) {
        ConfigProvider.updateGestureButtons { buttons ->
            buttons.map { it.cleanActions(shouldRemove) }
        }
        ConfigProvider.updateSubGestureSettings { settings ->
            settings.copy(
                subGestures = settings.subGestures.map { it.cleanActions(shouldRemove) }
            )
        }
    }

    fun isSubGestureAction(action: StoredAction): Boolean =
        action.typeId == "internal.subGesture"

    fun matchesDeletedSubGesture(action: StoredAction, deletedId: String): Boolean {
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
    tapActions = tapActions.cleanList(shouldRemove),
    doubleTapActions = doubleTapActions.cleanList(shouldRemove),
    longPressActions = longPressActions.cleanList(shouldRemove),
)

fun SubGesture.cleanActions(
    shouldRemove: (StoredAction) -> Boolean,
): SubGesture = copy(
    slideActions = slideActions.cleanActions(shouldRemove),
    slideHoldActions = slideHoldActions.cleanActions(shouldRemove),
    longSlideActions = longSlideActions.cleanActions(shouldRemove),
    longSlideHoldActions = longSlideHoldActions.cleanActions(shouldRemove),
)

fun DirectionActions.cleanActions(shouldRemove: (StoredAction) -> Boolean): DirectionActions =
    copy(actions = actions.mapValues { (_, list) -> list.cleanList(shouldRemove) })

/**
 * 过滤本体，顺手清掉面板长按变体里的失效引用。
 *
 * 变体（__longPress）是嵌在 params 里的独立 StoredAction，只滤本体会留下悬空引用：
 * 删条目后短按正常、长按静默无反应（分发器只记 DEBUG 日志）。
 */
fun List<StoredAction>.cleanList(shouldRemove: (StoredAction) -> Boolean): List<StoredAction> =
    mapNotNull { action ->
        if (shouldRemove(action)) return@mapNotNull null
        val variant = action.longPressVariant() ?: return@mapNotNull action
        val kept = variant.takeUnless(shouldRemove) ?: return@mapNotNull action
        action.withLongPressVariant(kept)
    }
