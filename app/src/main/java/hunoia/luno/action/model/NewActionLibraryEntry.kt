package hunoia.luno.action.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Serializable
@Keep
data class NewActionLibraryEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val typeId: String,
    val params: JsonObject = JsonObject(emptyMap()),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val storedAction: StoredAction get() = StoredAction(typeId, params)

    companion object {
        fun create(typeId: String, name: String = ""): NewActionLibraryEntry {
            return NewActionLibraryEntry(typeId = typeId, name = name)
        }

        fun duplicate(entry: NewActionLibraryEntry, name: String): NewActionLibraryEntry {
            return entry.copy(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
            )
        }
    }
}

@Serializable
@Keep
data class NewActionLibrarySettings(
    val entries: List<NewActionLibraryEntry> = emptyList()
)
