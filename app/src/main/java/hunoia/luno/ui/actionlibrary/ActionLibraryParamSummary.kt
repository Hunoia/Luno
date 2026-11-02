package hunoia.luno.ui.actionlibrary

import hunoia.luno.core.domain.ActionLibraryEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 动作库条目的副标题：已配置参数值；键值对只展键名，空值与 false 跳过。 */
internal fun actionLibraryParamSummary(entry: ActionLibraryEntry): String =
    entry.params.entries.mapNotNull { (key, value) ->
        val text = when (value) {
            is JsonObject -> value.keys
                .filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" · ")
            is JsonPrimitive -> value.contentOrNull?.takeIf(String::isNotBlank)
            else -> null
        }
        when {
            text == null -> null
            text == "false" -> null
            else -> text
        }
    }.joinToString(" · ")
