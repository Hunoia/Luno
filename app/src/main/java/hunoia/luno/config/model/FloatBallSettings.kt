package hunoia.luno.config.model

import android.graphics.Color as AndroidColor
import androidx.annotation.Keep
import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.Serializable

/**
 * 悬浮球配置。
 *
 * 挂在 [ActionSettings.floatBall] 下才能顺着 ConfigProvider → SettingsStore →
 * SettingsState 到达 `:service` 进程的 `FloatBallService`；挂到别的域会静默失效。
 * 坐标归一化（0..1），转屏 / 换分辨率不用迁移数据。
 * 按住即拖（无长按门槛），松手落位；点按 = 抬手未超 touchSlop 时执行 [tapAction]。
 */
@Serializable
@Keep
data class FloatBallSettings(
    val enabled: Boolean = FloatBallDefaults.Enabled,
    /** 归一化横坐标（球窗顶边 / 屏宽）。 */
    val x: Float = FloatBallDefaults.X,
    /** 归一化纵坐标（球窗顶边 / 屏高）。 */
    val y: Float = FloatBallDefaults.Y,
    val sizeDp: Float = FloatBallDefaults.SizeDp,
    val color: Int = FloatBallDefaults.Color,
    /** 单击球执行的动作。为 null 表示单击无响应。 */
    val tapAction: StoredAction? = null,
    /** 屏幕对侧镜像位置显示描边分身：点击触发动作并换位，拖动可搬球。 */
    val mirrorBall: Boolean = FloatBallDefaults.MirrorBall,
    /**
     * 静置多久后变暗缩小。0 = 关闭。
     */
    val idleDimDelayMs: Long = FloatBallDefaults.IdleDimDelayMs,
    /** 跟随持机手：手势碰了哪侧，球就即时移到哪侧（试验）。 */
    val followHand: Boolean = FloatBallDefaults.FollowHand,
    /** 左手侧球高度锚点（归一化 y，手动停靠时记忆）。null = 未记忆。 */
    val leftAnchorY: Float? = null,
    /** 右手侧球高度锚点。 */
    val rightAnchorY: Float? = null,
    /** 通知胶囊（独立开关，宿主同为 FloatBallService）：通知到达时底部弹出、自动收起。 */
    val notificationCapsule: NotificationCapsuleSettings = NotificationCapsuleSettings(),
    /** 剪贴板球（独立开关，宿主同为 FloatBallService）：复制文本后弹出、点击分享、自动收起。 */
    val clipboardBall: ClipboardBallSettings = ClipboardBallSettings(),
)

/**
 * 通知胶囊配置：通知到达时在屏幕底部居中弹出「图标 + 标题 + 正文」胶囊，
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
 * 剪贴板球配置：剪贴板文本/图片变化时在贴边记忆位弹出胶囊，点击弹系统分享，
 * 长按钉住/取消钉住，尾部图标展开/收起全文，[autoHideMs] 后自动收起（钉住/展开时暂停）。
 * 后台读剪贴板依赖 Shizuku 以 shell 身份读取（新 ROM 不向后台应用投递剪贴板回调，
 * 无障碍也不豁免），读不到内容就不弹。
 */
@Serializable
@Keep
data class ClipboardBallSettings(
    val enabled: Boolean = false,
    /** 弹出后多少 ms 自动收起（3–10s，设置页滑杆约束）。 */
    val autoHideMs: Long = FloatBallDefaults.ClipboardBallAutoHideMs,
    /** 记忆位置：x 只记侧别（0 = 左缘 / 1 = 右缘，null = 首弹右侧），y 为归一化纵向落点（按屏高夹在屏内）。 */
    val x: Float? = null,
    val y: Float? = null,
    /** 是否响应图片剪贴板（关闭后只处理纯文本）。 */
    val respondToImages: Boolean = true,
)

object FloatBallDefaults {
    const val Enabled = false
    const val X = 0.92f
    const val Y = 0.42f
    const val SizeDp = 56f
    const val MinSizeDp = 36f
    const val MaxSizeDp = 96f
    val Color: Int = AndroidColor.TRANSPARENT

    /** 静置延时（ms），0 = 关。 */
    const val IdleDimDelayMs = 3_000L

    const val IdleDimAlpha = 0.4f

    /**
     * 静置出屏比例：球径的这部分移到屏幕外（语义是横向偏移而非等比缩小）。
     * 一半出屏。
     */
    const val IdleOutRatio = 0.5f

    const val PressScale = 0.9f

    /** touchSlop（dp），点按与拖动分界。 */
    const val TouchSlopDp = 12f

    /** 键盘弹出时球与键盘顶边的间距（dp）。 */
    /** 键盘让位额外抬升：键盘顶上一般还有应用自己的输入栏/发送按钮，只留 ImeMargin 会挡住它。 */
    const val ImeExtraLiftDp = 48f

    const val ImeMarginDp = 8f

    const val MirrorBall = false

    /** 跟随持机手（试验）。 */
    const val FollowHand = false

    /** 分身描边的常驻透明度（半圆姿态，比主球 idle 更暗）。 */
    const val MirrorDimAlpha = 0.1f

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
}
