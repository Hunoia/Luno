package hunoia.luno.action.execution

import android.util.Log
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.action.definitions.excludedTemplateKeys
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionMapper
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.template.ParamTemplates
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class ActionResolver(
    private val registry: ActionExecutorRegistry,
) {
    suspend fun resolveFromLibrary(
        stored: StoredAction,
        context: ExecutorContext,
    ): ActionResult {
        val definition = ActionDefinitions.byTypeId(stored.typeId)
        val resolved = runCatching { resolveParams(stored, definition, context) }
            .onFailure { error -> Log.w("LunoLauncher", "param template resolve failed", error) }
            .getOrNull()
        val params = resolved?.params ?: stored.params

        // 只有本次真的展开过模板才校验空值，不改变无模板动作原有的失败语义。
        if (resolved?.substituted == true && hasBlankRequiredText(definition, params)) {
            return ActionResult.Failed(ActionFailure.InvalidParameter)
        }
        return registry.execute(ActionMapper.fromStored(StoredAction(stored.typeId, params)), context)
    }

    /** 无 token 时零分配；有 token 时才取值并展开，存储保持原文。 */
    private suspend fun resolveParams(
        stored: StoredAction,
        definition: ActionDefinition?,
        context: ExecutorContext,
    ): ResolvedParams {
        val params = stored.params
        val excluded = definition?.excludedTemplateKeys().orEmpty()

        if (excluded.isEmpty()) {
            val used = ParamTemplates.tokensIn(params)
            if (used.isEmpty()) return ResolvedParams(params, substituted = false)
            return ResolvedParams(
                ParamTemplates.resolveParams(params, context.templateValues(used)),
                substituted = true,
            )
        }

        val substitutable = JsonObject(params.filterKeys { it !in excluded })
        val used = ParamTemplates.tokensIn(substitutable)
        if (used.isEmpty()) return ResolvedParams(params, substituted = false)
        val replaced = ParamTemplates.resolveParams(substitutable, context.templateValues(used))
        return ResolvedParams(
            JsonObject(params.mapValues { (key, value) -> if (key in excluded) value else replaced[key] ?: value }),
            substituted = true,
        )
    }

    /** 模板展开后为空的必填文本参数（如手势触发时的 `${trigger.app}`）直接判为参数无效。 */
    private fun hasBlankRequiredText(definition: ActionDefinition?, params: JsonObject): Boolean =
        definition?.parameters.orEmpty().any { param ->
            val isText = param is ParameterDefinition.Text || param is ParameterDefinition.TextLarge
            isText && param.required &&
                (params[param.key] as? JsonPrimitive)?.contentOrNull?.isBlank() == true
        }

    private data class ResolvedParams(val params: JsonObject, val substituted: Boolean)
}
