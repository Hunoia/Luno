package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.core.domain.AudioStream
import hunoia.luno.core.domain.SettingsPage
import hunoia.luno.core.domain.VolumeDirection
import hunoia.luno.action.model.enumValueOr
import hunoia.luno.action.model.icon
import hunoia.luno.action.model.label
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.QuickToggleKind

/** 系统能力类动作。 */
internal object SystemActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        // 参数改在动作选择页行尾设置弹窗里配（openSettings 页面已改成变体，搜索按变体展开）。
        ActionDefinition(
            "system.openSettings", "打开设置页", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.Settings,
            variants = buildList {
                // 缺省页（主设置）放第一位：未配置的动作显示与执行的必须是同一个页面。
                val ordered = listOf(SettingsPage.DEFAULT) +
                    SettingsPage.entries.filter { it != SettingsPage.DEFAULT }
                ordered.forEach { page ->
                    add(
                        ActionVariant(
                            key = page.name,
                            label = page.label,
                            params = mapOf("page" to page.name),
                        ),
                    )
                }
            },
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
        // 一个动作 + 一组变体：勿扰/蓝牙/Wi-Fi 等合并成「快捷开关」，具体切哪个在
        // 动作选择页行尾设置里选，搜索结果按变体展开成独立条目。
        ActionDefinition(
            "system.toggle", "快捷开关", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.Tune,
            variants = buildList {
                // 缺省变体（勿扰）放第一位：未配置的动作显示与执行的必须是同一个开关。
                val ordered = listOf(QuickToggleKind.DEFAULT) +
                    QuickToggleKind.entries.filter { it != QuickToggleKind.DEFAULT }
                ordered.forEach { kind ->
                    add(
                        ActionVariant(
                            key = kind.name,
                            label = kind.label,
                            params = mapOf("kind" to kind.name),
                            icon = kind.icon,
                        ),
                    )
                }
            },
        ),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("system.openSettings") { p ->
            Action.OpenSettings(p.enumValueOr("page", SettingsPage.DEFAULT))
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
        ActionMappingRegistry.register("system.toggle") { p ->
            Action.QuickToggle(p.enumValueOr("kind", QuickToggleKind.DEFAULT))
        }
    }
}
