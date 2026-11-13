package hunoia.luno.runtime.floatball.inbox

import java.security.MessageDigest

/**
 * 智能球收件箱的内容来源。
 *
 * 现在只有两条独立队列：普通通知进通知胶囊，验证码/链接进建议胶囊。
 */
internal enum class BallSource {
    NOTIFICATION,
    SUGGESTION,
}

/**
 * 收件箱条目公共元数据。
 *
 * 气泡渲染所需的动作载荷（验证码 / 链接 / 目标应用）放在具体条目里，
 * 纯状态模型不依赖 Android 组件。
 */
internal sealed interface BallItem {
    val id: String
    val source: BallSource
    val receivedAtElapsedMs: Long

    /** 本条的固定 TTL；null = 跟随队列配置里该来源的默认值。 */
    val ttlMs: Long?
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
    override val ttlMs: Long? = null,
) : BallItem {
    override val id: String = "notif:$notificationKey"
    override val source: BallSource = BallSource.NOTIFICATION
    override val pinned: Boolean get() = false

    override fun withPinned(pinned: Boolean): BallItem = this
}

/** 建议类型：验证码只提供「复制」；链接提供「打开 / 复制 / 分享」。 */
internal enum class SuggestionKind {
    CODE,
    LINK,
}

/**
 * 建议项（验证码或链接）。
 *
 * [sourceKey] 是来源去重键（通知 key / 剪贴板内容指纹），用于「滑掉后短时间同源抑制重弹」。
 * 验证码正文可能敏感：本类的 [toString] 刻意只保留类型与哈希 ID，不输出正文。
 */
internal data class SuggestionItem(
    val kind: SuggestionKind,
    val sourceKey: String,
    override val title: String,
    override val text: String,
    override val receivedAtElapsedMs: Long,
    override val ttlMs: Long? = null,
    val code: String? = null,
    val urls: List<String> = emptyList(),
    val targetPackage: String? = null,
    val targetLabel: String? = null,
    override val pinned: Boolean = false,
) : BallItem {
    override val id: String = "sug:${stableHash("$kind|$sourceKey|${code ?: urls.joinToString("|")}")}"
    override val source: BallSource = BallSource.SUGGESTION

    val primaryUrl: String? get() = urls.firstOrNull()
    val linkCount: Int get() = urls.size

    override fun withPinned(pinned: Boolean): BallItem = copy(pinned = pinned)

    /** 防泄露：验证码与链接正文不进日志。 */
    override fun toString(): String =
        "SuggestionItem(kind=$kind, id=$id, sourceKey=${sourceKey.take(8)}, pinned=$pinned)"
}

/** 内容指纹哈希（sha-256 前 16 位十六进制），稳定且不落原文。 */
internal fun stableHash(raw: String): String = runCatching {
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    digest.take(8).joinToString("") { "%02x".format(it) }
}.getOrDefault((raw.hashCode().toLong() and 0xFFFFFFFFL).toString(16))
