package hunoia.luno.capability.notify

import android.content.Context
import android.os.Build
import android.provider.Settings
import hunoia.luno.core.domain.IslandCapability
import java.util.Locale

/**
 * ISLAND（超级岛）设备能力探测。
 *
 * 探测条件（全部满足才 [IslandCapability.READY]）：
 * 1. `Build.MANUFACTURER` 含 xiaomi；
 * 2. `Settings.System notification_focus_protocol >= 3`（焦点通知 V3 协议）；
 * 3. 系统属性 `persist.sys.feature.island` 为 true；
 * 4. `com.xiaomi.xmsf` 可见（绕过目标是它）。
 *
 * 注意：不探测 `updateAurogonUidRule`——2026-12 实测（HyperOS 4 / Android 17）aurogon
 * 规则挡不住 XMSF 已建立的 MiPush 长连接，鉴权仍会完成并失败（见 docs/archive.md §13）。
 * 有效绕过是 OEM_DENY_3 防火墙链（见 [XmsfGate]），由发布时经 Shizuku shell 执行，
 * Shizuku 不可用时发布器走回退链，不在此探测。
 *
 * 结果进程内缓存：探测是纯读，设备状态不会在一次会话内变化。
 */
internal class IslandProbe(private val appContext: Context) {

    val capability: IslandCapability by lazy { detect() }

    /** XMSF（com.xiaomi.xmsf）包名；不可见时 null（无法绕过）。 */
    val xmsfPackage: String? by lazy { resolveXmsfPackage() }

    private fun detect(): IslandCapability {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)
        if (!manufacturer.contains("xiaomi")) return IslandCapability.OTHER_ANDROID

        val protocol = runCatching {
            Settings.System.getInt(appContext.contentResolver, FOCUS_PROTOCOL_SETTING, 0)
        }.getOrDefault(0)
        if (protocol < 3) return IslandCapability.FALLBACK
        if (xmsfPackage == null) return IslandCapability.FALLBACK
        if (!islandFeatureEnabled()) return IslandCapability.FALLBACK
        return IslandCapability.READY
    }

    private fun islandFeatureEnabled(): Boolean = runCatching {
        val sp = Class.forName("android.os.SystemProperties").let { clazz ->
            clazz.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.java)
                .apply { isAccessible = true }
                .invoke(null, ISLAND_FEATURE_PROPERTY, false) as? Boolean
        }
        sp == true
    }.getOrDefault(false)

    private fun resolveXmsfPackage(): String? = runCatching {
        appContext.packageManager.getPackageInfo(XMSF_PACKAGE, 0)
        XMSF_PACKAGE
    }.getOrNull()

    private companion object {
        const val XMSF_PACKAGE = "com.xiaomi.xmsf"
        const val FOCUS_PROTOCOL_SETTING = "notification_focus_protocol"
        const val ISLAND_FEATURE_PROPERTY = "persist.sys.feature.island"
    }
}
