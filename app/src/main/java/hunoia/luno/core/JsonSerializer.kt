package hunoia.luno.core

import androidx.annotation.Keep
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Keep
object JsonSerializer {

    val globalJson = Json {
        ignoreUnknownKeys = true
    }

    inline fun <reified T> encodeToString(value: T): String {
        return globalJson.encodeToString(value)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return globalJson.decodeFromString(string)
    }
}