package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.action.model.arrayOfStr
import hunoia.luno.action.model.bool
import hunoia.luno.action.model.enumValueOr
import hunoia.luno.action.model.long
import hunoia.luno.action.model.str
import hunoia.luno.core.domain.ACTION_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.ACTION_PANEL_TYPE_ID
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.GRID_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.GRID_PANEL_TYPE_ID
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 应用内部动作：面板、显隐、入口与手势工具。 */
internal object InternalActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        ActionDefinition(Action.NONE_TYPE_ID, "无", ActionCategory.NONE, Capability.None,
            icon = Icons.Default.Block),
        ActionDefinition("internal.subGesture", "子手势", ActionCategory.SUB_GESTURE, Capability.None,
            icon = Icons.Default.Gesture),
        /**
         * 动作面板：触发时弹弧形菜单让用户挑一个。`panelId` 必填——既不出现在
         * `pickerDefinitions`（面板行按全局列表动态合成），也存不出空引用。
         */
        ActionDefinition(ACTION_PANEL_TYPE_ID, "动作面板", ActionCategory.ACTION_PANEL, Capability.None,
            parameters = listOf(ParameterDefinition.Text(
                key = ACTION_PANEL_PARAM_PANEL_ID,
                label = "面板",
                required = true,
            )),
            icon = Icons.Default.RadioButtonChecked),
        /**
         * 网格面板：触发时在触点附近弹网格快捷中心。与 [ACTION_PANEL_TYPE_ID] 一样
         * 必须落到具体面板，但不带 panelId 参数：选择页按全局面板列表动态合成一行
         * 一个具名面板（见 `assembleDataTransform`），引用由合成行带进去；无参同时
         * 进不了动作库新建页（`ActionCatalog.libraryDefinitions` 只收有参动作），
         * 不必占 `libraryExcluded`。
         */
        ActionDefinition(GRID_PANEL_TYPE_ID, "网格面板", ActionCategory.GRID_PANEL, Capability.None,
            icon = Icons.Default.GridView),
        // 参数改在动作选择页行尾设置按钮里配（弹窗），不设置落运行时默认值。
        // 原「隐藏触钮」合并进本动作的临时隐藏模式。
        ActionDefinition("internal.buttonsVisibility", "触钮显隐", ActionCategory.VISIBILITY, Capability.None,
            icon = Icons.Default.Visibility),
        ActionDefinition("internal.quickAppLauncher", "快速启动面板", ActionCategory.ENTRY, Capability.None,
            icon = Icons.Default.Apps),
        ActionDefinition("internal.floatBallVisibility", "悬浮球显隐", ActionCategory.VISIBILITY, Capability.None,
            icon = Icons.Default.Adjust),
        ActionDefinition("internal.quickSearch", "快速搜索", ActionCategory.ENTRY, Capability.None,
            icon = Icons.Default.Search),
        // 水平调节删掉，恒开；灵敏度在动作选择页行尾设置按钮里配。
        ActionDefinition("internal.volumeScrub", "滑动调音量", ActionCategory.GESTURE_TOOL, Capability.None,
            icon = Icons.Default.Widgets),
        ActionDefinition("internal.pointer", "指针", ActionCategory.GESTURE_TOOL, Capability.None,
            icon = Icons.Default.TouchApp),
        // 无参定义：动作库只收有参动作，自动不显示；选择页仍可选。
        // 排除名单改为全局「最近应用黑名单」（动作设置），历史源头过滤。
        ActionDefinition("internal.previousApp", "上一个应用", ActionCategory.APP, Capability.None,
            icon = Icons.Default.SwapHoriz),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register(Action.NONE_TYPE_ID) { Action.None }
        ActionMappingRegistry.register("internal.subGesture") { Action.SubGesture }
        ActionMappingRegistry.register("internal.buttonsVisibility") { p ->
            Action.ButtonsVisibility(
                p.bool("visible", false),
                p.enumValueOr("target", ButtonTarget.ALL),
                p.arrayOfStr("buttonIds"),
                p.long("hideForMs", 0),
            )
        }
        ActionMappingRegistry.register("internal.quickAppLauncher") { Action.QuickAppLauncher }
        ActionMappingRegistry.register("internal.floatBallVisibility") { p ->
            Action.FloatBallVisibility(
                when (p.str("visible", "toggle")) {
                    "true" -> true
                    "false" -> false
                    else -> null
                },
                p.long("hideForMs", 0),
            )
        }
        ActionMappingRegistry.register("internal.gridPanel") { p ->
            Action.GridPanel(p.str(GRID_PANEL_PARAM_PANEL_ID))
        }
        ActionMappingRegistry.register("internal.quickSearch") { Action.QuickSearch }
        ActionMappingRegistry.register("internal.volumeScrub") { p ->
            Action.VolumeScrub(
                // 未写阈值 = null，默认值由实现侧（配置层）决定，映射层不持默认。
                stepThresholdDp = (p["stepThresholdDp"] as? JsonPrimitive)
                    ?.contentOrNull?.toIntOrNull(),
            )
        }
        ActionMappingRegistry.register("internal.pointer") { Action.Pointer }
        ActionMappingRegistry.register("internal.previousApp") { Action.PreviousApp }
        // 动作面板由 SideGestureContainer.runGestureActions 就地展开成面板项，正常路径
        // 不会流到执行层。能走到这里说明面板被删或内容为空 —— 按「无动作」收口，
        // 而不是抛 UnknownActionTypeException 让手势链路报错。
        ActionMappingRegistry.register(ACTION_PANEL_TYPE_ID) { Action.None }
    }
}
