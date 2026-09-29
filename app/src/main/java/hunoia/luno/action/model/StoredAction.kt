package hunoia.luno.action.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
