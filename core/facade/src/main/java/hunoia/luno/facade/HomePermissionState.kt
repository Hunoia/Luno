package hunoia.luno.facade

/** 首页需要的一次性权限快照（悬浮/无障碍/保活）。 */
data class HomePermissionState(
    val isGestureEnabled: Boolean,
    val isAccessibilityEnabled: Boolean,
    val isKeepAliveEnabled: Boolean,
)
