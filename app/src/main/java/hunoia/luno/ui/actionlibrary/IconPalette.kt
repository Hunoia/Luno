package hunoia.luno.ui.actionlibrary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.action.definition.PlayPause

data class PaletteIcon(
    val key: String,
    val vector: ImageVector,
    val label: String,
)

data class IconPaletteGroup(
    val title: String,
    val icons: List<PaletteIcon>,
)

object IconPalette {

    val groups: List<IconPaletteGroup> = listOf(
        IconPaletteGroup("系统", listOf(
            PaletteIcon("android", Icons.Default.Android, "机器人"),
            PaletteIcon("settings", Icons.Default.Settings, "设置"),
            PaletteIcon("tune", Icons.Default.Tune, "调节"),
            PaletteIcon("build", Icons.Default.Build, "构建"),
            PaletteIcon("code", Icons.Default.Code, "代码"),
            PaletteIcon("terminal", Icons.Default.Terminal, "终端"),
            PaletteIcon("power_settings_new", Icons.Default.PowerSettingsNew, "电源"),
            PaletteIcon("refresh", Icons.Default.Refresh, "刷新"),
            PaletteIcon("block", Icons.Default.Block, "拦截"),
            PaletteIcon("visibility_off", Icons.Default.VisibilityOff, "隐藏"),
            PaletteIcon("shield", Icons.Default.Shield, "盾牌"),
            PaletteIcon("lock", Icons.Default.Lock, "锁定"),
        )),
        IconPaletteGroup("导航", listOf(
            PaletteIcon("home", Icons.Default.Home, "主页"),
            PaletteIcon("arrow_back", Icons.AutoMirrored.Filled.ArrowBack, "返回"),
            PaletteIcon("arrow_forward", Icons.AutoMirrored.Filled.ArrowForward, "前进"),
            PaletteIcon("menu", Icons.Default.Menu, "菜单"),
            PaletteIcon("apps", Icons.Default.Apps, "应用网格"),
            PaletteIcon("widgets", Icons.Default.Widgets, "小组件"),
            PaletteIcon("dashboard", Icons.Default.Dashboard, "仪表盘"),
            PaletteIcon("view_carousel", Icons.Default.ViewCarousel, "轮播"),
            PaletteIcon("open_in_new", Icons.AutoMirrored.Filled.OpenInNew, "打开"),
            PaletteIcon("shortcut", Icons.AutoMirrored.Default.Shortcut, "快捷方式"),
            PaletteIcon("swap_horiz", Icons.Default.SwapHoriz, "左右切换"),
            PaletteIcon("touch_app", Icons.Default.TouchApp, "点击"),
            PaletteIcon("gesture", Icons.Default.Gesture, "手势"),
        )),
        IconPaletteGroup("媒体", listOf(
            PaletteIcon("play_arrow", Icons.Default.PlayArrow, "播放"),
            PaletteIcon("pause", Icons.Default.Pause, "暂停"),
            PaletteIcon("play_pause", Icons.Default.PlayPause, "播放/暂停"),
            PaletteIcon("play_circle", Icons.Default.PlayCircle, "播放(圆)"),
            PaletteIcon("pause_circle", Icons.Default.PauseCircle, "暂停(圆)"),
            PaletteIcon("stop_circle", Icons.Default.StopCircle, "停止(圆)"),
            PaletteIcon("skip_next", Icons.Default.SkipNext, "下一首"),
            PaletteIcon("skip_previous", Icons.Default.SkipPrevious, "上一首"),
            PaletteIcon("fast_forward", Icons.Default.FastForward, "快进"),
            PaletteIcon("volume_up", Icons.AutoMirrored.Default.VolumeUp, "音量"),
            PaletteIcon("music_note", Icons.Default.MusicNote, "音乐"),
        )),
        IconPaletteGroup("应用", listOf(
            PaletteIcon("info", Icons.Default.Info, "信息"),
            PaletteIcon("share", Icons.Default.Share, "分享"),
            PaletteIcon("upload", Icons.Default.Upload, "上传"),
            PaletteIcon("description", Icons.Default.Description, "文档"),
            PaletteIcon("assistant", Icons.Default.Assistant, "助手"),
            PaletteIcon("web", Icons.Default.Web, "网页"),
            PaletteIcon("public", Icons.Default.Public, "公网"),
            PaletteIcon("mail", Icons.Default.Mail, "邮件"),
        )),
        IconPaletteGroup("操作", listOf(
            PaletteIcon("content_copy", Icons.Default.ContentCopy, "复制"),
            PaletteIcon("content_paste", Icons.Default.ContentPaste, "粘贴"),
            PaletteIcon("delete", Icons.Default.Delete, "删除"),
            PaletteIcon("delete_forever", Icons.Default.DeleteForever, "永久删除"),
            PaletteIcon("edit", Icons.Default.Edit, "编辑"),
            PaletteIcon("save", Icons.Default.Save, "保存"),
            PaletteIcon("search", Icons.Default.Search, "搜索"),
            PaletteIcon("done", Icons.Default.Done, "完成"),
            PaletteIcon("add", Icons.Default.Add, "添加"),
            PaletteIcon("remove", Icons.Default.Remove, "移除"),
            PaletteIcon("clear", Icons.Default.Clear, "清除"),
        )),
        IconPaletteGroup("设备", listOf(
            PaletteIcon("flashlight_on", Icons.Default.FlashlightOn, "手电筒"),
            PaletteIcon("vibration", Icons.Default.Vibration, "震动"),
            PaletteIcon("screen_lock_portrait", Icons.Default.ScreenLockPortrait, "竖屏锁定"),
            PaletteIcon("screenshot", Icons.Default.Screenshot, "截图"),
            PaletteIcon("splitscreen", Icons.Default.Splitscreen, "分屏"),
            PaletteIcon("wifi", Icons.Default.Wifi, "Wi-Fi"),
            PaletteIcon("bluetooth", Icons.Default.Bluetooth, "蓝牙"),
            PaletteIcon("battery_full", Icons.Default.BatteryFull, "电池"),
            PaletteIcon("camera", Icons.Default.Camera, "相机"),
            PaletteIcon("notifications", Icons.Default.Notifications, "通知"),
        )),
    )

    val all: List<PaletteIcon> = groups.flatMap { it.icons }

    private val byKeyMap: Map<String, ImageVector> = all.associate { it.key to it.vector }

    fun byKey(key: String?): ImageVector? = key?.let { byKeyMap[it] }

    fun matchesQuery(icon: PaletteIcon, query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim()
        return icon.label.contains(needle, ignoreCase = true) ||
            icon.key.contains(needle, ignoreCase = true)
    }
}
