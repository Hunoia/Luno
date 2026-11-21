package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.RecentsPickerLayout
import hunoia.luno.action.model.enumValueOr
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
         * 动作面板：触发时弹弧形菜单让用户挑一个。
         * 必须落到具名面板：选择页/动作库都不列它（在 `dynamicEntryTypeIds` 里），
         * 引用由「一行一个具名面板」的合成行带入，所以这里不靠 `required` 来挡——
         * 与 `internal.gridPanel` 保持同一套排除理由（两套理由只会让加新面板类型时漏一处）。
         */
        ActionDefinition(ACTION_PANEL_TYPE_ID, "动作面板", ActionCategory.PANEL, Capability.None,
            parameters = listOf(ParameterDefinition.Text(
                key = ACTION_PANEL_PARAM_PANEL_ID,
                label = "面板",
                required = false,
            )),
            icon = Icons.Default.RadioButtonChecked),
        /**
         * 网格面板：触发时在触点附近弹网格快捷中心。与 [ACTION_PANEL_TYPE_ID] 一样
         * 必须落到具体面板，但不带 panelId 参数：选择页按全局面板列表动态合成一行
         * 一个具名面板（见 `assembleDataTransform`），引用由合成行带进去；无参同时
         * 进不了动作库新建页（`ActionCatalog.libraryDefinitions` 只收有参动作），
         * 不必占 `libraryExcluded`。
         */
        ActionDefinition(GRID_PANEL_TYPE_ID, "面板", ActionCategory.PANEL, Capability.None,
            icon = Icons.Default.GridView),
        // 显隐统一收进「设置 → 显隐」：显示/隐藏由场景规则覆盖。
        // 选择页只留两条临时隐藏：触钮版按时长（手势工具设置）、悬浮球版按界面级失效。
        ActionDefinition("internal.hideTriggerButton", "临时隐藏触钮", ActionCategory.VISIBILITY, Capability.None,
            icon = Icons.Default.VisibilityOff),
        ActionDefinition("internal.hideFloatBall", "临时隐藏悬浮球", ActionCategory.VISIBILITY, Capability.None,
            icon = Icons.Default.VisibilityOff),
        ActionDefinition("internal.quickSearch", "快速搜索", ActionCategory.GESTURE_TOOL, Capability.None,
            icon = Icons.Default.Search),
        // 灵敏度在设置 tab 的「手势工具」页里配，动作本身无参。
        ActionDefinition("internal.volumeScrub", "滑动调音量", ActionCategory.GESTURE_TOOL, Capability.None,
            icon = Icons.Default.Widgets),
        ActionDefinition("internal.pointer", "指针", ActionCategory.GESTURE_TOOL, Capability.None,
            icon = Icons.Default.TouchApp),
        // 最近应用滚动选择器：横向/竖向拆成两条单动作，布局不再走行尾设置。
        ActionDefinition("internal.recentsPicker", "最近应用", ActionCategory.GESTURE_TOOL, Capability.None,
            icon = Icons.Default.FormatListBulleted,
            variants = listOf(
                ActionVariant("vertical", "最近应用（竖向）", mapOf("layout" to "VERTICAL")),
                ActionVariant("horizontal", "最近应用（横向）", mapOf("layout" to "HORIZONTAL")),
            )),
        // 无参定义：动作库只收有参动作，自动不显示；选择页仍可选。
        // 排除名单改为全局「最近应用黑名单」（动作设置），历史源头过滤。
        ActionDefinition("internal.previousApp", "上一个应用", ActionCategory.APP, Capability.None,
            icon = Icons.Default.SwapHoriz),
        // 识别剪贴板：读剪贴板，文本送分词视图、图片送 OCR 结果页（:service 进程内交接）。
        // 无参：选择页 / 触钮 / 子手势可选，动作库新建页不显示。
        ActionDefinition("internal.clipboardRecognize", "识别剪贴板", ActionCategory.RECOGNITION, Capability.None,
            icon = Icons.Default.DocumentScanner),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register(Action.NONE_TYPE_ID) { Action.None }
        ActionMappingRegistry.register("internal.subGesture") { Action.SubGesture }
        ActionMappingRegistry.register("internal.hideTriggerButton") { Action.HideTriggerButton }
        ActionMappingRegistry.register("internal.hideFloatBall") { Action.HideFloatBall }
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
        ActionMappingRegistry.register("internal.recentsPicker") { p ->
            Action.RecentsPicker(
                layout = p.enumValueOr("layout", RecentsPickerLayout.VERTICAL),
                // 未写 = null，默认值由实现侧（配置层）决定，映射层不持默认。
                blankScrollSensitivityPercent =
                    (p["sensitivityPercent"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
                iconSizeDp = (p["iconSizeDp"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            )
        }
        ActionMappingRegistry.register("internal.previousApp") { Action.PreviousApp }
        ActionMappingRegistry.register("internal.clipboardRecognize") { Action.ClipboardRecognize }
        // 动作面板由 SideGestureContainer.runGestureActions 就地展开成面板项，正常路径
        // 不会流到执行层。能走到这里说明面板被删或内容为空 —— 按「无动作」收口，
        // 而不是抛 UnknownActionTypeException 让手势链路报错。
        ActionMappingRegistry.register(ACTION_PANEL_TYPE_ID) { Action.None }
    }
}
