package hunoia.luno.core.domain

import hunoia.luno.config.model.NotificationDisplaySettings

/**
 * 通知呈现能力契约（通用通知设置，见 capability/notify/README.md）。
 *
 * 归属判据同 `AppLauncher`：呈现机制的变化原因是「平台通知策略」（焦点通知协议、
 * XMSF 鉴权绕过、promoted ongoing 权限），与任何单一 feature 的变化原因不同，
 * 所以默认实现住 `:capability:notify`，调用方（habit / backup / app 设置页）只持有契约。
 *
 * 回退语义（调用方负责，契约不代发）：请求 ISLAND 而设备不支持 / 绕过不可用 / 发布失败时，
 * 发布器只返回 [IslandPublishResult]；调用方按结果降级 LIVE（Android 16+ promoted 可用）
 * 否则 STANDARD。内容 / 通道 / 回退链归调用方，因为标准通知必须由调用方构造。
 */

/** ISLAND（超级岛）设备能力探测结果。 */
enum class IslandCapability {
    /** 全部条件满足：Xiaomi + protocol≥3 + island 特性位 + 绕过方法存在。 */
    READY,

    /** Xiaomi 但缺条件：走 LIVE / STANDARD 回退链。 */
    FALLBACK,

    /** 非 Xiaomi 设备。 */
    OTHER_ANDROID,
}

/** ISLAND 发布结果；`posted=false` 时调用方按回退链重发普通通知。 */
sealed interface IslandPublishResult {
    /** 焦点通知已投递且通过了校验窗口（未被 SystemUI 撤回）。 */
    data object Posted : IslandPublishResult

    /** 投递后在校验窗口内被撤回（鉴权未过）。 */
    data object Rejected : IslandPublishResult

    /** 设备能力不满足或绕过不可用。 */
    data class Unsupported(val reason: String) : IslandPublishResult

    /** 发布抛错（含 XMSF 网络恢复失败）。 */
    data class Failed(val message: String) : IslandPublishResult
}

/**
 * 长按岛卡片拖拽分享的内容（`param_island.shareData`）。
 *
 * 实测（LinkGo）：三段都是纯文本，落进聊天输入框时 `shareContent` 是最终插入的文本，
 * `title` / `content` 只是拖拽时的预览卡；`shareContent` 超长会被系统截断。
 */
data class IslandShareSpec(
    val title: String,
    val content: String,
    val shareContent: String,
)

/**
 * 一条 ISLAND 通知的内容规格。通道由调用方创建（通道归属调用方语义），
 * 发布器只负责焦点 payload 与投递事务。
 */
data class IslandNotificationSpec(
    val channelId: String,
    val notificationId: Int,
    val title: String,
    /** 展开态正文。 */
    val text: String,
    /** 小岛 / 胶囊摘要文案（实现侧截断，超长不进 payload）。 */
    val capsuleText: String,
    /** 首发 是否自动展开大岛。 */
    val autoExpand: Boolean = false,
    /** 大岛停留秒数（`param_island.expandedTime`）；0 = 不展开。 */
    val expandedSeconds: Int = NotificationDisplaySettings.DEFAULT_ISLAND_EXPAND_SECONDS,
    /** 外发光（`param_v2.outEffectSrc = outer_glow`）。 */
    val outerGlow: Boolean = false,
    /** 焦点通知是否同时进通知栏（`isShowNotification`）；false = 只有岛。 */
    val showInShade: Boolean = true,
    /** 长按岛卡片拖拽分享；null = 不支持拖拽。 */
    val share: IslandShareSpec? = null,
    /**
     * 投递事务里切断 XMSF 网络的时长（毫秒）；0 = 用发布器默认值。
     * 由通用通知设置的「断网时间」驱动，调用方不关心绕过细节。
     */
    val blockWindowMs: Int = 0,
    /** 岛超时秒数；0 = 不设超时。 */
    val timeoutSeconds: Int = 0,
    /** true = 常驻（ongoing，habit）；false = 一次性事件（backup），到时 / 点击自动收走。 */
    val ongoing: Boolean = true,
    /** 强调色（ARGB）：标题 / 高亮文字用；**不做背景**。null = 不染色。 */
    val accentColor: Int? = null,
    /** 岛背景色（ARGB）：null = 系统默认（不染背景）。与 [accentColor] 分开，强调色不该当背景。 */
    val backgroundColor: Int? = null,
    /** 点击岛主体的 Activity Intent URI；null = 不响应点击。 */
    val contentIntentUri: String? = null,
    /** 岛上的动作（最多 2 个，超出的丢弃）；key 用于日志追踪。 */
    val actions: List<IslandActionSpec> = emptyList(),
)

/**
 * 岛主体 / 按钮的 Intent 载体。`intentUri` 是 `Intent.toUri(URI_INTENT_SCHEME)` 产物
 * （含 action / component / extras）；PendingIntent 不进契约层（同 [NotificationPulse]），
 * 由发布器按 [intentKind] 构建，包成 `Notification.Action` 放进 `miui.focus.actions`
 * Bundle（键与 `textButton[i].action` 同名）。裸 PendingIntent 不放：SystemUI 取的是
 * Action 的 `actionIntent`，裸 PendingIntent 能渲染按钮但点击无反应（2026-12 实测）。
 */
enum class IslandIntentKind { ACTIVITY, BROADCAST }

/** 岛上按钮（最多投递 2 个）。 */
data class IslandActionSpec(
    val key: String,
    val title: String,
    val intentUri: String,
    val intentKind: IslandIntentKind = IslandIntentKind.ACTIVITY,
    /** 高亮（实底色按钮）；否则默认样式。 */
    val highlighted: Boolean = false,
    /**
     * 点击后是否让 SystemUI 立刻收起岛。
     *
     * `false` 用于需要展示结果的动作（习惯「+1」）：岛原地更新内容，由发布方重投；
     * `true` 用于一按就结束的动作（推迟 / 关闭）。
     */
    val collapseOnClick: Boolean = true,
)

interface NotificationPresenter {
    /** ISLAND 能力探测（纯读，可缓存由实现决定）。 */
    fun probeIslandCapability(): IslandCapability

    /**
     * 该通道是否允许悬浮（HyperOS `NotificationChannel.canShowFloat()` 隐藏方法，
     * 取不到时按 importance ≥ HIGH 兜底）。不允许悬浮时不必要求首帧展开大岛。
     */
    fun canChannelFloat(channelId: String): Boolean

    /**
     * 焦点鉴权预热：真发一条微型焦点通知再收走，让 SystemUI 把本包的鉴权结果
     * 缓存住（首次投递要等 XMSF 回包，容易落在校验窗口外被当成撤回）。
     * 无能力 / 绕过不可用时静默返回。
     */
    suspend fun prewarmIslandAuth(channelId: String)

    /**
     * 以 ISLAND 模式发布一条通知。同步语义（内部含 ~220ms 校验窗口），
     * 调用方须在协程 / 工作线程调用；失败时调用方执行 LIVE → STANDARD 回退链。
     */
    suspend fun publishIsland(spec: IslandNotificationSpec): IslandPublishResult
}
