package hunoia.luno.action.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 存储里的 typeId 在定义表中不存在（数据损坏、外部构造的备份）。 */
class UnknownActionTypeException(val typeId: String) :
    IllegalArgumentException("unknown action type: $typeId")

/** 枚举参数缺失或取值不在枚举内。 */
class InvalidActionParameterException(val key: String, val raw: String) :
    IllegalArgumentException("invalid enum param $key=\"$raw\"")

/** 枚举参数解析：缺失/非法值抛受控异常（由执行层转成 InvalidParameter），不再裸 valueOf 崩协程。 */
internal inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String): T {
    val raw = str(key)
    return enumValues<T>().firstOrNull { it.name == raw }
        ?: throw InvalidActionParameterException(key, raw)
}

/** 同 [enumValue]，但缺 key 时用默认值（值存在却非法仍然报错）。 */
internal inline fun <reified T : Enum<T>> JsonObject.enumValueOr(key: String, default: T): T =
    if (this[key] == null) default else enumValue(key)

internal fun JsonObject.str(key: String, default: String = ""): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: default

internal fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    this[key]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: default

internal fun JsonObject.intOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

internal fun JsonObject.long(key: String, default: Long): Long =
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: default

internal fun JsonObject.arrayOfStr(key: String): List<String> =
    this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive?.contentOrNull } ?: emptyList()

/** 键值对参数：存成 JSON 对象；空键忽略（编辑器允许留空行）。 */
internal fun JsonObject.mapOfStr(key: String): Map<String, String> {
    val obj = this[key] as? JsonObject ?: return emptyMap()
    return obj.entries.mapNotNull { (k, v) ->
        if (k.isBlank()) null else k to ((v as? JsonPrimitive)?.contentOrNull.orEmpty())
    }.toMap()
}

/** runRule 参数：只取 `param.` 前缀键，去掉前缀后作为子规则参数名。 */
internal fun JsonObject.ruleParams(): Map<String, String> =
    entries.mapNotNull { (key, value) ->
        if (!key.startsWith("param.")) null
        else key.removePrefix("param.") to ((value as? JsonPrimitive)?.contentOrNull.orEmpty())
    }.toMap()

/** Query 参数：存成 JSON 对象 `{key: value}`，空键忽略。 */
internal fun JsonObject.queryParams(): List<QueryParam> {
    val obj = this["queryParameters"] as? JsonObject ?: return emptyList()
    return obj.entries
        .map { (key, value) -> QueryParam(key, (value as? JsonPrimitive)?.contentOrNull.orEmpty()) }
        .filter { it.name.isNotBlank() }
}
