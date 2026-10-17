package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.config.defaults.AdvancedSettingsDefaults.KeepAliveEnabled
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class AdvancedSettings(
    /** 小窗位置与大小，竖屏横屏各一套。新写入只走这个字段。 */
    val miniWindow: MiniWindowSettings? = null,
    val keepAliveEnabled: Boolean = KeepAliveEnabled,
    // ↓ 旧版小窗四字段，仅用于读取历史数据。withMiniWindowSettings() 首次写回时把它们置 null，
    //   而 globalJson 的 encodeDefaults 取默认值 false，null 字段不会被序列化出去，
    //   所以旧字段只会出现在迁移前写下的那份 JSON 里，之后自动消失。
    val miniWindowX: Float? = null,
    val miniWindowY: Float? = null,
    val miniWindowWidth: Float? = null,
    val miniWindowHeight: Float? = null,
)