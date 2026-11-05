package hunoia.luno.actions.library

import android.content.Context
import hunoia.luno.actions.R
import hunoia.luno.core.domain.ActionLibraryEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 动作库条目的副标题：已配置参数值；键值对只展键名，空值与 false 跳过。 */
fun actionLibraryParamSummary(entry: ActionLibraryEntry, context: Context): String =
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
            // userId 是内部参数：主用户不展示，分身显示可读标签。
            key == "userId" -> context.getString(R.string.app_clone_label)
            else -> text
        }
    }.joinToString(" · ")
