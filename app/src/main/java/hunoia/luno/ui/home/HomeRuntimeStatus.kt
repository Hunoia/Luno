package hunoia.luno.ui.home

import hunoia.luno.shizuku.ShizukuStatus

enum class HomeRuntimeStatusLevel {
    Running,
    NeedsAttention,
    Unavailable,
}

enum class HomeStatusItemType {
    Accessibility,
    Gesture,
    KeepAlive,
    Shizuku,
}

enum class HomePrimaryIssue {
    None,
    AccessibilityDisabled,
    GestureDisabled,
    ShizukuNotInstalled,
    ShizukuNotRunning,
    ShizukuNotAuthorized,
    KeepAliveDisabled,
}

enum class HomeRuntimeAction {
    None,
    OpenAccessibility,
    EnableGesture,
    RequestShizukuPermission,
    RefreshStatus,
    EnableKeepAlive,
}

enum class HomeShizukuUiStatus {
    NotInstalled,
    NotRunning,
    NotAuthorized,
    Authorized,
}

data class HomeStatusItem(
    val type: HomeStatusItemType,
    val available: Boolean,
    val shizuku: HomeShizukuUiStatus? = null,
)

data class HomeRuntimeStatus(
    val level: HomeRuntimeStatusLevel,
    val primaryIssue: HomePrimaryIssue,
    val shizuku: HomeShizukuUiStatus,
    val items: List<HomeStatusItem>,
    val primaryAction: HomeRuntimeAction,
    val secondaryAction: HomeRuntimeAction = HomeRuntimeAction.None,
)

object HomeRuntimeStatusMapper {

    fun map(
        isAccessibilityEnabled: Boolean,
        isGestureSwitchEnabled: Boolean,
        isKeepAliveEnabled: Boolean,
        shizukuStatus: ShizukuStatus,
    ): HomeRuntimeStatus {
        val shizukuUiStatus = shizukuStatus.toUiStatus()
        val primaryIssue = when {
            !isAccessibilityEnabled -> HomePrimaryIssue.AccessibilityDisabled
            !isGestureSwitchEnabled -> HomePrimaryIssue.GestureDisabled
            shizukuUiStatus == HomeShizukuUiStatus.NotInstalled -> HomePrimaryIssue.ShizukuNotInstalled
            shizukuUiStatus == HomeShizukuUiStatus.NotRunning -> HomePrimaryIssue.ShizukuNotRunning
            shizukuUiStatus == HomeShizukuUiStatus.NotAuthorized -> HomePrimaryIssue.ShizukuNotAuthorized
            !isKeepAliveEnabled -> HomePrimaryIssue.KeepAliveDisabled
            else -> HomePrimaryIssue.None
        }
        val level = when (primaryIssue) {
            HomePrimaryIssue.None -> HomeRuntimeStatusLevel.Running
            HomePrimaryIssue.AccessibilityDisabled,
            HomePrimaryIssue.GestureDisabled -> HomeRuntimeStatusLevel.Unavailable
            HomePrimaryIssue.ShizukuNotInstalled,
            HomePrimaryIssue.ShizukuNotRunning,
            HomePrimaryIssue.ShizukuNotAuthorized,
            HomePrimaryIssue.KeepAliveDisabled -> HomeRuntimeStatusLevel.NeedsAttention
        }
        val primaryAction = when (primaryIssue) {
            HomePrimaryIssue.None -> HomeRuntimeAction.None
            HomePrimaryIssue.AccessibilityDisabled -> HomeRuntimeAction.OpenAccessibility
            HomePrimaryIssue.GestureDisabled -> HomeRuntimeAction.EnableGesture
            HomePrimaryIssue.ShizukuNotAuthorized -> HomeRuntimeAction.RequestShizukuPermission
            HomePrimaryIssue.ShizukuNotInstalled,
            HomePrimaryIssue.ShizukuNotRunning -> HomeRuntimeAction.RefreshStatus
            HomePrimaryIssue.KeepAliveDisabled -> HomeRuntimeAction.EnableKeepAlive
        }
        val secondaryAction = if (primaryIssue == HomePrimaryIssue.None) {
            HomeRuntimeAction.None
        } else {
            HomeRuntimeAction.RefreshStatus
        }
        return HomeRuntimeStatus(
            level = level,
            primaryIssue = primaryIssue,
            shizuku = shizukuUiStatus,
            items = listOf(
                HomeStatusItem(HomeStatusItemType.Accessibility, isAccessibilityEnabled),
                HomeStatusItem(HomeStatusItemType.Gesture, isGestureSwitchEnabled),
                HomeStatusItem(HomeStatusItemType.KeepAlive, isKeepAliveEnabled),
                HomeStatusItem(
                    type = HomeStatusItemType.Shizuku,
                    available = shizukuUiStatus == HomeShizukuUiStatus.Authorized,
                    shizuku = shizukuUiStatus,
                ),
            ),
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
        )
    }

    fun ShizukuStatus.toUiStatus(): HomeShizukuUiStatus = when {
        !installed -> HomeShizukuUiStatus.NotInstalled
        !binderAlive -> HomeShizukuUiStatus.NotRunning
        !permissionGranted -> HomeShizukuUiStatus.NotAuthorized
        else -> HomeShizukuUiStatus.Authorized
    }
}
