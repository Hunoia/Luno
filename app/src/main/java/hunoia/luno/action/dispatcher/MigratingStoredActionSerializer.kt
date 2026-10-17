package hunoia.luno.action.dispatcher

import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * StoredAction 读取迁移序列化器。依次判定四种历史形态：
 *
 * 1. `{"typeId":...}` —— 当前形态，直接解。
 * 2. `[{...}, {...}]` —— **多动作列表形态**。动作槽单值化后旧数据仍是数组，
 *    取首个元素，其余丢弃（见 docs/action-select-single.md §3.3）。
 * 3. `{"value":...,"data":...}` —— legacy 形态，经 [LegacyActionMapper] 转换。
 * 4. 其余 —— 落 `internal.none`。
 *
 * `descriptor` 声明为 `.nullable`，使它可以直接挂在 `StoredAction?` 属性上：
 * kotlinx 的 `decodeNullableSerializableValue` 会先问 decoder 要不要 null，
 * 拿到 null 就根本不会进 [deserialize]。写入同理，null 字段直接不输出。
 *
 * 数组分支是单值化改造能安全落地的**前提**：不加它，旧 DataStore 里的数组
 * 会喂给一个期望对象的解码器，直接抛 `JsonDecodingException`，App 起不来。
 */
object MigratingStoredActionSerializer : KSerializer<StoredAction> {
    private val fallback = StoredAction.serializer()

    override val descriptor: SerialDescriptor = fallback.descriptor.nullable

    override fun deserialize(decoder: Decoder): StoredAction {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return fallback.deserialize(decoder)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> decodeFromArray(jsonDecoder, element)
            is JsonObject -> decodeFromObject(jsonDecoder, element)
            else -> StoredAction.of("internal.none")
        }
    }

    /** 旧多动作列表：取首个元素，其余丢弃。空数组落 `internal.none`。 */
    private fun decodeFromArray(jsonDecoder: JsonDecoder, array: JsonArray): StoredAction {
        val first = array.firstOrNull() ?: return StoredAction.of("internal.none")
        val obj = first as? JsonObject ?: return StoredAction.of("internal.none")
        return decodeFromObject(jsonDecoder, obj)
    }

    private fun decodeFromObject(jsonDecoder: JsonDecoder, obj: JsonObject): StoredAction {
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