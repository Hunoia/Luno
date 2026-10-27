package hunoia.luno.core.domain

/**
 * 启动能力契约（`AppLauncher`）与它的输入 [LaunchSpec]。
 *
 * 归属判据（architecture_future §4.2「按变化原因分组」）：启动机制的变化原因是
 * 「平台启动策略」（解冻、小窗、快捷方式、Intent 覆盖），与执行内核的变化原因不同，
 * 所以默认实现住 `:capability:launch`，内核只持有这个契约。
 *
 * 这里不出现 `MiniWindowSettings` 等配置模型：小窗尺寸/位置由实现侧读配置快照，
 * 契约只表达「按什么参数启动」。
 */
data class LaunchSpec(
    val packageName: String,
    val className: String = "",
    val miniWindow: Boolean = false,
    /** 空 = 不覆盖系统默认启动行为。 */
    val intentAction: String = "",
    val data: String = "",
    val extras: Map<String, String> = emptyMap(),
)

/**
 * 启动能力：应用、快捷方式、弹窗、链接。
 *
 * 实现方负责解冻被禁用应用、小窗/弹窗参数、用量统计（经 [LaunchTelemetry]），
 * 调用方只表达「起什么」。
 */
interface AppLauncher {
    suspend fun launch(spec: LaunchSpec): Boolean

    /** 启动已声明的 App Shortcut（入参是落库的快捷方式 JSON）。 */
    suspend fun launchShortcut(dataJson: String): Boolean

    /** 当前应用以小窗打开；包名/Activity 解析与全局设置由实现侧负责。 */
    suspend fun launchInPopup(packageName: String): ActionResult

    /** 打开链接（含 Query 参数与小窗）。 */
    suspend fun openUrl(
        url: String,
        miniWindow: Boolean,
        queryParameters: List<QueryParam> = emptyList(),
    ): Boolean

    /** 打开应用详情页（设置页）。 */
    suspend fun openAppDetails(packageName: String): ActionResult
}

/**
 * 启动用量统计：配置流写入，不属于任何扩展面。
 *
 * 依据 architecture_future §二「配置写入不构造 ActionRequest，不走 Dispatcher」，
 * 实现放 `:core:data`（`ConfigProvider.recordAppLaunch` 的薄包装），由 `:app` 注入能力库。
 */
fun interface LaunchTelemetry {
    fun recordLaunch(packageName: String, source: AppLaunchSource)
}
