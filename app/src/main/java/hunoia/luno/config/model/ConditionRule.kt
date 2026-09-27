package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Keep
enum class RuleEffect { SHOW, HIDE }

@Serializable
@Keep
enum class RuleScope { ALL, EXCEPT, ONLY }

@Serializable
@Keep
enum class ScreenType { LOCK_SCREEN, LAUNCHER, LANDSCAPE, PORTRAIT, KEYBOARD_INPUT }

@Serializable
@Keep
sealed interface Condition {

    @Serializable @Keep @SerialName("all")
    data class All(val items: List<Condition> = emptyList()) : Condition

    @Serializable @Keep @SerialName("any")
    data class Any(val items: List<Condition> = emptyList()) : Condition

    @Serializable @Keep @SerialName("not")
    data class Not(val inner: Condition) : Condition

    @Serializable @Keep @SerialName("app")
    data class ForegroundApp(val packageNames: List<String> = emptyList()) : Condition

    @Serializable @Keep @SerialName("screen")
    data class Screen(val screenType: ScreenType) : Condition

    @Serializable @Keep @SerialName("battery")
    data class Battery(
        val charging: Boolean? = null,
        val levelMin: Int? = null,
        val levelMax: Int? = null,
    ) : Condition

    @Serializable @Keep @SerialName("time")
    data class TimeRange(
        val startMinute: Int,
        val endMinute: Int,
    ) : Condition {
        fun matchesMinute(minuteOfDay: Int): Boolean =
            if (startMinute <= endMinute) minuteOfDay in startMinute..endMinute
            else minuteOfDay >= startMinute || minuteOfDay < endMinute
    }
}

@Serializable
@Keep
data class VisibilityRule(
    val id: String,
    val enabled: Boolean = true,
    val name: String = "",
    val condition: Condition,
    val effect: RuleEffect = RuleEffect.HIDE,
    val scope: RuleScope = RuleScope.ALL,
    val buttonIds: List<String> = emptyList(),
) {
    fun covers(buttonId: String): Boolean = when (scope) {
        RuleScope.ALL -> true
        RuleScope.EXCEPT -> buttonId !in buttonIds
        RuleScope.ONLY -> buttonId in buttonIds
    }
}
