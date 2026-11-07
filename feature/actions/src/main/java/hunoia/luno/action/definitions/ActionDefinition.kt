package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.core.domain.Capability
import hunoia.luno.action.definitions.ActionCategory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
        /** 需要区分分身时，把选中条目的 userId 写进这个键；null = 不展示分身。 */
        val userIdKey: String? = null,
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
 * 同一动作的预设变体（「快捷开关」的勿扰/蓝牙/Wi-Fi 等）。
 *
 * 变体不是参数，也不是独立动作：它只是 `params` 的一组预设值。搜索按变体展开成独立条目，
 * 动作选择页把变体做成一行单选列表；真正需要用户填写的参数仍走 [ActionDefinition.parameters]。
 * 判据：**多个动作被合并成一个动作**时才用变体（如 Wi-Fi/蓝牙/勿扰 → 快捷开关）；
 * 音量刮擦灵敏度、音量步长这类属于参数，不是变体。
 */
data class ActionVariant(
    /** 稳定标识：搜索 key、行设置选中判定用，改它等于重建存储。 */
    val key: String,
    val label: String,
    /** 变体的参数预设；执行前由行设置/搜索写进 `StoredAction.params`。 */
    val params: Map<String, String>,
    /** 变体自己的图标（搜索结果按变体展示）；null = 用动作自身的图标。 */
    val icon: ImageVector? = null,
)

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
    /** 合并式动作的预设变体；空 = 普通动作（见 [ActionVariant]）。 */
    val variants: List<ActionVariant> = emptyList(),
) : hunoia.luno.core.domain.ActionMetadata {

    private val requiredTextKeyCache by lazy {
        parameters.mapNotNull { param ->
            val isText = param is ParameterDefinition.Text || param is ParameterDefinition.TextLarge
            if (isText && param.required) param.key else null
        }.toSet()
    }

    override val requiredTextKeys: Set<String>
        get() = requiredTextKeyCache

    /** 按已存参数匹配变体：变体的每组键值都要命中；未命中/无变体返回 null。 */
    fun variantOf(params: JsonObject): ActionVariant? =
        variants.firstOrNull { variant ->
            variant.params.all { (key, value) ->
                (params[key] as? JsonPrimitive)?.contentOrNull == value
            }
        }

    /** 缺省变体：变体表的第一个。定义时把缺省项放最前，展示与执行才不会口径不一。 */
    val defaultVariant: ActionVariant? get() = variants.firstOrNull()

    /**
     * 展示 / 执行前解析变体：参数未配置或匹配不到时回退到缺省变体。
     * 不能返回 null——否则“未配置”的动作按钮会显示动作名，却执行缺省变体。
     */
    fun resolvedVariant(params: JsonObject): ActionVariant? = variantOf(params) ?: defaultVariant
}

