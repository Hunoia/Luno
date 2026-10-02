package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.action.model.Capability
import hunoia.luno.action.definition.ActionCategory
import hunoia.luno.action.model.Action
import kotlin.reflect.KClass

sealed interface ParameterDefinition {
    val key: String
    val label: String
    val required: Boolean
    val defaultValue: String?

    data class Text(
        override val key: String,
        override val label: String,
        override val required: Boolean = true,
        override val defaultValue: String? = null,
    ) : ParameterDefinition

    data class Number(
        override val key: String,
        override val label: String,
        override val required: Boolean = true,
        override val defaultValue: String? = null,
        val min: Int? = null,
        val max: Int? = null,
    ) : ParameterDefinition

    data class Bool(
        override val key: String,
        override val label: String,
        override val defaultValue: String = "false",
    ) : ParameterDefinition {
        override val required: Boolean = false
    }

    data class Enum(
        override val key: String,
        override val label: String,
        val options: List<EnumOption>,
        override val required: Boolean = true,
        override val defaultValue: String? = null,
    ) : ParameterDefinition

    data class AppSelector(
        override val key: String,
        override val label: String,
        override val required: Boolean = true,
    ) : ParameterDefinition {
        override val defaultValue: String? = null
    }

    data class AppSelectorMulti(
        override val key: String,
        override val label: String,
        override val required: Boolean = false,
    ) : ParameterDefinition {
        override val defaultValue: String? = null
    }

    data class MiniWindow(
        override val key: String,
        override val label: String,
        override val required: Boolean = false,
    ) : ParameterDefinition {
        override val defaultValue: String? = null
    }

    data class ActivitySelector(
        override val key: String,
        override val label: String,
        override val required: Boolean = true,
        val packageKey: String = "packageName",
    ) : ParameterDefinition {
        override val defaultValue: String? = null
    }

    data class Path(
        override val key: String,
        override val label: String,
        override val required: Boolean = true,
    ) : ParameterDefinition {
        override val defaultValue: String? = null
    }

    data class TextLarge(
        override val key: String,
        override val label: String,
        override val required: Boolean = true,
    ) : ParameterDefinition {
        override val defaultValue: String? = null
    }
}

data class EnumOption(val value: String, val label: String)

data class ActionDefinition(
    val actionType: KClass<out Action>,
    val typeId: String,
    val name: String,
    val category: ActionCategory,
    val capability: Capability,
    val parameters: List<ParameterDefinition> = emptyList(),
    val icon: ImageVector = Icons.Default.Android,
    /** 内部动作同时存在的旧 action id，用于保持既有运行时特判 */
    val legacyId: String? = null,
)
