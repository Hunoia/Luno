package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Terminal
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.bool
import hunoia.luno.action.model.ruleParams
import hunoia.luno.action.model.str
import hunoia.luno.core.domain.Capability

/** 高级动作：shell 与「规则即动作」。 */
internal object AdvancedActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        ActionDefinition(
            "shell.custom", "自定义命令", ActionCategory.ADVANCED,
            Capability.Shizuku,
            listOf(
                ParameterDefinition.TextLarge("command", "Shell 命令"),
                ParameterDefinition.Bool("copyOutput", "复制输出到剪贴板", "false"),
                ParameterDefinition.Bool("showToast", "显示结果", "false"),
            ),
            icon = Icons.Default.Terminal,
        ),
        // 规则即动作：只能引用已存规则（库新建/定义表直选都不开放），ruleId 必填。
        ActionDefinition(
            "internal.runRule", "运行规则", ActionCategory.ADVANCED, Capability.None,
            parameters = listOf(ParameterDefinition.Text(
                key = "ruleId",
                label = "规则",
            )),
            icon = Icons.Default.PlayCircle,
        ),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("shell.custom") { p ->
            Action.CustomCommand(
                p.str("command"),
                p.bool("showToast"),
                p.bool("copyOutput"),
            )
        }
        ActionMappingRegistry.register("internal.runRule") { p ->
            Action.RunRule(
                ruleId = p.str("ruleId"),
                params = p.ruleParams(),
            )
        }
    }
}
