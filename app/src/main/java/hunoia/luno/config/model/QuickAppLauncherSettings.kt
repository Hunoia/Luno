package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class QuickAppLauncherSettings(
    val recentLaunchTime: Map<String, Long> = emptyMap(),
    val launchCount: Map<String, Long> = emptyMap(),
    val panelHeightFraction: Float = 0.52f,
    val contentHeightFraction: Float = 0.52f,
    val candidateRows: Int = 1,
    val panelWidthFraction: Float = 1.0f,
    val panelHorizontalBias: Float = 0.5f,
    val gridColumns: Int = 4,
    val tapOpensMiniWindow: Boolean = false,
    val keyHeightDp: Int = 36,
)
