package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDefaultLength
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDigitsEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordLowercaseEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordSymbolsEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordUppercaseEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubHorizontalEnabled
import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class ActionSettings(
    val passwordGenerator: PasswordGenerator = PasswordGenerator(),
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
) {
    @Serializable
    @Keep
    data class PasswordGenerator(
        val length: Int = PasswordDefaultLength,
        val lowercase: Boolean = PasswordLowercaseEnabled,
        val uppercase: Boolean = PasswordUppercaseEnabled,
        val digits: Boolean = PasswordDigitsEnabled,
        val symbols: Boolean = PasswordSymbolsEnabled
    )
}

@Serializable
@Keep
data class VolumeScrubConfig(
    val horizontalEnabled: Boolean = VolumeScrubHorizontalEnabled,
    val stepThresholdDp: Int = VolumeScrubStepThresholdDp,
)
