package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 方向动作：**键是扇区序号（0..count-1）**，不再是四向枚举。
 *
 * 方向数量与每个扇区的实际朝向由角度布局（`GestureButtonAngle` / `SubGestureAngle`）决定，
 * 动作只认「第几个扇区」；名称与图标在展示层由扇区中心角派生。
 *
 * 没有动作就是没有这个 key（值类型不可空），清空即 [withAction] 删键。
 */
@Serializable(with = DirectionActionsSerializer::class)
@Keep
data class DirectionActions(
    val actions: Map<Int, StoredAction> = emptyMap()
) {
    fun actionBy(slot: Int): StoredAction? = actions[slot]

    fun withAction(slot: Int, action: StoredAction?): DirectionActions {
        if (action == null) return copy(actions = actions - slot)
        return copy(actions = actions + (slot to action))
    }

    /** 按「旧槽 → 新槽」映射迁移动作（镜像、改方向数时用）；映射缺失即丢弃。 */
    fun remapSlots(newSlotOfOld: (Int) -> Int?): DirectionActions {
        val next = LinkedHashMap<Int, StoredAction>()
        actions.forEach { (oldSlot, action) ->
            newSlotOfOld(oldSlot)?.let { next[it] = action }
        }
        return copy(actions = next)
    }
}

@Serializable
private class DirectionActionsSurrogate(val actions: Map<String, StoredAction> = emptyMap())

/**
 * 以「数字字符串」写盘，读取时兼容旧版四向枚举名。
 *
 * 旧配置的键是 `Up/Left/Down/Right`（默认四扇区顺序），映射到槽位 0..3；
 * 这不是迁移框架，只是一层读取兼容：不认的键直接丢弃。
 */
object DirectionActionsSerializer : KSerializer<DirectionActions> {
    private val surrogate = DirectionActionsSurrogate.serializer()

    override val descriptor: SerialDescriptor = surrogate.descriptor

    override fun serialize(encoder: Encoder, value: DirectionActions) {
        encoder.encodeSerializableValue(
            surrogate,
            DirectionActionsSurrogate(value.actions.mapKeys { it.key.toString() }),
        )
    }

    override fun deserialize(decoder: Decoder): DirectionActions {
        val decoded = decoder.decodeSerializableValue(surrogate)
        val actions = LinkedHashMap<Int, StoredAction>()
        decoded.actions.forEach { (key, action) ->
            val slot = key.toIntOrNull()?.takeIf { it in 0..7 } ?: LegacyDirectionSlots[key]
            if (slot != null) actions[slot] = action
        }
        return DirectionActions(actions)
    }
}

/** 旧四向枚举名 → 默认四扇区顺序（Up, Left, Down, Right）里的槽位。 */
private val LegacyDirectionSlots = mapOf(
    "Up" to 0,
    "Left" to 1,
    "Down" to 2,
    "Right" to 3,
)
