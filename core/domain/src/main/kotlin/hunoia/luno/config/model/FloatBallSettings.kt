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

/**
 * 一颗内容球的自定义图标（通知球 / 建议球各一份，互不跟随）。
 *
 * [path] 为 null 表示用该球自己的默认面：通知球显示通知应用图标（无通知时铃铛），
 * 建议球显示灯泡。内容球**不读**主球的 [FloatBallSettings.customIconPath]，
 * 主球换图不会连带改掉胶囊球。
 */
@Serializable
@Keep
data class BallCustomIcon(
    /** filesDir/floatball_icon/ 下的绝对路径；null = 未自定义。 */
    val path: String? = null,
    /** 图标在球面内的缩放（1.0 = 填满球径）。 */
    val scale: Float = 1f,
    /** 归一化横向偏移（-0.5..0.5）。 */
    val offsetX: Float = 0f,
    /** 归一化纵向偏移（-0.5..0.5）。 */
    val offsetY: Float = 0f,
    /** 整体透明度（0.1..1.0）。 */
    val alpha: Float = 1f,
)

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
    /** 结果球位置（分发规则的结果胶囊挂在这颗球上）。 */
    val resultBallPosition: CapsuleBallPosition = CapsuleBallPosition(),
    /** 通知胶囊（独立开关，宿主同为 FloatBallService）：未命中的普通通知到达时从通知球向左展开。 */
    val notificationCapsule: NotificationCapsuleSettings = NotificationCapsuleSettings(),
    /** 结果胶囊：分发规则的胶囊档结果从这里展开。**没有开关**——有结果就会显示。 */
    val resultCapsule: ResultCapsuleSettings = ResultCapsuleSettings(),
    /** 两条队列的 TTL 与容量。 */
    val inbox: BallInboxSettings = BallInboxSettings(),
    /**
     * 自定义球面图标文件路径（filesDir/floatball_icon/ 下的绝对路径，选中时从内容 URI 复制，
     * 不依赖临时授权）。null = 默认彩色圆底 + 动作图标。
     * GIF 动画 / PNG / WebP 静态均支持（Coil GifDecoder，见 App.newImageLoader）。
     */
    val customIconPath: String? = null,
    /**
     * 自定义图标在球面内的缩放（裁剪预览确认值；1.0 = 填满球径）。
     * 与 [customIconOffsetX]/[customIconOffsetY] 一起构成 `graphicsLayer` 变换。
     */
    val customIconScale: Float = 1f,
    /** 自定义图标横向偏移（归一化，-0.5..0.5）。 */
    val customIconOffsetX: Float = 0f,
    /** 自定义图标纵向偏移（归一化，-0.5..0.5）。 */
    val customIconOffsetY: Float = 0f,
    /** 自定义图标整体透明度（0.1..1.0；0.1 下限防止完全透明找不着球）。 */
    val customIconAlpha: Float = 1f,
    /** 静置时暂停动图动画（与球静置变暗缩小同判定点），省电。 */
    val customIconPauseWhenIdle: Boolean = false,
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
     * 结果球落点：新字段没写过时从旧 `resultCapsule.x/y` 推导，
     * 旧值也为空才用右缘 y=0.54。
     */
    fun resultBall(landscape: Boolean): FloatBallPosition =
        resultBallPosition.position(landscape)
            ?: FloatBallPosition(
                x = resultCapsule.x ?: FloatBallDefaults.X,
                y = resultCapsule.y ?: FloatBallDefaults.ResultBallY,
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
    /** 通知球独立跟手：手势碰哪侧，通知球就挪到哪侧。 */
    val followHand: Boolean = false,
    /** 通知球自定义图标；未设置时用通知应用图标 / 铃铛，不跟随主球。 */
    val customIcon: BallCustomIcon = BallCustomIcon(),
    /** 通知球静置多久后变暗并半出屏；0 = 关闭（默认，保持内容球常亮）。 */
    val idleDimDelayMs: Long = FloatBallDefaults.CapsuleIdleDimDelayMs,
    /** 弹出后多少 ms 自动收起（3–10s，设置页滑杆约束）。 */
    val autoHideMs: Long = FloatBallDefaults.CapsuleAutoHideMs,
    /** 应用黑名单：这些包的通知不弹胶囊。 */
    val blocklist: List<String> = emptyList(),
    /** 开启后：进入通知胶囊的系统通知从通知栏隐藏/清掉，只显示胶囊。 */
    val hideSystemNotification: Boolean = FloatBallDefaults.CapsuleHideSystemNotification,
    /** 用户滑掉后，同 key 的通知在 [dismissSuppressMs] 内再次更新也不再弹。 */
    val dismissSuppressEnabled: Boolean = FloatBallDefaults.CapsuleDismissSuppressEnabled,
    /** 抑制重弹的时间窗（ms，设置页滑杆约束）。 */
    val dismissSuppressMs: Long = FloatBallDefaults.CapsuleDismissSuppressMs,
)

/**
 * 结果胶囊配置：分发规则的胶囊档结果从这里展开，[autoHideMs] 后自动收起。
 *
 * **没有 `enabled`**：胶囊是规则结果的呈现面，不是一项可以单独关掉的功能。
 * 要不要出结果由规则表（含各来源的读取开关）决定；要停就删规则或关它的开关。
 * 后台读剪贴板依赖 Shizuku 以 shell 身份读取（新 ROM 不向后台应用投递剪贴板回调，
 * 无障碍也不豁免），读不到内容就什么都不发生。
 */
@Serializable
@Keep
data class ResultCapsuleSettings(
    /** 结果球独立跟手：手势碰哪侧，结果球就挪到哪侧。 */
    val followHand: Boolean = false,
    /** 结果球自定义图标；未设置时用灯泡，不跟随主球。 */
    val customIcon: BallCustomIcon = BallCustomIcon(),
    /** 结果球静置多久后变暗并半出屏；0 = 关闭（默认，保持内容球常亮）。 */
    val idleDimDelayMs: Long = FloatBallDefaults.CapsuleIdleDimDelayMs,
    // 情景识别的开关与名单已搬到 [RecognitionSettings]（它是识别器的第五个输入源，
    // 挂在结果胶囊下会让「按来源配置」散在两处）。
    /** 弹出后多少 ms 自动收起（3–10s，设置页滑杆约束）。 */
    val autoHideMs: Long = FloatBallDefaults.ClipboardBallAutoHideMs,
    /**
     * 旧版剪贴板球位置：只作为迁移来源，不再继续写。
     * 新位置见 [FloatBallSettings.resultBallPosition]。
     */
    val x: Float? = null,
    val y: Float? = null,
)

/**
 * 两条队列配置：通知与结果各自计时，容量可分别覆盖共享默认值。
 * 钉住项不参与 TTL；容量包含钉住项。
 */
@Serializable
@Keep
data class BallInboxSettings(
    /** 通知在通知胶囊中的保留时长（ms）。 */
    val notificationTtlMs: Long = FloatBallDefaults.InboxNotificationTtlMs,
    /** 链接/结果在结果胶囊中的保留时长（ms）；验证码固定 5 分钟，不受此项影响。 */
    val clipboardTtlMs: Long = FloatBallDefaults.InboxResultTtlMs,
    /** 共享容量默认值。 */
    val capacity: Int = FloatBallDefaults.InboxCapacity,
    /** 通知队列容量覆盖；null = 用 [capacity]。 */
    val notificationCapacity: Int? = null,
    /** 建议队列容量覆盖；null = 用 [capacity]。 */
    val resultCapacity: Int? = null,
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

    /** 内容球（通知 / 建议）静置变暗默认值：0 = 关，不改变原先的常亮行为。 */
    const val CapsuleIdleDimDelayMs = 0L

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

    /** 通知胶囊：「只显示胶囊」开关默认值（关：系统通知照常显示在通知栏）。 */
    const val CapsuleHideSystemNotification = false

    /** 剪贴板球：自动收起时长默认值与滑杆上下限（ms）。 */
    const val ClipboardBallAutoHideMs = 5_000L
    const val ClipboardBallAutoHideMsMin = 3_000L
    const val ClipboardBallAutoHideMsMax = 10_000L

    /** 统一收件箱：来源 TTL 默认值与滑杆上下限（ms）。 */
    const val InboxNotificationTtlMs = 120_000L
    const val InboxNotificationTtlMsMin = 30_000L
    const val InboxNotificationTtlMsMax = 600_000L
    const val InboxResultTtlMs = 300_000L
    const val InboxResultTtlMsMin = 60_000L
    const val InboxResultTtlMsMax = 1_800_000L

    /** 三球默认落点：右缘纵向错开。 */
    const val NotificationBallY = 0.30f
    const val ResultBallY = 0.54f

    /** 统一收件箱容量默认值与滑杆上下限。 */
    const val InboxCapacity = 10
    const val InboxCapacityMin = 1
    const val InboxCapacityMax = 10

    /** 自定义图标透明度默认值与滑杆上下限（下限 0.1：全透明会让球找不着）。 */
    const val CustomIconAlpha = 1f
    const val CustomIconAlphaMin = 0.1f
    const val CustomIconAlphaMax = 1f

}
