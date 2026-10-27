package hunoia.luno.core.domain

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 运行态与条件共用的纯枚举。
 *
 * 从 `config.model` / `action.model` 下沉：`RuntimeSnapshot` 需要引用它们，而契约层
 * 不能反向依赖数据层或功能层。旧包用 typealias 保持引用不变。
 */

@Serializable
@Keep
enum class ScreenEventType { SCREEN_ON, SCREEN_OFF, USER_UNLOCK }

@Serializable
@Keep
enum class KeyboardEventType { SHOWN, HIDDEN }

/** 音乐播放变化方向（MUSIC_CHANGED 脉冲）。 */
@Serializable
@Keep
enum class PlaybackDirection { STARTED, STOPPED }

/** 铃声模式三态（静音条件）。 */
@Serializable
@Keep
enum class RingerMode { SILENT, VIBRATE, NORMAL }

@Serializable
@Keep
enum class NetworkType { WIFI, MOBILE, NONE, OTHER }

/** 通知方向/连接方向：null 表示不限。 */
@Serializable
@Keep
enum class ConnectionDirection { CONNECTED, DISCONNECTED }

enum class AudioStream { MUSIC, RING, NOTIFICATION, ALARM, VOICE, SYSTEM }

enum class VolumeDirection { UP, DOWN }
