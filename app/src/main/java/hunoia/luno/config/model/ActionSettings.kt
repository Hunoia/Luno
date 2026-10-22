package hunoia.luno.config.model

import androidx.annotation.Keep
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
    /** 悬浮球；同样必须留在本域内，否则运行时读不到。 */
    val floatBall: FloatBallSettings = FloatBallSettings(),
    /** 最近应用黑名单：历史记录源头过滤，内置部件最近应用与动作上一个应用共用。 */
    val recentAppsBlocklist: List<String> = emptyList(),
)

@Serializable
@Keep
data class VolumeScrubConfig(
    val stepThresholdDp: Int = VolumeScrubStepThresholdDp,
)
