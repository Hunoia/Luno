package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.core.domain.Capability
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.core.domain.POINT_PARAM_KEYS

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
        /** 输入框为空时的占位提示（如可选 Action 的常见取值）。 */
        val placeholder: String? = null,
    ) : ParameterDefinition

    data class Bool(
        override val key: String,
        override val label: String,
        override val defaultValue: String = "false",
    ) : ParameterDefinition {
        override val required: Boolean = false
    }

    data class AppSelector(
        override val key: String,
        override val label: String,
        override val required: Boolean = true,
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
    ) : ParameterDefinition {
        override val defaultValue: String? = null
    }
}

/**
 * 该定义是否带坐标参数（tap/longPress）：决定参数编辑器是否给屏幕取点入口、
 * 以及是否适用「使用触发位置坐标」开关（`StoredAction.usesTriggerPoint`）。
 */
fun ActionDefinition.hasPointParams(): Boolean =
    parameters.map { it.key }.containsAll(hunoia.luno.core.domain.POINT_PARAM_KEYS)

/**
 * 展示 / 编辑器投影。它同时实现纯契约 [hunoia.luno.core.domain.ActionMetadata]：
 * 执行口只读能力与必填文本键，展示层再加名称、分类、参数编辑器与图标。
 *
 * 新增动作时只增加一条 [ActionDefinition]；执行口不再需要另建匿名元数据适配器。
 */
data class ActionDefinition(
    override val typeId: String,
    override val name: String,
    val category: ActionCategory,
    override val capability: Capability,
    val parameters: List<ParameterDefinition> = emptyList(),
    val icon: ImageVector = Icons.Default.Android,
    /** 其他可选能力：任一可用即可执行（见 `ActionMetadata.alternativeCapabilities`）。 */
    override val alternativeCapabilities: Set<Capability> = emptySet(),
) : hunoia.luno.core.domain.ActionMetadata {

    private val requiredTextKeyCache by lazy {
        parameters.mapNotNull { param ->
            val isText = param is ParameterDefinition.Text || param is ParameterDefinition.TextLarge
            if (isText && param.required) param.key else null
        }.toSet()
    }

    override val requiredTextKeys: Set<String>
        get() = requiredTextKeyCache
}

