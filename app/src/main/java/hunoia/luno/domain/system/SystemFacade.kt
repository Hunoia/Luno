package hunoia.luno.domain.system

import android.content.Context

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

    suspend fun loadHomePermissionState(context: Context): HomePermissionState

    /** 各项特权授权的当前状态（悬浮窗 / 无障碍 / 通知监听 / 系统设置写入 / 电池白名单 / 通知）。 */
    fun privilegedPermissionStatus(context: Context): List<PrivilegedPermissionStatus>

    /** 该项对应的系统设置页；没有可跳页面时返回 null（如 `WRITE_SECURE_SETTINGS`）。 */
    fun privilegedPermissionIntent(context: Context, permission: PrivilegedPermission): android.content.Intent?

    /** 用 Shizuku 只授这一项；返回是否真的授上了。 */
    suspend fun grantPrivilegedPermission(context: Context, permission: PrivilegedPermission): Boolean

    /** 一键补齐：返回成功/失败计数，没拿到 Shizuku 时 total=0。 */
    suspend fun grantAllPrivilegedPermissions(context: Context): PrivilegedGrantSummary
}

/** `SystemFacade` 的默认实现：只做委托，不引入新的状态。 */
object SystemUseCases : SystemFacade {

    override suspend fun setKeepAliveEnabled(
        context: Context,
        enabled: Boolean,
        onPermissionRequired: (Int) -> Unit,
    ): Boolean = KeepAliveUseCase.setEnabled(context, enabled, onPermissionRequired)

    override suspend fun enableAccessibility(context: Context): Boolean =
        EnableAccessibilityUseCase.enable(context)

    override fun setFloatBallEnabled(context: Context, enabled: Boolean) =
        FloatBallUseCase.setEnabled(context, enabled)

    override fun refreshFloatBall(context: Context) = FloatBallUseCase.refresh(context)

    override suspend fun loadHomePermissionState(context: Context): HomePermissionState =
        PermissionStateUseCase.loadHomePermissionState(context)

    override fun privilegedPermissionStatus(context: Context): List<PrivilegedPermissionStatus> =
        PrivilegedPermissionUseCase.status(context)

    override fun privilegedPermissionIntent(
        context: Context,
        permission: PrivilegedPermission,
    ): android.content.Intent? = PrivilegedPermissionUseCase.settingsIntent(context, permission)

    override suspend fun grantPrivilegedPermission(
        context: Context,
        permission: PrivilegedPermission,
    ): Boolean = PrivilegedPermissionUseCase.grant(context, permission)

    override suspend fun grantAllPrivilegedPermissions(context: Context): PrivilegedGrantSummary =
        PrivilegedPermissionUseCase.grantAll(context)
}
