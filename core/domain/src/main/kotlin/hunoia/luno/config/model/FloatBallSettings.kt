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
 * 按住即拖（无长按门槛），松手落位；点按 = 抬手未超 touchSlop 时执行 [tapAction]；
 * 长按 = 按住不动到 [longPressMs] 执行 [longPressAction]，触发后本次手势不再跟手。
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

/** 落到新位置：只有 [left] 对应那一侧的高度锚点跟着更新，另一侧与未拖到侧的旧锚点保留。 */
fun FloatBallPosition.withAnchorY(left: Boolean, x: Float, y: Float): FloatBallPosition = copy(
    x = x,
    y = y,
    leftAnchorY = if (left) y else leftAnchorY,
    rightAnchorY = if (!left) y else rightAnchorY,
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
     * 长按球执行的动作：按住不动到 [longPressMs] 触发（先动 = 拖；触发后本次手势
     * 不再跟手，与触钮子手势同仲裁）。null = 长按无行为。
     */
    val longPressAction: StoredAction? = null,
    /** 长按触发时长（ms），滑杆范围 [FloatBallDefaults.LongPressMsMin]..[FloatBallDefaults.LongPressMsMax]。 */
    val longPressMs: Long = FloatBallDefaults.LongPressMs,
    /**
     * 拖入判定区松手执行的动作（彩蛋）：球心落进 [dropZone] 判定区时球面高亮，
     * 松手执行动作后球仍按普通落位吸附边缘。null = 无彩蛋，判定区不参与。
     */
    val dropAction: StoredAction? = null,
    /** 竖屏判定区（归一化矩形）；null = 未自定义，用默认居中 [FloatBallDefaults.defaultDropZone]。 */
    val dropZonePortrait: WindowBounds? = null,
    /** 横屏判定区；语义同 [dropZonePortrait]。 */
    val dropZoneLandscape: WindowBounds? = null,
    /** 拖入判定区（彩蛋）瞬间触发一次振动提示（进区边沿震一次，离开不震）。 */
    val dropZoneVibrate: Boolean = true,
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
    /** 当前界面悬浮窗（部件「钉悬浮」拉起，内容实时读 ForegroundActivityHub）：位置 / 尺寸 / 停靠侧。 */
    val activityFloat: ActivityFloatSettings = ActivityFloatSettings(),
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

    /** 当前朝向的判定区；未自定义时用默认居中矩形（竖横同一组占比）。 */
    fun dropZone(landscape: Boolean): WindowBounds =
        (if (landscape) dropZoneLandscape else dropZonePortrait)
            ?: FloatBallDefaults.defaultDropZone()

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

/** 只写当前朝向那份判定区（判定区调整页确认时用）。 */
fun FloatBallSettings.withDropZone(landscape: Boolean, zone: WindowBounds): FloatBallSettings =
    if (landscape) copy(dropZoneLandscape = zone) else copy(dropZonePortrait = zone)

/**
 * 胶囊手势槽绑定的内置行为（仅互选，不接动作库）。
 * 存储字段保存原值；读时经各胶囊设置类的 `*Behavior` 归一化属性收口——
 * 槽位不允许的行为（手改数据 / 旧备份）回退到该槽的出厂默认，规则见各属性 KDoc。
 */
@Serializable
@Keep
enum class CapsuleGestureBehavior {
    /** 无行为。 */
    NONE,

    /** 忽略该行（移除 + 同源重弹抑制）。 */
    IGNORE_ROW,

    /** 收起整个胶囊。 */
    COLLAPSE,

    /** 打开该行的操作菜单（仅结果行长按合法）。 */
    MENU,

    /** 清空该胶囊队列（仅球长按合法；清空后球随可见性刷新自然消失）。 */
    CLEAR_QUEUE,
}

/** 读时归一化：[this] 不在 [allowed] 里（手改数据 / 旧备份）就回 [fallback]。 */
private fun CapsuleGestureBehavior.coerceTo(
    allowed: Set<CapsuleGestureBehavior>,
    fallback: CapsuleGestureBehavior,
): CapsuleGestureBehavior = if (this in allowed) this else fallback

private val BallLongPressAllowed =
    setOf(CapsuleGestureBehavior.NONE, CapsuleGestureBehavior.CLEAR_QUEUE)
private val ResultRowLongPressAllowed =
    setOf(CapsuleGestureBehavior.NONE, CapsuleGestureBehavior.IGNORE_ROW, CapsuleGestureBehavior.MENU)
private val NotificationRowLongPressAllowed =
    setOf(CapsuleGestureBehavior.NONE, CapsuleGestureBehavior.IGNORE_ROW)
private val RowSwipeAllowed =
    setOf(CapsuleGestureBehavior.NONE, CapsuleGestureBehavior.IGNORE_ROW, CapsuleGestureBehavior.COLLAPSE)

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
    /** 通知球颜色；[ColorSpec.isUnset] 时跟随主题主色（不随悬浮球）。 */
    val ballColor: ColorSpec = ColorSpec.Unset,
    /** 胶囊背景色；[ColorSpec.isUnset] 时跟随主题主色（不随悬浮球）。 */
    val backgroundColor: ColorSpec = ColorSpec.Unset,
    /** 通知球静置多久后变暗并半出屏；0 = 关闭（默认，保持内容球常亮）。 */
    val idleDimDelayMs: Long = FloatBallDefaults.CapsuleIdleDimDelayMs,
    /** 弹出后多少 ms 自动收起（3–10s，设置页滑杆约束）。 */
    val autoHideMs: Long = FloatBallDefaults.CapsuleAutoHideMs,
    /** 应用黑名单：这些包的通知不弹胶囊。 */
    val blocklist: List<String> = emptyList(),
    /** 开启后：进入通知胶囊的系统通知从通知栏隐藏/清掉，只显示胶囊。 */
    val hideSystemNotification: Boolean = FloatBallDefaults.CapsuleHideSystemNotification,
    /**
     * 同屏可见行数（1–5）：胶囊内最多同时显示几条通知，超出部分竖向滚动查看。
     * 只影响展示，不改变队列容量（[BallInboxSettings.notificationCapacity]）。
     */
    val visibleRows: Int = FloatBallDefaults.CapsuleVisibleRows,
    /** 收起态行高设置（dp）：胶囊自己的大小，48–72；读请走 [capsuleRowHeightDp]。 */
    val rowHeightDp: Int = FloatBallDefaults.CapsuleRowHeightDp,
    /** 用户滑掉后，同 key 的通知在 [dismissSuppressMs] 内再次更新也不再弹。 */
    val dismissSuppressEnabled: Boolean = FloatBallDefaults.CapsuleDismissSuppressEnabled,
    /** 抑制重弹的时间窗（ms，设置页滑杆约束）。 */
    val dismissSuppressMs: Long = FloatBallDefaults.CapsuleDismissSuppressMs,
    /** 到达提示动效：新通知把胶囊从隐藏拉起时播放一次，播完即停（默认关）。 */
    val alert: NotificationAlertSettings = NotificationAlertSettings(),
    /** 存储原值，读请走 [ballLongPressBehavior]。 */
    val ballLongPress: CapsuleGestureBehavior = CapsuleGestureBehavior.NONE,
    /** 存储原值，读请走 [rowLongPressBehavior]。 */
    val rowLongPress: CapsuleGestureBehavior = CapsuleGestureBehavior.NONE,
    /** 存储原值，读请走 [rowSwipeAwayBehavior]。 */
    val rowSwipeAway: CapsuleGestureBehavior = CapsuleGestureBehavior.IGNORE_ROW,
    /** 存储原值，读请走 [rowSwipeTowardBehavior]。 */
    val rowSwipeToward: CapsuleGestureBehavior = CapsuleGestureBehavior.COLLAPSE,
) {

    /** 生效行高（dp）：夹在 48–72。 */
    val capsuleRowHeightDp: Int
        get() = rowHeightDp.coerceIn(
            FloatBallDefaults.CapsuleRowHeightDpMin,
            FloatBallDefaults.CapsuleRowHeightDpMax,
        )

    /** 通知球长按行为：仅允许 无 / 清空队列，其余归 无。 */
    val ballLongPressBehavior: CapsuleGestureBehavior
        get() = ballLongPress.coerceTo(BallLongPressAllowed, CapsuleGestureBehavior.NONE)

    /** 通知行长按行为：仅允许 无 / 忽略该行，其余归 无。 */
    val rowLongPressBehavior: CapsuleGestureBehavior
        get() = rowLongPress.coerceTo(NotificationRowLongPressAllowed, CapsuleGestureBehavior.NONE)

    /** 通知行背球侧滑行为：仅允许 忽略该行 / 收起胶囊 / 无，其余归 忽略该行（现行为）。 */
    val rowSwipeAwayBehavior: CapsuleGestureBehavior
        get() = rowSwipeAway.coerceTo(RowSwipeAllowed, CapsuleGestureBehavior.IGNORE_ROW)

    /** 通知行向球侧滑行为：集合同上，其余归 收起胶囊（现行为）。 */
    val rowSwipeTowardBehavior: CapsuleGestureBehavior
        get() = rowSwipeToward.coerceTo(RowSwipeAllowed, CapsuleGestureBehavior.COLLAPSE)
}

/**
 * 到达提示动效的四个类目（需求确认口径：分类开关，跨类可同时播放）。
 *
 * 类目内**单选一个样式**，类目间可叠加；四类全关 = 不播（总开关只是总闸）。
 * [Capsule] 画在胶囊窗自己的 Compose 层，其余三类画在全屏舞台窗。
 */
enum class AlertCategory {
    /** 屏幕边缘类：呼吸光 / 流光（后续波纹 / 闪点 / 烟花 / 回旋 / 双刃）。 */
    Edge,

    /** 粒子类：顶部雨（后续喷泉 / 流星 / 彩带 / 雪花 / 花瓣 / 气泡 / 星火 / 萤火）。 */
    Particle,

    /** 胶囊本体类：脉冲 / 边框流光。 */
    Capsule,

    /** 球心·全屏类：球光环 / 全屏涟漪。 */
    Anchor,
}

/**
 * 样式标识：每个类目一个枚举，类目内单选。
 *
 * 渲染分派按它穷尽 `when`（新增样式不登记就编译不过），动效家族映射也用
 * [category] 取（每类一个家族）。
 */
sealed interface AlertStyleId {
    val category: AlertCategory
}

/** 边缘类样式。 */
@Serializable
@Keep
enum class EdgeAlertStyle : AlertStyleId {
    /** 屏幕边缘呼吸光。 */
    EdgeGlow,

    /** 屏幕边缘流光：亮带沿屏缘环绕（单/双带 + 拖尾 + 刃感 + 双色可设）。 */
    EdgeFlow,

    /** 波纹：多道波峰沿屏缘匀速环行（波峰数可调，柔和扩散）。 */
    EdgeRipple,

    /** 闪点：沿屏缘随机位置定点闪烁的亮点。 */
    EdgeBlink,

    /** 屏缘烟花：在屏缘随机锚点向内迸发的小型粒子烟花。 */
    EdgeFireworks,

    /** 回旋：一簇光点绕屏缘环行并同步内外漂移，形成回旋纹理。 */
    EdgeSwirl,

    /** 双刃上升：左右两缘各一道竖直亮刃自下而上反复扫过。 */
    EdgeBladeRise,
    ;

    override val category: AlertCategory get() = AlertCategory.Edge
}

/** 粒子类样式（运动形式单选）。 */
@Serializable
@Keep
enum class ParticleAlertStyle : AlertStyleId {
    /** 顶部雨：从上往下错峰掉落。 */
    TopRain,

    /** 底部喷泉：从底缘向上喷出，重力回落。 */
    BottomFountain,

    /** 流星：带拖尾的亮点斜向下划过。 */
    Meteor,

    /** 彩带：彩色小片翻转飘落。 */
    Confetti,

    /** 雪花：小而慢的六向漂移下落。 */
    Snowflake,

    /** 花瓣：带自转的宽片缓落。 */
    Petal,

    /** 气泡：从底缘上浮并左右摇摆。 */
    Bubble,

    /** 星火迸发：从屏幕下部随机锚点斜向迸射的星火。 */
    Spark,

    /** 萤火：散布全屏、随机漂移与明灭的萤光点。 */
    Firefly,
    ;

    override val category: AlertCategory get() = AlertCategory.Particle
}

/** 胶囊本体类样式（画在胶囊窗自己的 Compose 层）。 */
@Serializable
@Keep
enum class CapsuleAlertStyle : AlertStyleId {
    /** 描边辉光脉冲。 */
    Pulse,

    /** 边框流光：亮带沿边框扫过。 */
    Shimmer,

    /** 呼吸描边：整圈轮廓轻微缩放 + 透明度呼吸（无辉光层）。 */
    BreathOutline,

    /** 内部扫光：一道亮带在行内部横向扫过。 */
    InnerSweep,
    ;

    override val category: AlertCategory get() = AlertCategory.Capsule
}

/** 球心·全屏类样式。 */
@Serializable
@Keep
enum class AnchorAlertStyle : AlertStyleId {
    /** 球光环扩散（雷达 ping，锚定通知球）。 */
    Ring,

    /** 全屏涟漪：从锚点（通知球心 / 屏幕中心）扩散一圈抵达屏缘的水波纹。 */
    Ripple,

    /** 球心爆发：从球心向外径向迸发的粒子爆。 */
    BallBurst,

    /** 球心光晕：球心处柔和的径向辉光，随呼吸明灭。 */
    BallHalo,
    ;

    override val category: AlertCategory get() = AlertCategory.Anchor
}

/**
 * 新通知到达提示动效配置。事件驱动、一次性播放：默认只在「通知到达把胶囊从隐藏拉起」
 * 的时机播放，播完自动停；熄屏 / 锁屏 / 勿扰不播。[playOnEveryNotification] 打开后
 * 胶囊已可见的新通知也播（在播时跳过：不排队、不打断）。
 *
 * 参数按**类目**分块（[edge] / [particle] / [capsule] / [anchor]），类目内各样式的旋钮
 * 各自存一份（粒子类另有共享的通用旋钮，见 ui-guidelines §2.4 例外）。
 * [color] 是动效的独立颜色项，[ColorSpec.isUnset] 时跟随主题主色（与胶囊解耦悬浮球）。
 */
@Serializable
@Keep
data class NotificationAlertSettings(
    val enabled: Boolean = FloatBallDefaults.AlertEnabled,
    /** 胶囊已可见时的新通知也播；有动画在播就不播（不排队、不打断）。 */
    val playOnEveryNotification: Boolean = false,
    /**
     * 动效独立黑名单（不复用通知胶囊黑名单）：这些包的通知到达不播动效，
     * 但胶囊照常显示。
     */
    val appBlocklist: List<String> = emptyList(),
    /** 动效颜色；[ColorSpec.isUnset] 时跟随主题主色。 */
    val color: ColorSpec = ColorSpec.Unset,
    val edge: EdgeAlertSettings = EdgeAlertSettings(),
    val particle: ParticleAlertSettings = ParticleAlertSettings(),
    val capsule: CapsuleAlertSettings = CapsuleAlertSettings(),
    val anchor: AnchorAlertSettings = AnchorAlertSettings(),
    /**
     * 用户自定义调色板（内置 8 套 + 彩虹在 [BuiltinAlertPalettes]，不落盘）。
     * 各类目用 [EdgeAlertSettings.paletteId] 等选择（null = 跟随 [color]）。
     */
    val customPalettes: List<AlertPalette> = emptyList(),
)

/** 边缘类：开关 + 单选样式 + 调色板选择 + 该类各样式的旋钮。 */
@Serializable
@Keep
data class EdgeAlertSettings(
    val enabled: Boolean = true,
    val style: EdgeAlertStyle = EdgeAlertStyle.EdgeGlow,
    /** 调色板 id；null = 跟随 [NotificationAlertSettings.color] 纯色。 */
    val paletteId: String? = null,
    val edgeGlow: EdgeGlowAlertSettings = EdgeGlowAlertSettings(),
    val edgeFlow: EdgeFlowAlertSettings = EdgeFlowAlertSettings(),
    val edgeRipple: EdgeRippleAlertSettings = EdgeRippleAlertSettings(),
    val edgeBlink: EdgeBlinkAlertSettings = EdgeBlinkAlertSettings(),
    val edgeFireworks: EdgeFireworksAlertSettings = EdgeFireworksAlertSettings(),
    val edgeSwirl: EdgeSwirlAlertSettings = EdgeSwirlAlertSettings(),
    val edgeBladeRise: EdgeBladeRiseAlertSettings = EdgeBladeRiseAlertSettings(),
)

/** 粒子类：开关 + 单选运动 + 调色板 + 共享旋钮 + 形状（④ 起有多运动）。 */
@Serializable
@Keep
data class ParticleAlertSettings(
    val enabled: Boolean = true,
    val style: ParticleAlertStyle = ParticleAlertStyle.TopRain,
    /** 调色板 id；null = 跟随 [NotificationAlertSettings.color] 纯色。 */
    val paletteId: String? = null,
    val particles: ParticlesAlertSettings = ParticlesAlertSettings(),
    /** 粒子形状：圆点 / 自定义图片 / 通知应用图标。 */
    val shape: ParticleShapeSettings = ParticleShapeSettings(),
)

/** 胶囊本体类：开关 + 单选样式 + 调色板 + 旋钮。 */
@Serializable
@Keep
data class CapsuleAlertSettings(
    val enabled: Boolean = true,
    val style: CapsuleAlertStyle = CapsuleAlertStyle.Pulse,
    /** 调色板 id；null = 跟随 [NotificationAlertSettings.color] 纯色。 */
    val paletteId: String? = null,
    val pulse: PulseAlertSettings = PulseAlertSettings(),
    val shimmer: ShimmerAlertSettings = ShimmerAlertSettings(),
    val breathOutline: BreathOutlineAlertSettings = BreathOutlineAlertSettings(),
    val innerSweep: InnerSweepAlertSettings = InnerSweepAlertSettings(),
)

/** 球心·全屏类：开关 + 单选样式 + 调色板 + 旋钮。 */
@Serializable
@Keep
data class AnchorAlertSettings(
    val enabled: Boolean = true,
    val style: AnchorAlertStyle = AnchorAlertStyle.Ring,
    /** 调色板 id；null = 跟随 [NotificationAlertSettings.color] 纯色。 */
    val paletteId: String? = null,
    val ring: RingAlertSettings = RingAlertSettings(),
    val fullRipple: FullRippleAlertSettings = FullRippleAlertSettings(),
    val ballBurst: BallBurstAlertSettings = BallBurstAlertSettings(),
    val ballHalo: BallHaloAlertSettings = BallHaloAlertSettings(),
)

/** 屏幕边缘呼吸光的专属旋钮：光带宽度、亮度、呼吸圈数、速度、屏缘圆角、衰减硬度。 */
@Serializable
@Keep
data class EdgeGlowAlertSettings(
    val bandWidthDp: Int = FloatBallDefaults.AlertEdgeGlowBandWidthDp,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    /** 播放速度（%）：100 = 家族基准时长，200 = 两倍速（时长减半）。 */
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
    /** 屏缘圆角半径（dp）：SDF 的圆角，按设备观感调。 */
    val cornerRadiusDp: Int = FloatBallDefaults.AlertEdgeGlowCornerDp,
    /** 衰减硬度（指数）：1 = 现行指数衰减，>1 光带主体更实、外缘收得更干脆。 */
    val hardness: Int = FloatBallDefaults.AlertEdgeGlowHardness,
)

/**
 * 屏幕边缘流光的专属旋钮：贴边深度、亮度、圈数（绕屏缘圈数）、速度、
 * 刃感（主带宽 / 硬度 / 拖尾浓度）、屏缘圆角、带数（单/双）、拖尾宽度、双色开关与第二颜色。
 */
@Serializable
@Keep
data class EdgeFlowAlertSettings(
    /** 贴边深度（dp）：光带向屏内衰减的距离尺度。 */
    val bandWidthDp: Int = FloatBallDefaults.AlertFlowBandWidthDp,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
    /** 主带宽度（周长千分比）：越小越像一道利刃，越大越像一段弥散光。 */
    val coreBandPermille: Int = FloatBallDefaults.AlertFlowCoreBandPermille,
    /** 深度衰减硬度（指数）：1 = 柔光弥散，越大主体越实、外缘收得越干脆。 */
    val hardness: Int = FloatBallDefaults.AlertFlowHardness,
    /** 拖尾浓度（%）：主带后那道更宽光带的 alpha 峰值。 */
    val trailAlpha: Int = FloatBallDefaults.AlertFlowTrailAlpha,
    /** 屏缘圆角半径（dp）：与呼吸光同口径，默认跟随设备观感。 */
    val cornerRadiusDp: Int = FloatBallDefaults.AlertEdgeGlowCornerDp,
    /** 双带追逐：两道亮带相向而行（相隔半圈）。 */
    val dualBand: Boolean = false,
    /** 拖尾宽度（主带宽倍数 ×100）：0 = 无拖尾。 */
    val trailScale: Int = FloatBallDefaults.AlertFlowTrailScale,
    /** 双色模式：双带时第二带用第二颜色（[secondColor] 未设置时跟随主题次色）。 */
    val dualColor: Boolean = false,
    val secondColor: ColorSpec = ColorSpec.Unset,
)

/** 顶部粒子掉落的专属旋钮：粒子数量、亮度、波次、速度、大小、摆幅、重力、下落行程。 */
@Serializable
@Keep
data class ParticlesAlertSettings(
    val count: Int = FloatBallDefaults.AlertParticlesCount,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val waves: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
    /** 粒子大小（%）：缩放基准粒径。 */
    val sizeScale: Int = FloatBallDefaults.AlertParticlesSizeScale,
    /** 摆动幅度（%）：缩放基准横摆幅。 */
    val swayScale: Int = FloatBallDefaults.AlertParticlesSwayScale,
    /** 重力强度（指数）：1 = 匀速，2 = 真重力，3 = 更强的加速。 */
    val gravity: Int = FloatBallDefaults.AlertParticlesGravity,
    /** 下落行程（dp）：顶部条窗高度与粒子下落距离同源。 */
    val travelDp: Int = FloatBallDefaults.AlertParticlesTravelDp,
)

/** 球光环扩散的专属旋钮：扩散行程（球径 %）、亮度、圈数、速度、描边宽度、每波环数、光晕强度。 */
@Serializable
@Keep
data class RingAlertSettings(
    val travel: Int = FloatBallDefaults.AlertRingTravel,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
    /** 核描边宽度（dp）：亮度只管 alpha，宽度由此独立控制。 */
    val strokeWidthDp: Int = FloatBallDefaults.AlertRingStrokeWidthDp,
    /** 每循环发出的环数。 */
    val ringsPerCycle: Int = FloatBallDefaults.AlertRingRingsPerCycle,
    /** 光晕强度（%）：缩放光晕环宽度与 alpha 占比，0 = 无光晕。 */
    val haloScale: Int = FloatBallDefaults.AlertHaloScale,
)

/** 胶囊本体脉冲的专属旋钮：亮度、脉冲次数、速度、辉光宽度（0.1dp 粒度）、光晕强度。 */
@Serializable
@Keep
data class PulseAlertSettings(
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
    /** 核心描边宽度（0.1dp 存储：15 = 1.5dp）。 */
    val glowWidthDpTenths: Int = FloatBallDefaults.AlertPulseGlowWidthTenths,
    /** 光晕强度（%）：缩放光晕层宽度与 alpha 占比，0 = 只剩核心描边。 */
    val haloScale: Int = FloatBallDefaults.AlertHaloScale,
)

/** 全屏涟漪的专属旋钮：亮度、圈数、速度、描边宽度、锚点（通知球心 / 屏幕中心）。 */
@Serializable
@Keep
data class FullRippleAlertSettings(
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
    val strokeWidthDp: Int = FloatBallDefaults.AlertRippleStrokeWidthDp,
    /** true = 从通知球心扩散；false = 从屏幕中心扩散。 */
    val centerAtBall: Boolean = true,
)

/** 胶囊边框流光的专属旋钮：主带宽度（%）、亮度、扫过次数、速度、拖尾宽度、反向扫动。 */
@Serializable
@Keep
data class ShimmerAlertSettings(
    val bandWidth: Int = FloatBallDefaults.AlertShimmerBandWidth,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
    /** 拖尾宽度（主带宽倍数 ×100）：0 = 无拖尾。 */
    val trailScale: Int = FloatBallDefaults.AlertShimmerTrailScale,
    /** true = 自右向左扫（默认自左向右）。 */
    val reverse: Boolean = false,
)

/** 边缘波纹的专属旋钮：波峰数、波峰大小、亮度、圈数、速度。 */
@Serializable
@Keep
data class EdgeRippleAlertSettings(
    val crests: Int = FloatBallDefaults.AlertEdgeRippleCrests,
    val sizeDp: Int = FloatBallDefaults.AlertEdgeRippleSizeDp,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 边缘闪点的专属旋钮：亮点数、大小、亮度、圈数、速度。 */
@Serializable
@Keep
data class EdgeBlinkAlertSettings(
    val points: Int = FloatBallDefaults.AlertEdgeBlinkPoints,
    val sizeDp: Int = FloatBallDefaults.AlertEdgeBlinkSizeDp,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 屏缘烟花的专属旋钮：爆点数、大小、迸射行程、亮度、圈数、速度。 */
@Serializable
@Keep
data class EdgeFireworksAlertSettings(
    val bursts: Int = FloatBallDefaults.AlertEdgeFireworksBursts,
    val sizeDp: Int = FloatBallDefaults.AlertEdgeFireworksSizeDp,
    val spreadDp: Int = FloatBallDefaults.AlertEdgeFireworksSpreadDp,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 边缘回旋的专属旋钮：光点数、径向半径、亮度、圈数、速度。 */
@Serializable
@Keep
data class EdgeSwirlAlertSettings(
    val points: Int = FloatBallDefaults.AlertEdgeSwirlPoints,
    val radiusDp: Int = FloatBallDefaults.AlertEdgeSwirlRadiusDp,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 双刃上升的专属旋钮：刃宽、刃高、亮度、圈数、速度。 */
@Serializable
@Keep
data class EdgeBladeRiseAlertSettings(
    val bladeWidthDp: Int = FloatBallDefaults.AlertEdgeBladeWidthDp,
    val bladeHeightDp: Int = FloatBallDefaults.AlertEdgeBladeHeightDp,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 胶囊呼吸描边的专属旋钮：描边宽度（0.1dp）、呼吸缩放、亮度、圈数、速度。 */
@Serializable
@Keep
data class BreathOutlineAlertSettings(
    val strokeWidthDpTenths: Int = FloatBallDefaults.AlertBreathStrokeWidthTenths,
    val breatheScale: Int = FloatBallDefaults.AlertBreathScale,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 胶囊内部扫光的专属旋钮：主带宽度（%）、反向、亮度、圈数、速度。 */
@Serializable
@Keep
data class InnerSweepAlertSettings(
    val bandWidth: Int = FloatBallDefaults.AlertInnerSweepBandWidth,
    val reverse: Boolean = false,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 球心爆发的专属旋钮：粒子数、粒径、迸射行程、亮度、圈数、速度。 */
@Serializable
@Keep
data class BallBurstAlertSettings(
    val count: Int = FloatBallDefaults.AlertBallBurstCount,
    val particleSizeDp: Int = FloatBallDefaults.AlertBallBurstSizeDp,
    val spreadDp: Int = FloatBallDefaults.AlertBallBurstSpreadDp,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 球心光晕的专属旋钮：半径（球径 %）、光晕强度、亮度、圈数、速度。 */
@Serializable
@Keep
data class BallHaloAlertSettings(
    val radiusPercent: Int = FloatBallDefaults.AlertBallHaloRadius,
    val haloScale: Int = FloatBallDefaults.AlertHaloScale,
    val brightness: Int = FloatBallDefaults.AlertBrightness,
    val cycles: Int = FloatBallDefaults.AlertCycles,
    val speed: Int = FloatBallDefaults.AlertSpeedPercent,
)

/** 粒子形状：圆点 / 自定义图片 / 通知应用图标。 */
@Serializable
@Keep
enum class ParticleShape { Dot, CustomImage, AppIcon }

/** 粒子形状设置：形状 + 自定义图片路径（[ParticleShape.CustomImage] 时用）。 */
@Serializable
@Keep
data class ParticleShapeSettings(
    val shape: ParticleShape = ParticleShape.Dot,
    /** 自定义图片的 content Uri 字符串；空 = 回落圆点。 */
    val customImage: String = "",
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
    /** 结果球颜色；[ColorSpec.isUnset] 时跟随主题主色（不随悬浮球）。 */
    val ballColor: ColorSpec = ColorSpec.Unset,
    /** 胶囊背景色；[ColorSpec.isUnset] 时跟随主题主色（不随悬浮球）。 */
    val backgroundColor: ColorSpec = ColorSpec.Unset,
    /** 结果球静置多久后变暗并半出屏；0 = 关闭（默认，保持内容球常亮）。 */
    val idleDimDelayMs: Long = FloatBallDefaults.CapsuleIdleDimDelayMs,
    // 情景识别的开关与名单已搬到 [RecognitionSettings]（它是识别器的第五个输入源，
    // 挂在结果胶囊下会让「按来源配置」散在两处）。
    /** 弹出后多少 ms 自动收起（3–10s，设置页滑杆约束）。 */
    val autoHideMs: Long = FloatBallDefaults.ClipboardBallAutoHideMs,
    /**
     * 同屏可见行数（1–5）：胶囊内最多同时显示几条结果，超出部分竖向滚动查看。
     * 只影响展示，不改变队列容量（[BallInboxSettings.resultCapacity]）。
     */
    val visibleRows: Int = FloatBallDefaults.CapsuleVisibleRows,
    /** 收起态行高设置（dp）：胶囊自己的大小，48–72；读请走 [capsuleRowHeightDp]。 */
    val rowHeightDp: Int = FloatBallDefaults.CapsuleRowHeightDp,
    /** 存储原值，读请走 [ballLongPressBehavior]。 */
    val ballLongPress: CapsuleGestureBehavior = CapsuleGestureBehavior.NONE,
    /** 存储原值，读请走 [rowLongPressBehavior]。 */
    val rowLongPress: CapsuleGestureBehavior = CapsuleGestureBehavior.MENU,
    /** 存储原值，读请走 [rowSwipeAwayBehavior]。 */
    val rowSwipeAway: CapsuleGestureBehavior = CapsuleGestureBehavior.IGNORE_ROW,
    /** 存储原值，读请走 [rowSwipeTowardBehavior]。 */
    val rowSwipeToward: CapsuleGestureBehavior = CapsuleGestureBehavior.COLLAPSE,
    /**
     * 旧版剪贴板球位置：只作为迁移来源，不再继续写。
     * 新位置见 [FloatBallSettings.resultBallPosition]。
     */
    val x: Float? = null,
    val y: Float? = null,
) {

    /** 生效行高（dp）：夹在 48–72。 */
    val capsuleRowHeightDp: Int
        get() = rowHeightDp.coerceIn(
            FloatBallDefaults.CapsuleRowHeightDpMin,
            FloatBallDefaults.CapsuleRowHeightDpMax,
        )

    /** 结果球长按行为：仅允许 无 / 清空队列，其余归 无。 */
    val ballLongPressBehavior: CapsuleGestureBehavior
        get() = ballLongPress.coerceTo(BallLongPressAllowed, CapsuleGestureBehavior.NONE)

    /** 结果行长按行为：仅允许 操作菜单 / 忽略该行 / 无，其余归 操作菜单（现行为）。 */
    val rowLongPressBehavior: CapsuleGestureBehavior
        get() = rowLongPress.coerceTo(ResultRowLongPressAllowed, CapsuleGestureBehavior.MENU)

    /** 结果行背球侧滑行为：仅允许 忽略该行 / 收起胶囊 / 无，其余归 忽略该行（现行为）。 */
    val rowSwipeAwayBehavior: CapsuleGestureBehavior
        get() = rowSwipeAway.coerceTo(RowSwipeAllowed, CapsuleGestureBehavior.IGNORE_ROW)

    /** 结果行向球侧滑行为：集合同上，其余归 收起胶囊（现行为）。 */
    val rowSwipeTowardBehavior: CapsuleGestureBehavior
        get() = rowSwipeToward.coerceTo(RowSwipeAllowed, CapsuleGestureBehavior.COLLAPSE)
}

/**
 * 两条队列配置：通知与结果各自计时，容量可分别覆盖共享默认值。
 * 钉住项不参与 TTL；容量包含钉住项。
 */
@Serializable
@Keep
data class BallInboxSettings(
    /** 通知在通知胶囊中的保留时长（ms）。 */
    val notificationTtlMs: Long = FloatBallDefaults.InboxNotificationTtlMs,
    /**
     * 结果在结果胶囊中的保留时长（ms）；验证码固定 5 分钟，不受此项影响。
     * 旧名 clipboardTtlMs（剪贴板球时代误称）：改名即迁移，旧落盘键被 ignoreUnknownKeys
     * 丢弃、全部设备落新默认（§11.2）。结果按「即时提示」口径默认只留 60s。
     */
    val resultTtlMs: Long = FloatBallDefaults.InboxResultTtlMs,
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

    /** 长按动作触发时长（ms）默认值与滑杆上下限（与动作面板的长按口径同范围）。 */
    const val LongPressMs = 500L
    const val LongPressMsMin = 200L
    const val LongPressMsMax = 1200L

    /** 判定区默认占比：居中、宽 / 高约屏宽（高）的 1/3 × 1/4，竖横同一组。 */
    const val DropZoneWidthFraction = 1f / 3f
    const val DropZoneHeightFraction = 1f / 4f

    /** 未自定义时的判定区：以屏幕中心为中心的 [DropZoneWidthFraction] × [DropZoneHeightFraction]。 */
    fun defaultDropZone(): WindowBounds = WindowBounds(
        x = (1f - DropZoneWidthFraction) / 2f,
        y = (1f - DropZoneHeightFraction) / 2f,
        width = DropZoneWidthFraction,
        height = DropZoneHeightFraction,
    )

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

    /** 到达提示动效：默认关（老用户升级零感知），开启后默认边缘呼吸光。 */
    const val AlertEnabled = false

    /** 到达提示动效通用旋钮默认值与滑杆上下限：亮度（%）、播放次数、播放速度（%）。 */
    const val AlertBrightness = 60
    const val AlertBrightnessMin = 0
    const val AlertBrightnessMax = 100
    const val AlertCycles = 2
    const val AlertCyclesMin = 1
    const val AlertCyclesMax = 4
    const val AlertSpeedPercent = 100
    const val AlertSpeedPercentMin = 50
    const val AlertSpeedPercentMax = 200

    /** 到达提示·边缘呼吸光：光带宽度默认值与滑杆上下限（dp，衰减尺度，可见晕约 3×）。 */
    const val AlertEdgeGlowBandWidthDp = 24
    const val AlertEdgeGlowBandWidthDpMin = 1
    const val AlertEdgeGlowBandWidthDpMax = 96

    /** 到达提示·边缘呼吸光：屏缘圆角半径与衰减硬度（指数）默认值与滑杆上下限。 */
    const val AlertEdgeGlowCornerDp = 28
    const val AlertEdgeGlowCornerDpMin = 8
    const val AlertEdgeGlowCornerDpMax = 48
    const val AlertEdgeGlowHardness = 1
    const val AlertEdgeGlowHardnessMin = 1
    const val AlertEdgeGlowHardnessMax = 4

    /** 到达提示·边缘流光：贴边深度默认值与滑杆上下限（dp，同呼吸光口径）。 */
    const val AlertFlowBandWidthDp = 24
    const val AlertFlowBandWidthDpMin = 1
    const val AlertFlowBandWidthDpMax = 96

    /** 到达提示·边缘流光：拖尾宽度（主带宽倍数 ×100）默认值与滑杆上下限，0 = 无拖尾。 */
    const val AlertFlowTrailScale = 200
    const val AlertFlowTrailScaleMin = 0
    const val AlertFlowTrailScaleMax = 300

    /**
     * 到达提示·边缘流光的刃感：主带宽（周长千分比）、深度衰减硬度（指数）、
     * 拖尾浓度（%）默认值与滑杆上下限。
     *
     * 这三项是「太柔和、光在扩散」的解法：主带越窄 + 硬度越高，光带主体越像一道刃，
     * 而不是一团弥散晕。
     */
    const val AlertFlowCoreBandPermille = 30
    const val AlertFlowCoreBandPermilleMin = 10
    const val AlertFlowCoreBandPermilleMax = 80
    const val AlertFlowHardness = 2
    const val AlertFlowHardnessMin = 1
    const val AlertFlowHardnessMax = 6
    const val AlertFlowTrailAlpha = 40
    const val AlertFlowTrailAlphaMin = 0
    const val AlertFlowTrailAlphaMax = 100

    /** 到达提示·顶部粒子：粒子数量默认值与滑杆上下限。 */
    const val AlertParticlesCount = 48
    const val AlertParticlesCountMin = 12
    const val AlertParticlesCountMax = 120

    /** 到达提示·顶部粒子：粒子大小 / 摆动幅度缩放（%）、重力强度（指数）、下落行程（dp）。 */
    const val AlertParticlesSizeScale = 100
    const val AlertParticlesSizeScaleMin = 50
    const val AlertParticlesSizeScaleMax = 300
    const val AlertParticlesSwayScale = 100
    const val AlertParticlesSwayScaleMin = 0
    const val AlertParticlesSwayScaleMax = 200
    const val AlertParticlesGravity = 2
    const val AlertParticlesGravityMin = 1
    const val AlertParticlesGravityMax = 3
    const val AlertParticlesTravelDp = 200
    const val AlertParticlesTravelDpMin = 100
    const val AlertParticlesTravelDpMax = 800

    /** 到达提示·球光环：扩散行程默认值与滑杆上下限（球径 %）。 */
    const val AlertRingTravel = 95
    const val AlertRingTravelMin = 40
    const val AlertRingTravelMax = 200

    /** 到达提示·球光环：描边宽度（dp）、每循环环数默认值与滑杆上下限。 */
    const val AlertRingStrokeWidthDp = 3
    const val AlertRingStrokeWidthDpMin = 1
    const val AlertRingStrokeWidthDpMax = 8
    const val AlertRingRingsPerCycle = 2
    const val AlertRingRingsPerCycleMin = 1
    const val AlertRingRingsPerCycleMax = 4

    /** 到达提示·全屏涟漪：描边宽度（dp）默认值与滑杆上下限。 */
    const val AlertRippleStrokeWidthDp = 3
    const val AlertRippleStrokeWidthDpMin = 1
    const val AlertRippleStrokeWidthDpMax = 8

    /** 到达提示通用：光晕强度缩放（%）默认值与滑杆上下限（光环 / 脉冲各存一份）。 */
    const val AlertHaloScale = 100
    const val AlertHaloScaleMin = 0
    const val AlertHaloScaleMax = 200

    /** 到达提示·胶囊脉冲：辉光宽度（0.1dp 存储）默认值与滑杆上下限。 */
    const val AlertPulseGlowWidthTenths = 15
    const val AlertPulseGlowWidthTenthsMin = 5
    const val AlertPulseGlowWidthTenthsMax = 40

    /** 到达提示·边框流光：主带宽度默认值与滑杆上下限（胶囊宽 %）、拖尾宽度（倍数 ×100）。 */
    const val AlertShimmerBandWidth = 25
    const val AlertShimmerBandWidthMin = 10
    const val AlertShimmerBandWidthMax = 60
    const val AlertShimmerTrailScale = 200
    const val AlertShimmerTrailScaleMin = 0
    const val AlertShimmerTrailScaleMax = 300

    /** 到达提示·边缘波纹：波峰数默认值与滑杆上下限、波峰大小（dp）。 */
    const val AlertEdgeRippleCrests = 3
    const val AlertEdgeRippleCrestsMin = 2
    const val AlertEdgeRippleCrestsMax = 6
    const val AlertEdgeRippleSizeDp = 6
    const val AlertEdgeRippleSizeDpMin = 2
    const val AlertEdgeRippleSizeDpMax = 20

    /** 到达提示·边缘闪点：亮点数默认值与滑杆上下限、大小（dp）。 */
    const val AlertEdgeBlinkPoints = 8
    const val AlertEdgeBlinkPointsMin = 4
    const val AlertEdgeBlinkPointsMax = 20
    const val AlertEdgeBlinkSizeDp = 4
    const val AlertEdgeBlinkSizeDpMin = 2
    const val AlertEdgeBlinkSizeDpMax = 12

    /** 到达提示·屏缘烟花：爆点数默认值与滑杆上下限、粒径（dp）、迸射行程（dp）。 */
    const val AlertEdgeFireworksBursts = 3
    const val AlertEdgeFireworksBurstsMin = 1
    const val AlertEdgeFireworksBurstsMax = 6
    const val AlertEdgeFireworksSizeDp = 4
    const val AlertEdgeFireworksSizeDpMin = 2
    const val AlertEdgeFireworksSizeDpMax = 10
    const val AlertEdgeFireworksSpreadDp = 140
    const val AlertEdgeFireworksSpreadDpMin = 60
    const val AlertEdgeFireworksSpreadDpMax = 320

    /** 到达提示·边缘回旋：光点数默认值与滑杆上下限、径向半径（dp）。 */
    const val AlertEdgeSwirlPoints = 6
    const val AlertEdgeSwirlPointsMin = 3
    const val AlertEdgeSwirlPointsMax = 12
    const val AlertEdgeSwirlRadiusDp = 6
    const val AlertEdgeSwirlRadiusDpMin = 2
    const val AlertEdgeSwirlRadiusDpMax = 24

    /** 到达提示·双刃上升：刃宽（dp）与刃高（dp）默认值与滑杆上下限。 */
    const val AlertEdgeBladeWidthDp = 4
    const val AlertEdgeBladeWidthDpMin = 2
    const val AlertEdgeBladeWidthDpMax = 16
    const val AlertEdgeBladeHeightDp = 120
    const val AlertEdgeBladeHeightDpMin = 60
    const val AlertEdgeBladeHeightDpMax = 360

    /** 到达提示·胶囊呼吸描边：描边宽度（0.1dp）与呼吸缩放（‰）默认值与滑杆上下限。 */
    const val AlertBreathStrokeWidthTenths = 16
    const val AlertBreathStrokeWidthTenthsMin = 8
    const val AlertBreathStrokeWidthTenthsMax = 36
    const val AlertBreathScale = 60
    const val AlertBreathScaleMin = 0
    const val AlertBreathScaleMax = 120

    /** 到达提示·胶囊内部扫光：主带宽度默认值与滑杆上下限（胶囊宽 %）。 */
    const val AlertInnerSweepBandWidth = 30
    const val AlertInnerSweepBandWidthMin = 10
    const val AlertInnerSweepBandWidthMax = 80

    /** 到达提示·球心爆发：粒子数、粒径（dp）、迸射行程（dp）默认值与滑杆上下限。 */
    const val AlertBallBurstCount = 36
    const val AlertBallBurstCountMin = 12
    const val AlertBallBurstCountMax = 80
    const val AlertBallBurstSizeDp = 3
    const val AlertBallBurstSizeDpMin = 1
    const val AlertBallBurstSizeDpMax = 8
    const val AlertBallBurstSpreadDp = 160
    const val AlertBallBurstSpreadDpMin = 60
    const val AlertBallBurstSpreadDpMax = 360

    /** 到达提示·球心光晕：半径（球径 %）默认值与滑杆上下限。 */
    const val AlertBallHaloRadius = 160
    const val AlertBallHaloRadiusMin = 60
    const val AlertBallHaloRadiusMax = 300

    /** 内容胶囊（通知 / 结果）同屏可见行数默认值与滑杆上下限（行）。 */
    const val CapsuleVisibleRows = 3
    const val CapsuleVisibleRowsMin = 1
    const val CapsuleVisibleRowsMax = 5

    /** 内容胶囊（通知 / 结果）收起态行高默认值与滑杆上下限（dp）。下限与 §4.6 命中区硬下限同源。 */
    const val CapsuleRowHeightDp = 56
    const val CapsuleRowHeightDpMin = 48
    const val CapsuleRowHeightDpMax = 72

    /** 剪贴板球：自动收起时长默认值与滑杆上下限（ms）。 */
    const val ClipboardBallAutoHideMs = 5_000L
    const val ClipboardBallAutoHideMsMin = 3_000L
    const val ClipboardBallAutoHideMsMax = 10_000L

    /** 统一收件箱：来源 TTL 默认值与滑杆上下限（ms）。 */
    const val InboxNotificationTtlMs = 120_000L
    const val InboxNotificationTtlMsMin = 30_000L
    const val InboxNotificationTtlMsMax = 600_000L

    /** 结果 TTL 默认 60s（即时提示口径）与滑杆上下限；验证码走 per-item 5 分钟覆盖。 */
    const val InboxResultTtlMs = 60_000L
    const val InboxResultTtlMsMin = 30_000L
    const val InboxResultTtlMsMax = 600_000L

    /** 三球默认落点：右缘纵向错开。 */
    const val NotificationBallY = 0.30f
    const val ResultBallY = 0.54f

    /** 统一收件箱容量默认值与滑杆上下限。 */
    const val InboxCapacity = 10
    const val InboxCapacityMin = 1
    const val InboxCapacityMax = 10

    /** 自定义图标透明度滑杆上下限（下限 0.1：全透明会让球找不着）。 */
    const val CustomIconAlphaMin = 0.1f
    const val CustomIconAlphaMax = 1f

}
