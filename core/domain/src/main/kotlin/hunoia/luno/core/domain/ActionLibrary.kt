package hunoia.luno.core.domain

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Serializable
@Keep
data class ActionLibraryEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val typeId: String,
    val params: JsonObject = JsonObject(emptyMap()),
    val createdAt: Long = System.currentTimeMillis(),
    val iconKey: String? = null,
) {
    val storedAction: StoredAction get() = StoredAction(typeId, params)

    companion object {
        fun create(typeId: String, name: String = ""): ActionLibraryEntry {
            return ActionLibraryEntry(typeId = typeId, name = name)
        }
    }
}

@Serializable
@Keep
data class ActionLibrarySettings(
    val entries: List<ActionLibraryEntry> = emptyList(),
)
