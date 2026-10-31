package hunoia.luno.runtime.floatball.inbox

/**
 * 智能球收件箱的内容来源。
 */
internal enum class BallSource {
    NOTIFICATION,
    CLIPBOARD,
}

/**
 * 收件箱条目公共元数据。
 *
 * 本阶段只放去重、TTL、排序和徽标需要的字段；气泡渲染所需的动作载荷
 * （PendingIntent / Uri / 链接目标等）在接入阶段再扩展，避免纯状态模型
 * 依赖 Android 组件。
 */
internal sealed interface BallItem {
    val id: String
    val source: BallSource
    val receivedAtElapsedMs: Long
    val pinned: Boolean
    val title: String
    val text: String

    fun withPinned(pinned: Boolean): BallItem
}

/**
 * 通知项。通知只用系统 key 去重，v1 不支持钉住。
 */
internal data class NotificationItem(
    val notificationKey: String,
    val packageName: String,
    override val title: String,
    override val text: String,
    override val receivedAtElapsedMs: Long,
) : BallItem {
    override val id: String = "notif:$notificationKey"
    override val source: BallSource = BallSource.NOTIFICATION
    override val pinned: Boolean get() = false

    override fun withPinned(pinned: Boolean): BallItem = this
}

/**
 * 剪贴板文本项。contentHash 由服务层按文本内容计算，避免把完整正文当 ID。
 */
internal data class ClipboardTextItem(
    val contentHash: String,
    override val text: String,
    override val receivedAtElapsedMs: Long,
    override val title: String = "剪贴板",
    override val pinned: Boolean = false,
) : BallItem {
    override val id: String = "clip:$contentHash"
    override val source: BallSource = BallSource.CLIPBOARD

    override fun withPinned(pinned: Boolean): BallItem = copy(pinned = pinned)
}

/**
 * 剪贴板图片项。uri 先以字符串保存，避免纯状态模型依赖 android.net.Uri。
 */
internal data class ClipboardImageItem(
    val contentHash: String,
    val uri: String,
    val mimeType: String?,
    override val receivedAtElapsedMs: Long,
    override val title: String = "图片",
    override val text: String = "",
    override val pinned: Boolean = false,
) : BallItem {
    override val id: String = "clip:$contentHash"
    override val source: BallSource = BallSource.CLIPBOARD

    override fun withPinned(pinned: Boolean): BallItem = copy(pinned = pinned)
}
