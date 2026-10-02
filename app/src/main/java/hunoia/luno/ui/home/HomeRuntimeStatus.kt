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
    ShizukuNotRunning,
    ShizukuNotAuthorized,
    KeepAliveDisabled,
}

enum class HomeShizukuUiStatus {
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
            shizukuUiStatus == HomeShizukuUiStatus.NotRunning -> HomePrimaryIssue.ShizukuNotRunning
            shizukuUiStatus == HomeShizukuUiStatus.NotAuthorized -> HomePrimaryIssue.ShizukuNotAuthorized
            !isKeepAliveEnabled -> HomePrimaryIssue.KeepAliveDisabled
            else -> HomePrimaryIssue.None
        }
        val level = when (primaryIssue) {
            HomePrimaryIssue.None -> HomeRuntimeStatusLevel.Running
            HomePrimaryIssue.AccessibilityDisabled,
            HomePrimaryIssue.GestureDisabled -> HomeRuntimeStatusLevel.Unavailable
            HomePrimaryIssue.ShizukuNotRunning,
            HomePrimaryIssue.ShizukuNotAuthorized,
            HomePrimaryIssue.KeepAliveDisabled -> HomeRuntimeStatusLevel.NeedsAttention
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
        )
    }

    fun ShizukuStatus.toUiStatus(): HomeShizukuUiStatus = when {
        binderAlive && permissionGranted -> HomeShizukuUiStatus.Authorized
        binderAlive && !permissionGranted -> HomeShizukuUiStatus.NotAuthorized
        else -> HomeShizukuUiStatus.NotRunning
    }
}
