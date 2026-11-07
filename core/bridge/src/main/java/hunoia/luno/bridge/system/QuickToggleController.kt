package hunoia.luno.bridge.system

import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.provider.Settings
import hunoia.luno.core.domain.QuickToggleKind
import hunoia.luno.shizuku.ShizukuFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 系统快捷开关的读写口（`system.toggle` 的唯一实现）。
 *
 * 三条通道，按能力从高到低：
 * 1. 应用自身 API（勿扰已授权）：不需要额外能力，直接写。
 * 2. Shizuku shell（Wi-Fi/蓝牙/移动数据/飞行模式/定位/自动旋转/深色模式/勿扰未授权）：
 *    Android 10+ 起第三方应用切不动这些开关，shell 是唯一非 root 通道。
 * 3. 系统面板 / 设置页（[fallbackIntent]）：写不进去时的降级出口，不硬调私有 API。
 *
 * 写入一律「读状态 → 取反 → 写 → 读回确认」：命令退出码为 0 不等于系统状态真的变了
 * （部分 ROM 会静默忽略），读回不一致按失败处理，由调用方降级。
 */
object QuickToggleController {

    /** 读回确认：次数 × 间隔。Wi-Fi/蓝牙的状态广播有延迟，给到 ~1s。 */
    private const val VERIFY_ATTEMPTS = 5
    private const val VERIFY_DELAY_MS = 200L

    /** 该开关是否有直接写入通道；热点只能打开设置页。 */
    fun canToggle(kind: QuickToggleKind): Boolean = kind != QuickToggleKind.HOTSPOT

    /** 当前是否开启；读不到（或不适用）返回 null。 */
    fun isOn(context: Context, kind: QuickToggleKind): Boolean? = runCatching {
        when (kind) {
            QuickToggleKind.WIFI ->
                context.getSystemService(WifiManager::class.java)?.isWifiEnabled
            QuickToggleKind.BLUETOOTH -> intFlag(Settings.Global.getInt(
                context.contentResolver, "bluetooth_on", -1,
            ))
            QuickToggleKind.DND -> intFlag(Settings.Global.getInt(
                context.contentResolver, "zen_mode", 0,
            ))
            QuickToggleKind.MOBILE_DATA -> mobileDataOn(context)
            QuickToggleKind.AIRPLANE -> intFlag(Settings.Global.getInt(
                context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, -1,
            ))
            QuickToggleKind.LOCATION -> intFlag(Settings.Secure.getInt(
                context.contentResolver, "location_mode", -1,
            ))
            QuickToggleKind.HOTSPOT -> null
            QuickToggleKind.AUTO_ROTATE -> intFlag(Settings.System.getInt(
                context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, -1,
            ))
            QuickToggleKind.DARK_MODE -> nightModeOn(context)
        }
    }.getOrNull()

    /** 写入并读回确认；false = 没写成功或状态没变，调用方负责降级。 */
    suspend fun setOn(context: Context, kind: QuickToggleKind, on: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val written = when (kind) {
                QuickToggleKind.WIFI -> shell("svc wifi ${if (on) "enable" else "disable"}")
                QuickToggleKind.BLUETOOTH -> shell("svc bluetooth ${if (on) "enable" else "disable"}")
                QuickToggleKind.DND -> setDnd(context, on)
                QuickToggleKind.MOBILE_DATA -> shell("svc data ${if (on) "enable" else "disable"}")
                QuickToggleKind.AIRPLANE ->
                    shell("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
                QuickToggleKind.LOCATION -> shell("settings put secure location_mode ${if (on) 3 else 0}")
                QuickToggleKind.HOTSPOT -> false
                QuickToggleKind.AUTO_ROTATE ->
                    shell("settings put system accelerometer_rotation ${if (on) 1 else 0}")
                QuickToggleKind.DARK_MODE -> shell("cmd uimode night ${if (on) "yes" else "no"}")
            }
            if (!written) return@withContext false
            confirm(context, kind, on)
        }

    /** 降级出口：系统面板或对应设置页；调用方负责 startActivity 与失败反馈。 */
    fun fallbackIntent(kind: QuickToggleKind): Intent {
        val action = when (kind) {
            QuickToggleKind.WIFI -> Settings.Panel.ACTION_WIFI
            QuickToggleKind.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            QuickToggleKind.DND -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            QuickToggleKind.MOBILE_DATA -> Settings.ACTION_DATA_ROAMING_SETTINGS
            QuickToggleKind.AIRPLANE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            QuickToggleKind.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            QuickToggleKind.HOTSPOT -> "android.settings.TETHER_SETTINGS"
            QuickToggleKind.AUTO_ROTATE -> Settings.ACTION_DISPLAY_SETTINGS
            QuickToggleKind.DARK_MODE -> Settings.ACTION_DISPLAY_SETTINGS
        }
        return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private suspend fun confirm(context: Context, kind: QuickToggleKind, expected: Boolean): Boolean {
        if (!canToggle(kind)) return false
        repeat(VERIFY_ATTEMPTS) {
            if (isOn(context, kind) == expected) return true
            delay(VERIFY_DELAY_MS)
        }
        return isOn(context, kind) == expected
    }

    /** -1/缺省 = 读不到（多卡、ROM 未写该键等），不能当成「关」。 */
    private fun intFlag(value: Int): Boolean? = when {
        value < 0 -> null
        else -> value != 0
    }

    private fun nightModeOn(context: Context): Boolean? {
        // UiModeManager 的持久值（`cmd uimode night` 写的就是它）：1=no，2=yes，0=auto。
        // 注意不能用 Configuration.UI_MODE_NIGHT_YES（0x20）比较，两者量纲不同。
        val stored = Settings.Secure.getInt(context.contentResolver, "ui_night_mode", -1)
        if (stored >= 0) return stored == UiModeManager.MODE_NIGHT_YES
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 移动数据：多卡机型/部分 ROM 把真实状态写在 `mobile_data<subId>`，全局
     * `mobile_data` 可能恒为 0（本机 HyperOS 就是如此）。按子号聚合，全无再回退全局。
     */
    private fun mobileDataOn(context: Context): Boolean? {
        val perSub = (0..3).mapNotNull { sub ->
            val value = Settings.Global.getInt(context.contentResolver, "mobile_data$sub", -1)
            if (value < 0) null else value == 1
        }
        if (perSub.isNotEmpty()) return perSub.any { it }
        return intFlag(Settings.Global.getInt(context.contentResolver, "mobile_data", -1))
    }

    private suspend fun setDnd(context: Context, on: Boolean): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        // 已授权就走自家 API（不需 Shizuku）；未授权退回 shell 的 NotificationShellCmd。
        if (manager.isNotificationPolicyAccessGranted) {
            val filter = if (on) {
                NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            return runCatching { manager.setInterruptionFilter(filter) }.isSuccess
        }
        return shell("cmd notification set_dnd ${if (on) "on" else "off"}")
    }

    private suspend fun shell(command: String): Boolean =
        ShizukuFacade.isReady() && ShizukuFacade.runShellCommand(command).success
}
