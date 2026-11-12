package hunoia.luno.picker.components.rowsettings

import android.content.Context
import androidx.compose.runtime.Composable
import hunoia.luno.config.defaults.ActionSettingsDefaults
import hunoia.luno.config.model.GestureButton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 参数改在动作选择页行尾「设置」Sheet 里配的动作类型。 */
object RowSettingsDefaults {
    val VolumeScrubStepDp = ActionSettingsDefaults.VolumeScrubStepThresholdDp
    val HideGestureButtonDelayMs = ActionSettingsDefaults.HideGestureButtonDelayMs
    const val FloatBallVisible = "toggle"
    const val ButtonsVisible = false
    const val ButtonsTarget = "ALL"
    const val AudioStream = "MUSIC"
    const val VolumeDirection = "UP"
    const val RecentsPickerLayout = "VERTICAL"
    val RecentsPickerSensitivityPercent = ActionSettingsDefaults.RecentsPickerSensitivityPercent
    val RecentsPickerIconSizeDp = ActionSettingsDefaults.RecentsPickerIconSizeDp
}

/** 行设置编辑器的上下文：草稿 + 数据 + 写回函数。 */
class RowSettingsEditorScope(
    val typeId: String,
    val draft: JsonObject,
    val buttons: List<GestureButton>,
    val context: Context,
    val setStr: (String, String) -> Unit,
    val setBool: (String, Boolean) -> Unit,
    val setInt: (String, Int) -> Unit,
    val setLong: (String, Long) -> Unit,
    val setStringArray: (String, List<String>) -> Unit,
    /** 一次性写入一组键值（合并式动作的变体预设）：已存在的键覆盖，未列出的键保留。 */
    val setParams: (Map<String, String>) -> Unit,
)

typealias RowSettingsEditor = @Composable (RowSettingsEditorScope) -> Unit

/**
 * 一个动作的行设置描述符：标题、副标题、默认值、校验、规范化与编辑器。
 *
 * 新增可配置动作只需注册一条描述符，动作选择页不再有 `when(typeId)`。
 */
data class ActionRowSettingsDescriptor(
    val typeId: String,
    val titleRes: Int,
    val subtitle: (params: JsonObject, context: Context) -> String,
    val canonicalize: (draft: JsonObject) -> JsonObject,
    val isValid: (draft: JsonObject) -> Boolean = { true },
    val editor: RowSettingsEditor,
)

/** 行设置注册表：typeId 唯一，顺序不重要。 */
object ActionRowSettingsRegistry {

    private val descriptors = LinkedHashMap<String, ActionRowSettingsDescriptor>()

    init {
        registerBuiltins()
    }

    fun register(descriptor: ActionRowSettingsDescriptor) {
        require(descriptor.typeId.isNotBlank()) { "row settings typeId must not be blank" }
        require(!descriptors.containsKey(descriptor.typeId)) {
            "duplicate row settings typeId: ${descriptor.typeId}"
        }
        descriptors[descriptor.typeId] = descriptor
    }

    fun byTypeId(typeId: String): ActionRowSettingsDescriptor? = descriptors[typeId]

    val typeIds: Set<String> get() = descriptors.keys.toSet()

    private fun registerBuiltins() {
        BuiltinRowSettings.registerAll(::register)
    }
}

internal fun JsonObject.str(key: String, default: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: default

internal fun JsonObject.int(key: String, default: Int): Int =
    str(key, "").toIntOrNull() ?: default

internal fun JsonObject.long(key: String, default: Long): Long =
    str(key, "").toLongOrNull() ?: default

internal fun JsonObject.bool(key: String, default: Boolean): Boolean =
    str(key, "").toBooleanStrictOrNull() ?: default

internal fun JsonObject.ids(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

