package hunoia.luno.action.dispatcher

import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * StoredAction 读取迁移序列化器：新形态直接解，legacy {value, data} 经
 * [LegacyActionMapper] 转换后读，未知 value 落 `internal.none`。
 * 写入永远是新形态。这是单轨下唯一的兼容层。
 */
object MigratingStoredActionSerializer : KSerializer<StoredAction> {
    private val fallback = StoredAction.serializer()
    override val descriptor: SerialDescriptor = fallback.descriptor

    override fun deserialize(decoder: Decoder): StoredAction {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return fallback.deserialize(decoder)
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        if (obj.containsKey("typeId")) {
            return jsonDecoder.json.decodeFromJsonElement(fallback, obj)
        }
        val value = obj["value"]?.jsonPrimitive?.contentOrNull
            ?: return StoredAction.of("internal.none")
        val data = obj["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return LegacyActionMapper.mapRaw(value, data) ?: StoredAction.of("internal.none")
    }

    override fun serialize(encoder: Encoder, value: StoredAction) {
        fallback.serialize(encoder, value)
    }
}
