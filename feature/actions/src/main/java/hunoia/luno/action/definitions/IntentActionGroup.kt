package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.bool
import hunoia.luno.action.model.queryParams
import hunoia.luno.action.model.str
import hunoia.luno.core.domain.Capability

/** Intent / 分享类动作。 */
internal object IntentActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        ActionDefinition(
            "intent.openUrl", "打开链接", ActionCategory.LINK_SHARE,
            Capability.None,
            listOf(
                ParameterDefinition.Text("url", "URL"),
                ParameterDefinition.KeyValueMap("queryParameters", "Query 参数"),
                ParameterDefinition.Bool("miniWindow", "小窗打开", "false"),
            ),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        ),
        ActionDefinition(
            "intent.shareText", "分享文本", ActionCategory.LINK_SHARE,
            Capability.None,
            icon = Icons.Default.Share,
        ),
        ActionDefinition(
            "intent.shareFile", "分享文件", ActionCategory.LINK_SHARE,
            Capability.None,
            icon = Icons.Default.Upload,
        ),
        ActionDefinition(
            "intent.assist", "系统助手", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.Assistant,
        ),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("intent.openUrl") { p ->
            Action.OpenUrl(
                url = p.str("url"),
                queryParameters = p.queryParams(),
                miniWindow = p.bool("miniWindow"),
            )
        }
        ActionMappingRegistry.register("intent.shareText") { p ->
            Action.ShareText(p.str("text"), p.str("mimeType", "text/plain"))
        }
        ActionMappingRegistry.register("intent.shareFile") { Action.ShareFile }
        ActionMappingRegistry.register("intent.assist") { Action.Assist }
    }
}
