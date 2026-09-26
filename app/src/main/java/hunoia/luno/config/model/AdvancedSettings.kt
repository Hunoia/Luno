package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.ActionPanelStyles
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.ClipApps
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.ClipShortcuts

import hunoia.luno.config.defaults.AdvancedSettingsDefaults.ExcludeApps
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowHorizontalBias
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowVerticalBias
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowVerticalOffsetFraction
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowWidthFraction
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowHeightFraction
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.KeepAliveEnabled
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.MiniWindowOverrideBounds
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class AdvancedSettings(
    val excludeApps: List<String> = ExcludeApps,
    val actionPanelStyles: ActionPanelStyles = ActionPanelStyles,
    val miniWindowHorizontalBias: Float = MiniWindowHorizontalBias,
    val miniWindowVerticalBias: Float = MiniWindowVerticalBias,
    val miniWindowVerticalOffsetFraction: Float = MiniWindowVerticalOffsetFraction,
    val miniWindowWidthFraction: Float = MiniWindowWidthFraction,
    val miniWindowHeightFraction: Float = MiniWindowHeightFraction,
    val miniWindowOverrideBounds: Boolean = MiniWindowOverrideBounds,
    val keepAliveEnabled: Boolean = KeepAliveEnabled,
    val clipApps: Map<String, Float> = ClipApps,
    val clipShortcuts: Map<String, Float> = ClipShortcuts
)
