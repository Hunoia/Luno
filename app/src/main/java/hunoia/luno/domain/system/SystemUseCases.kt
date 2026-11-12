package hunoia.luno.domain.system

import hunoia.luno.facade.SystemFacade
import hunoia.luno.facade.HomePermissionState
import hunoia.luno.facade.PrivilegedGrantSummary
import hunoia.luno.facade.PrivilegedPermission
import hunoia.luno.facade.PrivilegedPermissionStatus
import android.content.Context
import kotlinx.coroutines.flow.Flow
object SystemUseCases : SystemFacade {

    override suspend fun setKeepAliveEnabled(
        context: Context,
        enabled: Boolean,
        onPermissionRequired: (Int) -> Unit,
    ): Boolean = KeepAliveUseCase.setEnabled(context, enabled, onPermissionRequired)

    override suspend fun enableAccessibility(context: Context): Boolean =
        EnableAccessibilityUseCase.enable(context, forceRebind = true)

    override fun setFloatBallEnabled(context: Context, enabled: Boolean) =
        FloatBallUseCase.setEnabled(context, enabled)

    override fun refreshFloatBall(context: Context) = FloatBallUseCase.refresh(context)

    override suspend fun restoreFloatBall(context: Context) =
        FloatBallUseCase.restoreIfEnabled(context)

    override fun isNotificationAccessGranted(context: Context): Boolean =
        hunoia.luno.bridge.system.NotificationAccess.isGranted(context)

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

    override fun fontScale(context: Context): Float =
        hunoia.luno.bridge.system.SystemFontScale.get(context)

    override fun canWriteSystemSettings(context: Context): Boolean =
        hunoia.luno.bridge.system.SystemFontScale.canWrite(context)

    override fun setFontScale(context: Context, scale: Float): Boolean =
        hunoia.luno.bridge.system.SystemFontScale.set(context, scale)

    override val hideLunoFromRecents: Flow<Boolean> = LunoRecentsUseCase.hideFromRecents

    override suspend fun setHideLunoFromRecents(hidden: Boolean) =
        LunoRecentsUseCase.setHidden(hidden)

    override fun applyHideLunoFromRecents(context: Context, taskId: Int, hidden: Boolean): Boolean =
        LunoRecentsUseCase.apply(context, taskId, hidden)
}
