package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Window
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.bool
import hunoia.luno.action.model.mapOfStr
import hunoia.luno.action.model.str
import hunoia.luno.core.domain.Capability

/** App 类动作：定义 + 解码映射同文件，新增应用动作只改这里。 */
internal object AppActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        ActionDefinition(
            hunoia.luno.core.domain.AppLaunchAction.TYPE_ID, "启动应用", ActionCategory.APP,
            Capability.None,
            listOf(
                ParameterDefinition.AppSelector("packageName", "应用"),
                ParameterDefinition.Bool("miniWindow", "小窗模式", "false"),
                ParameterDefinition.ActivitySelector("className", "Activity", required = false, packageKey = "packageName"),
                // Intent 覆盖：留空保持默认启动行为，与旧数据/旧交互完全一致。
                ParameterDefinition.Text(
                    "intentAction",
                    "Intent Action",
                    required = false,
                    allowTemplates = false,
                    placeholder = "如 android.intent.action.VIEW",
                ),
                ParameterDefinition.Text("data", "Data URI", required = false),
                ParameterDefinition.KeyValueMap("extras", "Extras"),
            ),
            icon = Icons.Default.Android,
        ),
        ActionDefinition(
            "app.details", "应用详情", ActionCategory.APP,
            Capability.None,
            listOf(ParameterDefinition.AppSelector("packageName", "应用")),
            icon = Icons.Default.Info,
        ),
        // 无参定义：动作库自动不显示；小窗尺寸/位置一律用全局小窗设置。
        ActionDefinition(
            "app.popup", "小窗当前应用", ActionCategory.APP,
            Capability.None,
            icon = Icons.Default.Window,
        ),
        ActionDefinition(
            "app.launchShortcut", "启动快捷方式", ActionCategory.APP,
            Capability.None,
            listOf(ParameterDefinition.Text("data", "快捷方式数据", required = false)),
            icon = Icons.AutoMirrored.Default.Shortcut,
        ),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register(hunoia.luno.core.domain.AppLaunchAction.TYPE_ID) { p ->
            Action.LaunchApp(
                packageName = p.str("packageName"),
                miniWindow = p.bool("miniWindow"),
                className = p.str("className"),
                intentAction = p.str("intentAction"),
                data = p.str("data"),
                extras = p.mapOfStr("extras"),
            )
        }
        ActionMappingRegistry.register("app.details") { p -> Action.AppDetails(p.str("packageName")) }
        ActionMappingRegistry.register("app.popup") { Action.Popup }
        ActionMappingRegistry.register("app.launchShortcut") { p -> Action.LaunchShortcut(p.str("data")) }
    }
}
