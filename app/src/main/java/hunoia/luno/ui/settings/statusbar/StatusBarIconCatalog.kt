package hunoia.luno.ui.settings.statusbar

import androidx.annotation.StringRes
import hunoia.luno.R

/**
 * 状态栏 slot 目录：`key` 是 SystemUI 的 slot 名（写进 `icon_blacklist` 的字面量），
 * 文案只在 UI 层维护，domain 不认 slot 名单。
 *
 * [common] 对齐系统调节工具暴露的 12 项；[more] 是 MIUI 状态栏里另外能藏的系统图标。
 * 不在此表里的 key 若是用户从别处写进去的，读写时原样保留、不清洗。
 */
internal data class StatusBarIconSlot(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val hintRes: Int? = null,
)

internal object StatusBarIconCatalog {

    val common: List<StatusBarIconSlot> = listOf(
        StatusBarIconSlot("mobile", R.string.status_bar_icon_mobile, R.string.status_bar_icon_mobile_hint),
        StatusBarIconSlot("wifi", R.string.status_bar_icon_wifi),
        StatusBarIconSlot("bluetooth", R.string.status_bar_icon_bluetooth),
        StatusBarIconSlot("hotspot", R.string.status_bar_icon_hotspot),
        StatusBarIconSlot("cast", R.string.status_bar_icon_cast),
        StatusBarIconSlot("managed_profile", R.string.status_bar_icon_managed_profile),
        StatusBarIconSlot("headset", R.string.status_bar_icon_headset),
        StatusBarIconSlot("rotate", R.string.status_bar_icon_rotate),
        StatusBarIconSlot("volume", R.string.status_bar_icon_volume),
        StatusBarIconSlot("zen", R.string.status_bar_icon_zen),
        StatusBarIconSlot("camera", R.string.status_bar_icon_camera),
        StatusBarIconSlot("ethernet", R.string.status_bar_icon_ethernet),
    )

    val more: List<StatusBarIconSlot> = listOf(
        StatusBarIconSlot("network_speed", R.string.status_bar_icon_network_speed),
        StatusBarIconSlot("hd", R.string.status_bar_icon_hd, R.string.status_bar_icon_hd_hint),
        StatusBarIconSlot("no_sim", R.string.status_bar_icon_no_sim),
        StatusBarIconSlot("vpn", R.string.status_bar_icon_vpn),
        StatusBarIconSlot("location", R.string.status_bar_icon_location),
        StatusBarIconSlot("nfc", R.string.status_bar_icon_nfc),
        StatusBarIconSlot("alarm_clock", R.string.status_bar_icon_alarm_clock),
        StatusBarIconSlot("mute", R.string.status_bar_icon_mute),
        StatusBarIconSlot("ime", R.string.status_bar_icon_ime),
        StatusBarIconSlot("privacy_mode", R.string.status_bar_icon_privacy_mode),
        StatusBarIconSlot("stealth", R.string.status_bar_icon_stealth, R.string.status_bar_icon_stealth_hint),
        StatusBarIconSlot("second_space", R.string.status_bar_icon_second_space),
        StatusBarIconSlot("micphone", R.string.status_bar_icon_microphone),
        StatusBarIconSlot("handle", R.string.status_bar_icon_handle),
        StatusBarIconSlot("dist_compute", R.string.status_bar_icon_dist_compute),
        StatusBarIconSlot("tty", R.string.status_bar_icon_tty),
        StatusBarIconSlot("wireless_headset", R.string.status_bar_icon_wireless_headset),
        StatusBarIconSlot("glasses", R.string.status_bar_icon_glasses),
        StatusBarIconSlot("pad", R.string.status_bar_icon_pad),
        StatusBarIconSlot("pc", R.string.status_bar_icon_pc),
        StatusBarIconSlot("phone", R.string.status_bar_icon_phone),
        StatusBarIconSlot("tv", R.string.status_bar_icon_tv),
        StatusBarIconSlot("car", R.string.status_bar_icon_car),
        StatusBarIconSlot("sound_box", R.string.status_bar_icon_sound_box),
        StatusBarIconSlot("sound_box_screen", R.string.status_bar_icon_sound_box_screen),
        StatusBarIconSlot("sound_box_group", R.string.status_bar_icon_sound_box_group),
        StatusBarIconSlot("stereo", R.string.status_bar_icon_stereo),
    )
}
