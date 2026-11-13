package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.Serializable

/**
 * 悬浮球配置。
 *
 * 挂在 [ActionSettings.floatBall] 下才能顺着 ConfigProvider → SettingsStore →
 * SettingsState 到达 `:service` 进程的 `FloatBallService`；挂到别的域会静默失效。
 * 位置坐标归一化（0..1），横竖屏各存一份，转屏后各用各的落点。
 * 按住即拖（无长按门槛），松手落位；点按 = 抬手未超 touchSlop 时执行 [tapAction]。
 */
@Serializable
@Keep
data class FloatBallPosition(
    /** 归一化横坐标（球窗顶边 / 屏宽）。 */
    val x: Float = FloatBallDefaults.X,
    /** 归一化纵坐标（球窗顶边 / 屏高）。 */
    val y: Float = FloatBallDefaults.Y,
    /** 左手侧球高度锚点（归一化 y，手动停靠时记忆）。null = 未记忆。 */
    val leftAnchorY: Float? = null,
    /** 右手侧球高度锚点。 */
    val rightAnchorY: Float? = null,
)

@Serializable
@Keep
data class CapsuleBallPosition(
    /** 竖屏落点；null = 尚未写过，按默认值/旧剪贴板球坐标推导。 */
    val portrait: FloatBallPosition? = null,
    /** 横屏落点；null = 尚未写过。 */
    val landscape: FloatBallPosition? = null,
) {
    fun position(landscape: Boolean): FloatBallPosition? =
        if (landscape) this.landscape else portrait

    /** 只改当前朝向那份落点；未写过时用 [base] 起手。 */
    fun withPosition(
        landscape: Boolean,
        base: FloatBallPosition,
        transform: (FloatBallPosition) -> FloatBallPosition,
    ): CapsuleBallPosition =
        if (landscape) copy(landscape = transform(this.landscape ?: base))
        else copy(portrait = transform(this.portrait ?: base))
}

@Serializable
@Keep
data class FloatBallSettings(
    val enabled: Boolean = FloatBallDefaults.Enabled,
    val sizeDp: Float = FloatBallDefaults.SizeDp,
    val color: Int = FloatBallDefaults.Color,
    val colorSource: ColorSource = ColorSource.Custom,
    val colorThemeKey: ThemeColorKey = ThemeColorKey.Primary,
    /** 单击球执行的动作。为 null 表示单击无响应。 */
    val tapAction: StoredAction? = null,
    /**
     * 静置多久后变暗缩小。0 = 关闭。
     */
    val idleDimDelayMs: Long = FloatBallDefaults.IdleDimDelayMs,
    /** 跟随持机手：手势碰了哪侧，球就即时移到哪侧（试验）。 */
    val followHand: Boolean = FloatBallDefaults.FollowHand,
    /** 竖屏落点。 */
    val portrait: FloatBallPosition = FloatBallPosition(),
    /** 横屏落点。 */
    val landscape: FloatBallPosition = FloatBallPosition(),
    /** 通知球位置（独立球，点按展开自己的胶囊）。 */
    val notificationBallPosition: CapsuleBallPosition = CapsuleBallPosition(),
    /** 建议球位置（验证码 / 链接）。 */
    val suggestionBallPosition: CapsuleBallPosition = CapsuleBallPosition(),
    /** 通知胶囊（独立开关，宿主同为 FloatBallService）：通知到达时从通知球向左展开。 */
    val notificationCapsule: NotificationCapsuleSettings = NotificationCapsuleSettings(),
    /** 建议胶囊（独立开关）：验证码/链接从建议球展开；沿用旧 clipboardBall 作为总开关。 */
    val clipboardBall: ClipboardBallSettings = ClipboardBallSettings(),
    /** 两条队列的 TTL 与容量。 */
    val inbox: BallInboxSettings = BallInboxSettings(),
) {
    fun position(landscape: Boolean): FloatBallPosition =
        if (landscape) this.landscape else portrait

    val colorSpec: ColorSpec
        get() = ColorSpec(colorSource, colorThemeKey, color)

    /** 通知球落点：没写过用右缘 y=0.30。 */
    fun notificationBall(landscape: Boolean): FloatBallPosition =
        notificationBallPosition.position(landscape)
            ?: FloatBallPosition(x = FloatBallDefaults.X, y = FloatBallDefaults.NotificationBallY)

    /**
     * 建议球落点：新字段没写过时从旧 `clipboardBall.x/y` 推导，
     * 旧值也为空才用右缘 y=0.54。
     */
    fun suggestionBall(landscape: Boolean): FloatBallPosition =
        suggestionBallPosition.position(landscape)
            ?: FloatBallPosition(
                x = clipboardBall.x ?: FloatBallDefaults.X,
                y = clipboardBall.y ?: FloatBallDefaults.SuggestionBallY,
            )
}

/** 只改当前朝向那份落点。 */
fun FloatBallSettings.withPosition(
    landscape: Boolean,
    transform: (FloatBallPosition) -> FloatBallPosition,
): FloatBallSettings =
    if (landscape) copy(landscape = transform(this.landscape))
    else copy(portrait = transform(portrait))

/**
 * 通知胶囊配置：普通通知到达时从通知球向左展开「图标 + 标题 + 正文」，
 * 点击直达（通知 contentIntent 优先，失败回退启动应用），[autoHideMs] 后自动收起。
 */
@Serializable
@Keep
data class NotificationCapsuleSettings(
    val enabled: Boolean = false,
    /** 弹出后多少 ms 自动收起（3–10s，设置页滑杆约束）。 */
    val autoHideMs: Long = FloatBallDefaults.CapsuleAutoHideMs,
    /** 应用黑名单：这些包的通知不弹胶囊。 */
    val blocklist: List<String> = emptyList(),
    /** 用户滑掉后，同 key 的通知在 [dismissSuppressMs] 内再次更新也不再弹。 */
    val dismissSuppressEnabled: Boolean = FloatBallDefaults.CapsuleDismissSuppressEnabled,
    /** 抑制重弹的时间窗（ms，设置页滑杆约束）。 */
    val dismissSuppressMs: Long = FloatBallDefaults.CapsuleDismissSuppressMs,
)

/**
 * 建议胶囊配置：验证码/链接变化时从建议球展开，[autoHideMs] 后自动收起。
 * 后台读剪贴板依赖 Shizuku 以 shell 身份读取（新 ROM 不向后台应用投递剪贴板回调，
 * 无障碍也不豁免），读不到内容就不弹。
 */
@Serializable
@Keep
data class ClipboardBallSettings(
    val enabled: Boolean = false,
    /** 弹出后多少 ms 自动收起（3–10s，设置页滑杆约束）。 */
    val autoHideMs: Long = FloatBallDefaults.ClipboardBallAutoHideMs,
    /**
     * 旧版剪贴板球位置：只作为迁移来源，不再继续写。
     * 新位置见 [FloatBallSettings.suggestionBallPosition]。
     */
    val x: Float? = null,
    val y: Float? = null,
)

/**
 * 两条队列配置：通知与建议各自计时，容量可分别覆盖共享默认值。
 * 钉住项不参与 TTL；容量包含钉住项。
 */
@Serializable
@Keep
data class BallInboxSettings(
    /** 通知在通知胶囊中的保留时长（ms）。 */
    val notificationTtlMs: Long = FloatBallDefaults.InboxNotificationTtlMs,
    /** 链接建议的保留时长（ms）；验证码固定 5 分钟，不受此项影响。 */
    val clipboardTtlMs: Long = FloatBallDefaults.InboxSuggestionTtlMs,
    /** 共享容量默认值。 */
    val capacity: Int = FloatBallDefaults.InboxCapacity,
    /** 通知队列容量覆盖；null = 用 [capacity]。 */
    val notificationCapacity: Int? = null,
    /** 建议队列容量覆盖；null = 用 [capacity]。 */
    val suggestionCapacity: Int? = null,
)

object FloatBallDefaults {
    const val Enabled = false
    const val X = 0.92f
    const val Y = 0.42f
    const val SizeDp = 48f
    /** 视觉尺寸下限 = 命中区硬下限（ui-guidelines §4.6：窗口/命中区 ≥ 48dp）。 */
    const val MinSizeDp = 48f
    const val MaxSizeDp = 96f
    /** 0 = 透明（与 `android.graphics.Color.TRANSPARENT` 同值）。 */
    val Color: Int = 0

    /** 静置延时（ms），0 = 关；滑杆上下限与默认值同源。 */
    const val IdleDimDelayMs = 3_000L
    const val IdleDimDelayMsMin = 0L
    const val IdleDimDelayMsMax = 8_000L

    const val IdleDimAlpha = 0.5f

    /**
     * 静置出屏比例：球径的一半移到屏幕外（语义是横向偏移而非等比缩小）。
     * 与 ui-guidelines §4.6 一致（半个球身出屏，只剩半圆）。
     */
    const val IdleOutRatio = 0.5f

    const val PressScale = 0.9f

    /** touchSlop（dp），点按与拖动分界。 */
    const val TouchSlopDp = 12f

    /** 键盘弹出时球与键盘顶边的间距（dp）。 */
    /** 键盘让位额外抬升：键盘顶上一般还有应用自己的输入栏/发送按钮，只留 ImeMargin 会挡住它。 */
    const val ImeExtraLiftDp = 48f

    const val ImeMarginDp = 8f

    /** 跟随持机手（试验）。 */
    const val FollowHand = false

    /** 通知胶囊：自动收起时长默认值与滑杆上下限（ms）。 */
    const val CapsuleAutoHideMs = 5_000L
    const val CapsuleAutoHideMsMin = 3_000L
    const val CapsuleAutoHideMsMax = 10_000L

    /** 通知胶囊：滑掉后的重弹抑制开关、时间窗默认值与滑杆上下限（ms）。 */
    const val CapsuleDismissSuppressEnabled = true
    const val CapsuleDismissSuppressMs = 10_000L
    const val CapsuleDismissSuppressMsMin = 3_000L
    const val CapsuleDismissSuppressMsMax = 60_000L

    /** 剪贴板球：自动收起时长默认值与滑杆上下限（ms）。 */
    const val ClipboardBallAutoHideMs = 5_000L
    const val ClipboardBallAutoHideMsMin = 3_000L
    const val ClipboardBallAutoHideMsMax = 10_000L

    /** 统一收件箱：来源 TTL 默认值与滑杆上下限（ms）。 */
    const val InboxNotificationTtlMs = 120_000L
    const val InboxNotificationTtlMsMin = 30_000L
    const val InboxNotificationTtlMsMax = 600_000L
    const val InboxSuggestionTtlMs = 300_000L
    const val InboxSuggestionTtlMsMin = 60_000L
    const val InboxSuggestionTtlMsMax = 1_800_000L

    /** 验证码固定保存 5 分钟：不提供设置项。 */
    const val CodeTtlMs = 300_000L

    /** 三球默认落点：右缘纵向错开。 */
    const val NotificationBallY = 0.30f
    const val SuggestionBallY = 0.54f

    /** 统一收件箱容量默认值与滑杆上下限。 */
    const val InboxCapacity = 10
    const val InboxCapacityMin = 5
    const val InboxCapacityMax = 20

}
