package hunoia.luno.bridge.system

import hunoia.luno.core.LunoLog as Log
import hunoia.luno.shizuku.ShizukuFacade

/**
 * 默认桌面（HOME 角色）与导航方式的 shell 出口：临时桌面自动切换、恢复与导航还原。
 *
 * 全部一发一收（[ShizukuFacade.runShellCommand]，自带超时预算），任一失败静默降级为
 * false / null——调用方据此回退手动流程，绝不把业务压在特权通道后面（§7.4 纪律）。
 * Shizuku 未就绪时同样降级：本控制器只让切换免手点，不承载任何必需能力。
 */
object HomeRoleController {

    private const val TAG = "HomeRoleController"
    private const val ROLE_HOME = "android.app.role.HOME"
    private const val NAV_MODE_KEY = "navigation_mode"

    /** 当前 HOME 角色持有者（系统返回顺序，首位即默认桌面）；读不到为空表。 */
    suspend fun holders(): List<String> = runCatching {
        ShizukuFacade.runShellCommand("cmd role get-role-holders $ROLE_HOME")
            .takeIf { it.isSuccess }
            ?.stdout
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.split('\n')
            ?.map { it.trim() }
            .orEmpty()
    }.getOrDefault(emptyList())

    /** Shizuku 可用（控制器全量依赖它，未就绪时所有操作直接降级）。 */
    fun available(): Boolean = ShizukuFacade.isReady()

    /**
     * 把默认桌面切到 [packageName]：优先 `cmd role add-role-holder`，成功后再校验
     * holders 首位（默认桌面）命中；首位不中（角色追加语义）回退
     * `cmd package set-home-activity`（[homeComponent] 为该包的 HOME 入口组件）。
     */
    suspend fun setHomeTo(packageName: String, homeComponent: String?): Boolean = runCatching {
        ShizukuFacade.runShellCommand("cmd role add-role-holder $ROLE_HOME ${shellQuote(packageName)}")
        if (holders().firstOrNull() == packageName) return true
        if (homeComponent != null) {
            ShizukuFacade.runShellCommand(
                "cmd package set-home-activity ${shellQuote(homeComponent)}",
            )
        }
        holders().firstOrNull() == packageName
    }.getOrElse {
        Log.w(TAG, "set home to $packageName failed", it)
        false
    }

    /**
     * 恢复切换前的桌面：先摘掉自己，再逐个补齐快照里的持有者并校验首位。
     * 任何一步失败返回 false，由调用方回退系统设置页。
     */
    suspend fun restoreHome(selfPackage: String, snapshotHolders: List<String>): Boolean = runCatching {
        if (holders().contains(selfPackage)) {
            ShizukuFacade.runShellCommand("cmd role remove-role-holder $ROLE_HOME ${shellQuote(selfPackage)}")
        }
        snapshotHolders.forEach { holder ->
            if (holder.isNotBlank() && !holders().contains(holder)) {
                ShizukuFacade.runShellCommand("cmd role add-role-holder $ROLE_HOME ${shellQuote(holder)}")
            }
        }
        val restored = holders()
        restored.firstOrNull() in snapshotHolders && selfPackage !in restored
    }.getOrElse {
        Log.w(TAG, "restore home failed holders=$snapshotHolders", it)
        false
    }

    /** 当前导航方式；读不到（键不存在 / Shizuku 不可用）为 null，恢复时跳过。 */
    suspend fun navigationMode(): String? = runCatching {
        ShizukuFacade.runShellCommand("settings get secure $NAV_MODE_KEY")
            .takeIf { it.isSuccess }
            ?.stdout
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
    }.getOrNull()

    /** 写回导航方式（切回原桌面后还原手势）。 */
    suspend fun setNavigationMode(value: String): Boolean = runCatching {
        ShizukuFacade.runShellCommand("settings put secure $NAV_MODE_KEY ${shellQuote(value)}").isSuccess
    }.getOrDefault(false)

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
