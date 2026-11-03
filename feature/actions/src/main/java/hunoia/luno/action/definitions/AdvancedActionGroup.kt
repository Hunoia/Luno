package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.bool
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
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("shell.custom") { p ->
            Action.CustomCommand(
                p.str("command"),
                p.bool("showToast"),
                p.bool("copyOutput"),
            )
        }
    }
}
