package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Keep
data class Action(
    val value: String = "0",
    val data: String = "",
    val longPressAction: Action? = null,
    @Transient
    val extra: Any? = null
) {
    companion object {
        val NONE: Action get() = Action(value = "0")
        fun toList(vararg value: String): List<Action> {
            return value.map { Action(it) }
        }
    }
}
