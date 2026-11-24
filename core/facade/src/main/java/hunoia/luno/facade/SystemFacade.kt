package hunoia.luno.facade

import android.content.Context
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.core.effect.AlertEffectHandle
import hunoia.luno.core.effect.AlertEffectRequest
import kotlinx.coroutines.flow.Flow

/**
 * 系统能力门面契约：权限、保活、悬浮球服务启停（UI 唯一入口）。
 */
interface SystemFacade {

    suspend fun setKeepAliveEnabled(
        context: Context,
        enabled: Boolean,
        onPermissionRequired: (Int) -> Unit,
    ): Boolean

    suspend fun enableAccessibility(context: Context): Boolean

    fun setFloatBallEnabled(context: Context, enabled: Boolean)
    fun refreshFloatBall(context: Context)

    /**
     * 试播一次通知胶囊的到达提示动效（按当前落盘配置；服务没在跑时不播）。
     * [layerId] 非空时只播预设里的这一层；失败原因由服务侧 Toast 反馈。
     */
    fun previewFloatBallAlert(context: Context, layerId: String? = null)

    /**
     * 试播一份**未落盘**的预设（预设库 / 编辑器用），不写配置。
     * [layerId] 非空时只播该层。
     */
    fun previewFloatBallAlertPreset(
        context: Context,
        preset: AlertPreset,
        layerId: String? = null,
    )

    /**
     * 播放一次通用效果（外部调用 / 试播共用）。
     *
     * 服务在跑 → 转发到 `:service`（Intent + JSON）；未在跑 → 由调用进程托管轻量效果窗
     * （architecture §1.5 登记例外）。返回 null = 并发策略拒绝或不可用。
     */
    fun playAlertEffect(context: Context, request: AlertEffectRequest): AlertEffectHandle?

    /** 总闸重新打开后按落盘配置恢复悬浮球宿主（球 / 通知 / 剪贴板任一开启才拉起）。 */
    suspend fun restoreFloatBall(context: Context)

    /** 通知使用权是否已授（通知胶囊 / 媒体控制共用）。 */
    fun isNotificationAccessGranted(context: Context): Boolean

    suspend fun loadHomePermissionState(context: Context): HomePermissionState

    /** 各项特权授权的当前状态（悬浮窗 / 无障碍 / 通知监听 / 系统设置写入 / 电池白名单 / 通知）。 */
    fun privilegedPermissionStatus(context: Context): List<PrivilegedPermissionStatus>

    /** 该项对应的系统设置页；没有可跳页面时返回 null（如 `WRITE_SECURE_SETTINGS`）。 */
    fun privilegedPermissionIntent(context: Context, permission: PrivilegedPermission): android.content.Intent?

    /** 用 Shizuku 只授这一项；返回是否真的授上了。 */
    suspend fun grantPrivilegedPermission(context: Context, permission: PrivilegedPermission): Boolean

    /** 一键补齐：返回成功/失败计数，没拿到 Shizuku 时 total=0。 */
    suspend fun grantAllPrivilegedPermissions(context: Context): PrivilegedGrantSummary

    /**
     * 只授指定项，权限范围由调用方决定。
     *
     * 与 [grantAllPrivilegedPermissions] 的区别是**不静默扩权**：后者会连带授予清单里
     * 声明的全部运行期权限（相机 / 通讯录 / 短信等），主页「一键授权」不能走那条路。
     */
    suspend fun grantPrivilegedPermissions(
        context: Context,
        permissions: List<PrivilegedPermission>,
    ): PrivilegedGrantSummary

    /** 系统字号（`font_scale`），范围 [hunoia.luno.bridge.system.SystemFontScale.MIN]..[hunoia.luno.bridge.system.SystemFontScale.MAX]。 */
    fun fontScale(context: Context): Float

    /** `WRITE_SETTINGS` 是否已授权；未授权时只能先跳系统设置页。 */
    fun canWriteSystemSettings(context: Context): Boolean

    /** 写入系统字号；失败返回 false，系统值保持不变。 */
    fun setFontScale(context: Context, scale: Float): Boolean

    /** Luno 主页面是否从最近任务隐藏；图片标注等独立页面不受影响。 */
    val hideLunoFromRecents: Flow<Boolean>

    /** 落盘「隐藏 Luno 后台」开关。 */
    suspend fun setHideLunoFromRecents(hidden: Boolean)

    /** 把当前主 task 立即设为隐藏 / 显示。 */
    fun applyHideLunoFromRecents(context: Context, taskId: Int, hidden: Boolean): Boolean
}

/** `SystemFacade` 的默认实现：只做委托，不引入新的状态。 */
