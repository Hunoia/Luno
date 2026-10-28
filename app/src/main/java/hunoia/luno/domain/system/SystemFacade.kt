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
}
