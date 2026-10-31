package hunoia.luno.runtime.floatball.inbox

/**
 * 收件箱策略。TTL 与容量先保持纯配置，Stage 3 再映射到可序列化的设置模型。
 */
internal data class BallInboxConfig(
    val notificationTtlMs: Long = 120_000L,
    val clipboardTtlMs: Long = 300_000L,
    val maxItems: Int = 10,
) {
    init {
        require(notificationTtlMs > 0L) { "notificationTtlMs 必须大于 0" }
        require(clipboardTtlMs > 0L) { "clipboardTtlMs 必须大于 0" }
        require(maxItems > 0) { "maxItems 必须大于 0" }
    }

    fun ttlFor(source: BallSource): Long = when (source) {
        BallSource.NOTIFICATION -> notificationTtlMs
        BallSource.CLIPBOARD -> clipboardTtlMs
    }
}

/**
 * 球上徽标的展示状态：数字只算未钉住项；只有钉住项时显示图钉。
 */
internal data class BadgeState(
    val unpinnedCount: Int,
    val pinnedCount: Int,
) {
    init {
        require(unpinnedCount >= 0) { "unpinnedCount 不能为负" }
        require(pinnedCount >= 0) { "pinnedCount 不能为负" }
    }

    val hasPinned: Boolean get() = pinnedCount > 0
    val visible: Boolean get() = unpinnedCount > 0 || hasPinned
    val showPinOnly: Boolean get() = unpinnedCount == 0 && hasPinned
    val countLabel: String? get() = unpinnedCount.takeIf { it > 0 }?.toString()
}

/**
 * 收件箱不可变状态。items 始终新 → 旧且 id 唯一；currentId 要么为 null（空队列），
 * 要么指向 items 中真实存在的项。
 */
internal data class BallInboxState(
    val items: List<BallItem> = emptyList(),
    val currentId: String? = null,
    val revision: Long = 0L,
) {
    init {
        require(items.map { it.id }.distinct().size == items.size) { "items 中存在重复 id" }
        require(items.isEmpty() == (currentId == null)) {
            "非空 items 必须有 currentId，空 items 的 currentId 必须为 null"
        }
        require(currentId == null || items.any { it.id == currentId }) {
            "currentId 必须指向 items 中存在的项"
        }
    }

    val current: BallItem? get() = currentId?.let { id -> items.firstOrNull { it.id == id } }

    val currentIndex: Int get() = items.indexOfFirst { it.id == currentId }

    val badge: BadgeState get() = BadgeState(
        unpinnedCount = items.count { !it.pinned },
        pinnedCount = items.count { it.pinned },
    )

    val countLabel: String?
        get() {
            if (items.size <= 1) return null
            val index = currentIndex
            if (index < 0) return null
            return "${index + 1}/${items.size}"
        }

    /**
     * 下一次 TTL 到期时刻（elapsedRealtime）。钉住项与 [keepId] 不参与；
     * 已经到期的项返回 [nowMs]，让调用方尽快跑一次 prune。
     */
    fun nextExpiryAt(
        nowMs: Long,
        config: BallInboxConfig,
        keepId: String? = null,
    ): Long? {
        val next = items.asSequence()
            .filter { !it.pinned && it.id != keepId }
            .map { it.receivedAtElapsedMs + config.ttlFor(it.source) }
            .minOrNull()
            ?: return null
        return next.coerceAtLeast(nowMs)
    }
}
