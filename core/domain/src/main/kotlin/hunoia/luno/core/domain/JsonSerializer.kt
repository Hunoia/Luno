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
        // 多出的字段直接丢弃（旧版本写入、新版本不认识的键）。
        //
        // 刻意**不**设 `coerceInputValues`：那会把「非法 / 未知枚举值」也静默换成默认值，
        // 连真正写坏的数据都一起放过，与 architecture §11.2「严格解析、解析失败则该域重置」
        // 冲突。未知枚举值导致整域重置是**已接受**的代价——但必须能恢复，所以落盘侧
        // 在重置前一定会先留 `.bak.<timestamp>`（`DataStore` 的 readFrom）。
        ignoreUnknownKeys = true
    }

    inline fun <reified T> encodeToString(value: T): String {
        return globalJson.encodeToString(value)
    }

    /**
     * 解码。
     *
     * 失败时抛出，由调用方决定是否回默认值 —— 落盘侧（`DataStore`）会先保留
     * `.bak.<timestamp>` 再重置，见 `architecture.md §11.2`。
     *
     * 注意：**删掉一个枚举值**会让含该值的旧数据整份解析失败，进而整个域被重置。
     * 这是正常操作（改设计、合并选项）就会踩到的路径，不是「旧数据兼容」问题。
     * 需要容忍未知枚举值的字段，应给该字段写一个带兜底值的自定义 serializer，
     * 而不是放宽全局 —— 全局宽松会让「真正写坏了」的数据也静默通过。
     */
    inline fun <reified T> decodeFromString(string: String): T {
        return globalJson.decodeFromString(string)
    }
}
