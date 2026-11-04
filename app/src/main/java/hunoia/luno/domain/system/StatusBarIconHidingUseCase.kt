package hunoia.luno.domain.system

import android.content.Context
import hunoia.luno.bridge.system.StatusBarIcons
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 状态栏图标隐藏的页面门面：只读写 `Settings.Secure.icon_blacklist`。
 *
 * 边界（architecture §1.3）：这是配置写入，不构造 `ActionRequest`。
 * 真相在系统设置（SystemUI Tuner 写的是同一项），Luno 不另存配置、也不进备份；
 * 每次进页 / 回前台重读，用户在系统调节工具里改过的值能跟手。
 *
 * 能力边界：只对走 `StatusBarIconController` 的系统图标区生效；时钟、电池、
 * 电量百分比与通知图标不在这条路径上，页面里不提供开关。
 */
object StatusBarIconHidingUseCase {

    data class PageState(
        val hidden: Set<String> = emptySet(),
        val canWrite: Boolean = false,
    )

    fun load(context: Context): PageState = PageState(
        hidden = StatusBarIcons.read(context),
        canWrite = context.hasWriteSecureSettingsPermission(),
    )

    /** 单个 slot 开关；读写之间不留窗口，直接以最新系统值为基线。 */
    suspend fun setHidden(context: Context, slot: String, hidden: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val current = linkedSetOf<String>().apply { addAll(StatusBarIcons.read(context)) }
            if (hidden) current.add(slot) else current.remove(slot)
            StatusBarIcons.write(context, current)
        }

    /** 清空隐藏列表（等价于系统调节工具里把开关全部打开）。 */
    suspend fun showAll(context: Context): Boolean = withContext(Dispatchers.IO) {
        StatusBarIcons.write(context, emptyList())
    }
}
