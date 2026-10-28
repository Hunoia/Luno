package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.SettingsPage
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.action.model.bool
import hunoia.luno.action.model.enumValueOr
import hunoia.luno.action.model.str
import hunoia.luno.core.domain.Capability

/** 系统能力与外部请求类动作。 */
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
        ActionDefinition(
            "system.vibrate", "振动", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.Vibration,
        ),
        // 剪贴板/媒体控制拆成无参数动作；密码长度用动作设置里的密码生成配置。
        ActionDefinition(
            "system.clipboardRandomName", "剪贴板随机名称", ActionCategory.CLIPBOARD,
            Capability.None,
            icon = Icons.Default.ContentCopy,
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
        // P2-5：自动化最常用的四个外部动作。HTTP 走 HttpURLConnection，不引入额外依赖。
        ActionDefinition(
            "http.request", "HTTP 请求", ActionCategory.ADVANCED,
            Capability.None,
            listOf(
                ParameterDefinition.Text("url", "URL"),
                ParameterDefinition.Text(
                    "method",
                    "方法",
                    required = false,
                    defaultValue = "GET",
                    allowTemplates = false,
                    placeholder = "GET / POST / PUT / DELETE / HEAD",
                ),
                ParameterDefinition.TextLarge("headers", "请求头（每行 Key: Value）", required = false),
                ParameterDefinition.TextLarge("body", "请求体", required = false),
            ),
            icon = Icons.Default.Link,
        ),
        ActionDefinition(
            "system.toast", "Toast 提示", ActionCategory.SYSTEM,
            Capability.None,
            listOf(ParameterDefinition.Text("text", "文本")),
            icon = Icons.Default.Info,
        ),
        ActionDefinition(
            "system.notify", "发送通知", ActionCategory.SYSTEM,
            Capability.Notification,
            listOf(
                ParameterDefinition.Text("title", "标题", required = false, defaultValue = "Luno"),
                ParameterDefinition.TextLarge("text", "正文"),
            ),
            icon = Icons.Default.Notifications,
        ),
        ActionDefinition(
            "system.clipboardSet", "写入剪贴板", ActionCategory.CLIPBOARD,
            Capability.None,
            listOf(ParameterDefinition.TextLarge("text", "文本")),
            icon = Icons.Default.ContentCopy,
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
        ActionMappingRegistry.register("system.vibrate") { p -> Action.Vibrate(p.str("pattern", "0,500")) }
        ActionMappingRegistry.register("system.clipboardRandomName") { Action.ClipboardRandomName }
        ActionMappingRegistry.register("system.mediaPlayPause") { Action.MediaPlayPause }
        ActionMappingRegistry.register("system.mediaNext") { Action.MediaNext }
        ActionMappingRegistry.register("system.mediaPrevious") { Action.MediaPrevious }
        ActionMappingRegistry.register("system.mediaMute") { Action.MediaMute }
        ActionMappingRegistry.register("system.flashlight") { Action.Flashlight }
        ActionMappingRegistry.register("http.request") { p ->
            Action.HttpRequest(
                url = p.str("url"),
                method = p.str("method", "GET"),
                headers = p.str("headers"),
                body = p.str("body"),
            )
        }
        ActionMappingRegistry.register("system.toast") { p -> Action.Toast(p.str("text")) }
        ActionMappingRegistry.register("system.notify") { p ->
            Action.Notify(
                title = p.str("title", "Luno"),
                text = p.str("text"),
            )
        }
        ActionMappingRegistry.register("system.clipboardSet") { p -> Action.SetClipboard(p.str("text")) }
    }
}
