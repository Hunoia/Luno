package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.ActionSettingsDefaults.RecentsPickerIconSizeDp
import hunoia.luno.config.defaults.ActionSettingsDefaults.RecentsPickerSensitivityPercent
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class ActionSettings(
    val actionPanelStyle: ArcStyle = ArcStyle(),
    /**
     * 全局动作面板列表。挂在这里是为了复用既有通路到达 `:service` 运行时，
     * 见 [ActionPanel] 的类注释 —— 挪走会让面板静默失效。
     */
    val actionPanels: ActionPanelSettings = ActionPanelSettings(),
    /** 全局网格面板列表；同样必须留在本域内，否则运行时读不到。 */
    val actionGridPanels: GridPanelSettings = GridPanelSettings(),
    /**
     * 单功能面板配置：每个支持整页的功能一块，固定由注册表决定清单。
     * 未配置的功能使用默认几何，因此这里为空时面板仍存在。
     */
    val singlePanels: SinglePanelSettings = SinglePanelSettings(),
    /** 悬浮球；同样必须留在本域内，否则运行时读不到。 */
    val floatBall: FloatBallSettings = FloatBallSettings(),
    /** 最近应用黑名单：历史记录源头过滤，内置部件最近应用与动作上一个应用共用。 */
    val recentAppsBlocklist: List<String> = emptyList(),
    /** 链接分发：剪贴板胶囊与「Luno 作为默认浏览器」两个入口共用的规则表。 */
    val linkDispatch: LinkDispatchSettings = LinkDispatchSettings(),
    /**
     * 应用列表是否包含「无桌面入口的系统包」。
     * 统一应用清单（启动器 / 抽屉 / 搜索 / 选择弹层 / 黑名单）共用这一个开关。
     */
    val showSystemAppsInAppList: Boolean = false,
)

@Serializable
@Keep
data class VolumeScrubConfig(
    val stepThresholdDp: Int = VolumeScrubStepThresholdDp,
)

@Serializable
@Keep
data class RecentsPickerConfig(
    val layout: hunoia.luno.action.model.RecentsPickerLayout =
        hunoia.luno.action.model.RecentsPickerLayout.VERTICAL,
    /** 列表外滑动灵敏度（百分比，100 = 1.0x），作用于按住触发与 overlay 空白拖动。 */
    val blankScrollSensitivityPercent: Int = RecentsPickerSensitivityPercent,
    /** 图标边长 dp。 */
    val iconSizeDp: Int = RecentsPickerIconSizeDp,
)
