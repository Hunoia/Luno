package hunoia.luno.core.domain

import androidx.annotation.Keep
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 全局 JSON 编解码：契约层与数据层共用同一套 Json 配置。
 * 放在 domain 而不是 core.common，避免 domain 的扩展函数反向依赖 Android 基础库。
 */
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
