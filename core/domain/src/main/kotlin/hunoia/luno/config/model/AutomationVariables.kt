package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 自动化全局变量：跨运行、跨规则持久化，只支持字符串值（P2-1 的收敛版）。 */
@Serializable
@Keep
data class GlobalVariable(
    val name: String,
    val value: String = "",
    val updatedAtMs: Long = 0L,
)

@Serializable
@Keep
data class GlobalVariables(
    val entries: List<GlobalVariable> = emptyList(),
) {
    /** 模板/条件统一用 `var.<name>`，和局部 SET_VAR 同形。 */
    fun toTemplateMap(): Map<String, String> =
        entries.associate { "var.${it.name}" to it.value }

    /** 去空白、去重、丢弃非法名；后出现的同名项覆盖先出现的。 */
    fun normalized(): GlobalVariables {
        val map = LinkedHashMap<String, GlobalVariable>()
        entries.forEach { item ->
            val name = item.name.trim()
            if (!VariableNames.isValid(name)) return@forEach
            if (map.size >= MAX_VARIABLES && name !in map) return@forEach
            map[name] = item.copy(name = name, value = item.value.take(MAX_VALUE_LENGTH))
        }
        return GlobalVariables(map.values.toList())
    }

    fun put(name: String, value: String, nowMs: Long = System.currentTimeMillis()): GlobalVariables {
        val clean = name.trim()
        if (!VariableNames.isValid(clean)) return this
        val next = entries.toMutableList()
        val index = next.indexOfFirst { it.name == clean }
        val item = GlobalVariable(clean, value.take(MAX_VALUE_LENGTH), nowMs)
        if (index >= 0) next[index] = item else next += item
        return GlobalVariables(next).normalized()
    }

    fun remove(name: String): GlobalVariables =
        GlobalVariables(entries.filterNot { it.name == name })

    fun valueOf(name: String): String? = entries.firstOrNull { it.name == name }?.value

    companion object {
        const val MAX_VARIABLES = 100
        const val MAX_VALUE_LENGTH = 4096
    }
}

object VariableNames {
    private val pattern = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    fun isValid(name: String): Boolean = pattern.matches(name.trim())
}
