package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.SettingsPage
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.action.model.enumValueOr
import hunoia.luno.core.domain.Capability

/** 系统能力类动作。 */
internal object SystemActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        // 参数改在动作选择页行尾设置弹窗里配（openSettings 页面由快速搜索长按共用写入）。
        ActionDefinition(
            "system.openSettings", "打开设置页", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.Settings,
        ),
        ActionDefinition(
            "system.volume", "音量控制", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.AutoMirrored.Default.VolumeUp,
        ),
        // 剪贴板/媒体控制拆成无参数动作；密码长度用动作设置里的密码生成配置。
        ActionDefinition(
            "system.clipboardRandomName", "剪贴板随机名称", ActionCategory.CLIPBOARD,
            Capability.None,
            icon = Icons.Default.ContentCopy,
        ),
        ActionDefinition(
            "system.speechRecognize", "语音识别", ActionCategory.CLIPBOARD,
            Capability.None,
            icon = Icons.Default.Mic,
        ),
        ActionDefinition(
            "system.mediaPlayPause", "播放/暂停", ActionCategory.MEDIA,
            Capability.None,
            icon = Icons.Default.PlayPause,
        ),
        ActionDefinition(
            "system.mediaNext", "下一首", ActionCategory.MEDIA,
            Capability.None,
            icon = Icons.Default.SkipNext,
        ),
        ActionDefinition(
            "system.mediaPrevious", "上一首", ActionCategory.MEDIA,
            Capability.None,
            icon = Icons.Default.SkipPrevious,
        ),
        ActionDefinition(
            "system.mediaMute", "静音", ActionCategory.MEDIA,
            Capability.None,
            icon = Icons.Default.VolumeOff,
        ),
        ActionDefinition(
            "system.flashlight", "手电筒", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.FlashlightOn,
        ),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("system.openSettings") { p ->
            Action.OpenSettings(p.enumValueOr("page", SettingsPage.MAIN))
        }
        ActionMappingRegistry.register("system.volume") { p ->
            Action.Volume(
                p.enumValueOr("stream", AudioStream.MUSIC),
                p.enumValueOr("direction", VolumeDirection.UP),
            )
        }
        ActionMappingRegistry.register("system.clipboardRandomName") { Action.ClipboardRandomName }
        ActionMappingRegistry.register("system.speechRecognize") { Action.SpeechRecognize }
        ActionMappingRegistry.register("system.mediaPlayPause") { Action.MediaPlayPause }
        ActionMappingRegistry.register("system.mediaNext") { Action.MediaNext }
        ActionMappingRegistry.register("system.mediaPrevious") { Action.MediaPrevious }
        ActionMappingRegistry.register("system.mediaMute") { Action.MediaMute }
        ActionMappingRegistry.register("system.flashlight") { Action.Flashlight }
    }
}
