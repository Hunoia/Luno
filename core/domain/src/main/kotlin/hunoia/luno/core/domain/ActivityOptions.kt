package hunoia.luno.core.domain

/**
 * 动作编辑器需要的「指定应用内可启动 Activity」查询。
 *
 * 契约在 `:core:domain`，实现在 `:app`（依赖 `:feature:search` 的平台查询），
 * 经 `:core:runtime` 的安装点注入，这样 `:feature:actions` 不必反向依赖搜索模块。
 */
interface ActivityOptionsSource {

    /** 该包的 launcher Activity 类名；查不到返回 null。 */
    fun launcherPackageActivity(packageName: String): String?

    /** 该包可启动的 Activity；`label` 已按展示口径格式化。 */
    suspend fun activities(packageName: String, launcherActivity: String?): List<ActivityOptionItem>
}

data class ActivityOptionItem(
    val className: String,
    val label: String,
)
