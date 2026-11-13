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

/**
 * 主页运行状态卡的主因：只放**运行态**（无障碍 / 手势总闸 / 保活）。
 *
 * Shizuku 不在其中：它只影响「能不能一键授权」，不缺权限时运行照常；把它放进运行状态卡
 * 会和权限卡说同一件事。Shizuku 的当前状态只用于权限卡的按钮降级。
 */
enum class HomePrimaryIssue {
    None,
    AccessibilityDisabled,
    GestureDisabled,
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
            !isKeepAliveEnabled -> HomePrimaryIssue.KeepAliveDisabled
            else -> HomePrimaryIssue.None
        }
        val level = when (primaryIssue) {
            HomePrimaryIssue.None -> HomeRuntimeStatusLevel.Running
            HomePrimaryIssue.AccessibilityDisabled,
            HomePrimaryIssue.GestureDisabled -> HomeRuntimeStatusLevel.Unavailable
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
