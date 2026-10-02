package hunoia.luno.action.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

@Serializable
@Keep
data class NewActionLibraryEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val typeId: String,
    val params: JsonObject = JsonObject(emptyMap()),
    val createdAt: Long = System.currentTimeMillis(),
    val iconKey: String? = null,
) {
    val storedAction: StoredAction get() = StoredAction(typeId, params)

    companion object {
        fun create(typeId: String, name: String = ""): NewActionLibraryEntry {
            return NewActionLibraryEntry(typeId = typeId, name = name)
        }
    }
}

@Serializable
@Keep
data class NewActionLibrarySettings(
    val entries: List<NewActionLibraryEntry> = emptyList()
)

fun NewActionLibraryEntry.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    return name.contains(needle, ignoreCase = true) ||
        typeId.contains(needle, ignoreCase = true) ||
        params.values.any { (it as? JsonPrimitive)?.contentOrNull?.contains(needle, ignoreCase = true) == true }
}
