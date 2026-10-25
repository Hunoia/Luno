package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.action.model.Capability
import hunoia.luno.action.definitions.ActionCategory

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
        val allowTemplates: Boolean = true,
        /** 输入框为空时的占位提示（如可选 Action 的常见取值）。 */
        val placeholder: String? = null,
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

    /**
     * 键值对参数（Intent extras、URL query）：存成 `JsonObject`，值一律字符串。
     * 空键的行在运行时读取时忽略，未填时不会写入存储。
     * 没有“必填”概念：空对象等价于未配置。
     */
    data class KeyValueMap(
        override val key: String,
        override val label: String,
    ) : ParameterDefinition {
        override val required: Boolean = false
        override val defaultValue: String? = null
    }

    data class TextLarge(
        override val key: String,
        override val label: String,
        override val required: Boolean = true,
        val allowTemplates: Boolean = true,
    ) : ParameterDefinition {
        override val defaultValue: String? = null
    }
}

data class EnumOption(val value: String, val label: String)

/**
 * 该定义是否带坐标参数（tap/longPress）：决定参数编辑器是否给屏幕取点入口、
 * 以及是否适用「使用触发位置坐标」开关（`StoredAction.usesTriggerPoint`）。
 */
fun ActionDefinition.hasPointParams(): Boolean =
    parameters.map { it.key }.containsAll(hunoia.luno.action.model.POINT_PARAM_KEYS)

/** 该定义中明确禁止模板展开的文本参数 key（编辑器不提供插入入口，运行时也不替换）。 */
fun ActionDefinition.excludedTemplateKeys(): Set<String> = parameters
    .mapNotNull { definition ->
        when (definition) {
            is ParameterDefinition.Text -> definition.key.takeIf { !definition.allowTemplates }
            is ParameterDefinition.TextLarge -> definition.key.takeIf { !definition.allowTemplates }
            else -> null
        }
    }
    .toSet()

data class ActionDefinition(
    val typeId: String,
    val name: String,
    val category: ActionCategory,
    val capability: Capability,
    val parameters: List<ParameterDefinition> = emptyList(),
    val icon: ImageVector = Icons.Default.Android,
)

