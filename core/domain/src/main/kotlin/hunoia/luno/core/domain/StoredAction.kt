package hunoia.luno.core.domain

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 唯一持久化动作形态：`typeId + params`。
 *
 * 契约层拥有它，配置（core:data）、执行口（feature:execute）、动作库（feature:actions）
 * 都只引用这里，不各自定义。
 */
@Serializable
@Keep
data class StoredAction(
    val typeId: String,
    val params: JsonObject = JsonObject(emptyMap()),
) {
    companion object {
        fun of(typeId: String, vararg params: Pair<String, String?>): StoredAction {
            val json = buildJsonObject {
                for ((key, value) in params) {
                    if (value != null) put(key, JsonPrimitive(value))
                }
            }
            return StoredAction(typeId, json)
        }

        fun of(typeId: String): StoredAction = StoredAction(typeId)
    }
}
