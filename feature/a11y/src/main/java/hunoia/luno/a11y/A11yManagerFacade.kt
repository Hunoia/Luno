package hunoia.luno.a11y

import android.content.ComponentName
import android.content.Context
import hunoia.luno.bridge.system.AccessibilitySettings

/**
 * 无障碍服务管理门面：枚举设备上全部已安装服务并投影成 [A11yServiceEntry]，
 * 启停统一走 `:core:bridge` 的 `AccessibilitySettings.setEnabled`（原子读-改-写，
 * 保留其它服务的 token）。
 *
 * 边界：只读取与启停，**不守护、不回写、不构造 `ActionRequest`** —— 保活恢复仍是
 * `DaemonService.restoreIfNeeded` 的单一实现（架构 §10），启停属于配置写入（§1.3）。
 */
object A11yManagerFacade {

    /** 已安装服务列表（含未启用），按 [sortServices] 排好序；需要 `QUERY_ALL_PACKAGES`。 */
    fun loadServices(context: Context): List<A11yServiceEntry> {
        val appContext = context.applicationContext
        val enabled = AccessibilitySettings.enabledComponents(appContext)
        return sortServices(
            AccessibilitySettings.installedServices(appContext).map { info ->
                A11yServiceEntry(
                    id = info.id,
                    label = info.label,
                    description = info.description,
                    packageName = info.packageName,
                    enabled = info.component in enabled,
                    isOwnService = info.packageName == appContext.packageName,
                )
            },
        )
    }

    /**
     * 启停一个服务。返回 false = 没写成（缺 `WRITE_SECURE_SETTINGS`、id 非法或系统拒绝），
     * 调用方据此提示并重查状态；已处于目标状态时幂等返回 true。
     */
    fun setEnabled(context: Context, id: String, enable: Boolean): Boolean {
        val appContext = context.applicationContext
        if (!AccessibilitySettings.hasWriteSecureSettings(appContext)) return false
        val component = ComponentName.unflattenFromString(id) ?: return false
        return AccessibilitySettings.setEnabled(appContext, component, enable)
    }
}

/** 纯字母序：只看 [A11yServiceEntry.label]，与启用状态、包名、是否本应用无关。 */
internal fun sortServices(services: List<A11yServiceEntry>): List<A11yServiceEntry> =
    services.sortedBy { it.label.lowercase() }
