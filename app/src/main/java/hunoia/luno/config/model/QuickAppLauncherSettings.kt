package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class QuickAppLauncherSettings(
    val recentLaunchTime: Map<String, Long> = emptyMap(),
    val launchCount: Map<String, Long> = emptyMap(),
    val panelHeightFraction: Float = 0.43063f,
    val contentHeightFraction: Float = 0.40301f,
    val candidateRows: Int = 2,
    val panelWidthFraction: Float = 0.89973f,
    val panelHorizontalBias: Float = 0.5f,
    val gridColumns: Int = 4,
    val tapOpensMiniWindow: Boolean = false,
    val keyHeightDp: Int = 45,
)
