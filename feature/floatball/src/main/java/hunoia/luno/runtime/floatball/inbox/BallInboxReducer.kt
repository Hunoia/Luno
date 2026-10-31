package hunoia.luno.runtime.floatball.inbox

/**
 * 收件箱动作。Add.makeCurrent=false 用于「用户正在手动浏览时，新内容只入队不抢当前项」。
 */
internal sealed interface BallInboxAction {
    data class Add(
        val item: BallItem,
        val makeCurrent: Boolean = true,
    ) : BallInboxAction

    data class Remove(val id: String) : BallInboxAction

    data class SetPinned(
        val id: String,
        val pinned: Boolean,
    ) : BallInboxAction

    data class ClearSource(val source: BallSource) : BallInboxAction

    /** keepId 通常是气泡当前显示项：TTL 到期也先保留，等气泡隐藏后再清。 */
    data class Prune(val keepId: String? = null) : BallInboxAction

    /** 设置页调低容量后裁到上限；钉住项不淘汰。 */
    data object Trim : BallInboxAction

    data class Select(val id: String) : BallInboxAction

    data object Next : BallInboxAction

    data object Previous : BallInboxAction
}

/**
 * 纯状态 reducer：不依赖 Android、Service 或窗口，TTL / 容量 / 钉住 / 当前项
 * 全部在这里决定，方便 JVM 单测。
 */
internal object BallInboxReducer {

    fun reduce(
        state: BallInboxState,
        action: BallInboxAction,
        nowMs: Long,
        config: BallInboxConfig = BallInboxConfig(),
    ): BallInboxChange = when (action) {
        is BallInboxAction.Add -> add(state, action, nowMs, config)
        is BallInboxAction.Remove -> remove(state, action.id)
        is BallInboxAction.SetPinned -> setPinned(state, action, nowMs, config)
        is BallInboxAction.ClearSource -> clearSource(state, action.source)
        is BallInboxAction.Prune -> prune(state, action.keepId, nowMs, config)
        BallInboxAction.Trim -> trim(state, config)
        is BallInboxAction.Select -> select(state, action.id)
        BallInboxAction.Next -> cycle(state, forward = true)
        BallInboxAction.Previous -> cycle(state, forward = false)
    }

    private fun add(
        state: BallInboxState,
        action: BallInboxAction.Add,
        nowMs: Long,
        config: BallInboxConfig,
    ): BallInboxChange {
        // 手动浏览时保留当前项；新内容将成为当前项时，可以顺手清掉已过期的旧当前项。
        val keepId = if (action.makeCurrent) null else state.currentId
        val pruned = pruneState(state, nowMs, config, keepId)
        var working = pruned.state
        var currentChanged = pruned.currentChanged

        val item = action.item
        val existing = working.items.firstOrNull { it.id == item.id }
        if (existing != null) {
            // 同 id 更新：移到队首；重复复制时若原来已钉住，继续保持钉住。
            val merged = item.withPinned(existing.pinned || item.pinned)
            val newCurrentId =
                if (action.makeCurrent || working.currentId == null) merged.id else working.currentId
            val newState = working.copy(
                items = listOf(merged) + working.items.filterNot { it.id == item.id },
                currentId = newCurrentId,
            ).bump()
            return BallInboxChange(
                state = newState,
                updated = merged,
                expired = pruned.expired,
                duplicate = true,
                currentChanged = currentChanged || newCurrentId != state.currentId,
            )
        }

        if (isExpired(item, nowMs, config)) {
            return BallInboxChange(
                state = if (pruned.changed) working.bump() else working,
                expired = pruned.expired,
                dropped = item,
                currentChanged = currentChanged,
            )
        }

        val protectedCurrentId = if (action.makeCurrent) null else working.currentId
        val evicted = mutableListOf<BallItem>()
        while (working.items.size >= config.maxItems) {
            val candidate = working.items.asReversed().firstOrNull { candidate ->
                !candidate.pinned && candidate.id != protectedCurrentId
            }
            if (candidate == null) {
                val newState = if (pruned.changed || evicted.isNotEmpty()) working.bump() else working
                return BallInboxChange(
                    state = newState,
                    removed = evicted,
                    expired = pruned.expired,
                    dropped = item,
                    currentChanged = currentChanged || newState.currentId != state.currentId,
                )
            }
            val candidateIndex = working.items.indexOfFirst { it.id == candidate.id }
            val remaining = working.items.filterNot { it.id == candidate.id }
            val nextCurrentId = when {
                candidate.id != working.currentId -> working.currentId
                remaining.isEmpty() -> null
                else -> selectAfterRemoval(remaining, candidateIndex)
            }
            evicted += candidate
            working = working.copy(items = remaining, currentId = nextCurrentId)
        }

        val newCurrentId = if (action.makeCurrent || working.currentId == null) item.id else working.currentId
        val newState = working.copy(
            items = listOf(item) + working.items,
            currentId = newCurrentId,
        ).bump()
        return BallInboxChange(
            state = newState,
            added = item,
            removed = evicted,
            expired = pruned.expired,
            currentChanged = currentChanged || newCurrentId != state.currentId,
        )
    }

    private fun remove(state: BallInboxState, id: String): BallInboxChange {
        val index = state.items.indexOfFirst { it.id == id }
        if (index < 0) return BallInboxChange(state)

        val item = state.items[index]
        val remaining = state.items.filterNot { it.id == id }
        val newCurrentId = if (state.currentId == id) {
            selectAfterRemoval(remaining, index)
        } else {
            state.currentId
        }
        return BallInboxChange(
            state = state.copy(items = remaining, currentId = newCurrentId).bump(),
            removed = listOf(item),
            currentChanged = newCurrentId != state.currentId,
        )
    }

    private fun setPinned(
        state: BallInboxState,
        action: BallInboxAction.SetPinned,
        nowMs: Long,
        config: BallInboxConfig,
    ): BallInboxChange {
        val index = state.items.indexOfFirst { it.id == action.id }
        if (index < 0) return BallInboxChange(state)

        val item = state.items[index]
        // 通知不支持钉住；避免 UI 长按后产生「看起来钉住、实际没钉」的状态。
        if (item.source == BallSource.NOTIFICATION) return BallInboxChange(state)
        if (item.pinned == action.pinned) return BallInboxChange(state)

        val updated = item.withPinned(action.pinned)
        if (!action.pinned && isExpired(updated, nowMs, config)) {
            val remaining = state.items.filterNot { it.id == item.id }
            val newCurrentId = if (state.currentId == item.id) {
                selectAfterRemoval(remaining, index)
            } else {
                state.currentId
            }
            return BallInboxChange(
                state = state.copy(items = remaining, currentId = newCurrentId).bump(),
                removed = listOf(item),
                currentChanged = newCurrentId != state.currentId,
            )
        }

        val items = state.items.mapIndexed { itemIndex, old ->
            if (itemIndex == index) updated else old
        }
        return BallInboxChange(
            state = state.copy(items = items).bump(),
            updated = updated,
        )
    }

    private fun clearSource(state: BallInboxState, source: BallSource): BallInboxChange {
        val removed = state.items.filter { it.source == source }
        if (removed.isEmpty()) return BallInboxChange(state)

        val remaining = state.items.filterNot { it.source == source }
        val currentRemoved = removed.any { it.id == state.currentId }
        val newCurrentId = when {
            !currentRemoved -> state.currentId
            remaining.isEmpty() -> null
            else -> selectAfterRemoval(
                remaining = remaining,
                removedIndex = state.items.indexOfFirst { it.id == state.currentId },
            )
        }
        return BallInboxChange(
            state = state.copy(items = remaining, currentId = newCurrentId).bump(),
            removed = removed,
            currentChanged = newCurrentId != state.currentId,
        )
    }

    private fun prune(
        state: BallInboxState,
        keepId: String?,
        nowMs: Long,
        config: BallInboxConfig,
    ): BallInboxChange {
        val pruned = pruneState(state, nowMs, config, keepId)
        if (!pruned.changed) return BallInboxChange(state)
        return BallInboxChange(
            state = pruned.state.bump(),
            expired = pruned.expired,
            currentChanged = pruned.currentChanged,
        )
    }

    private fun trim(
        state: BallInboxState,
        config: BallInboxConfig,
    ): BallInboxChange {
        if (state.items.size <= config.maxItems) return BallInboxChange(state)

        val removed = mutableListOf<BallItem>()
        var working = state
        while (working.items.size - removed.size > config.maxItems) {
            // 从最旧端找未钉住项淘汰；只剩钉住项时保留超容状态。
            val candidate = working.items.asReversed().firstOrNull { !it.pinned } ?: break
            val index = working.items.indexOfFirst { it.id == candidate.id }
            val remaining = working.items.filterNot { it.id == candidate.id }
            val newCurrentId = if (working.currentId == candidate.id) {
                selectAfterRemoval(remaining, index)
            } else {
                working.currentId
            }
            removed += candidate
            working = working.copy(items = remaining, currentId = newCurrentId)
        }
        if (removed.isEmpty()) return BallInboxChange(state)
        return BallInboxChange(
            state = working.bump(),
            removed = removed,
            currentChanged = working.currentId != state.currentId,
        )
    }

    private fun select(state: BallInboxState, id: String): BallInboxChange {
        if (state.currentId == id) return BallInboxChange(state)
        if (state.items.none { it.id == id }) return BallInboxChange(state)
        return BallInboxChange(
            state = state.copy(currentId = id).bump(),
            currentChanged = true,
        )
    }

    private fun cycle(state: BallInboxState, forward: Boolean): BallInboxChange {
        if (state.items.isEmpty()) return BallInboxChange(state)
        val currentIndex = state.currentIndex
        val nextIndex = if (forward) {
            if (currentIndex < 0) 0 else (currentIndex + 1) % state.items.size
        } else {
            if (currentIndex < 0) state.items.lastIndex else
                (currentIndex - 1 + state.items.size) % state.items.size
        }
        val nextId = state.items[nextIndex].id
        if (nextId == state.currentId) return BallInboxChange(state)
        return BallInboxChange(
            state = state.copy(currentId = nextId).bump(),
            currentChanged = true,
        )
    }

    private fun pruneState(
        state: BallInboxState,
        nowMs: Long,
        config: BallInboxConfig,
        keepId: String?,
    ): PruneOutcome {
        val expired = state.items.filter { item ->
            item.id != keepId && isExpired(item, nowMs, config)
        }
        if (expired.isEmpty()) return PruneOutcome(state, emptyList(), false)

        val expiredIds = expired.mapTo(HashSet()) { it.id }
        val remaining = state.items.filterNot { it.id in expiredIds }
        val currentRemoved = state.currentId != null && state.currentId in expiredIds
        val newCurrentId = if (currentRemoved) {
            selectAfterRemoval(
                remaining = remaining,
                removedIndex = state.items.indexOfFirst { it.id == state.currentId },
            )
        } else {
            state.currentId
        }
        return PruneOutcome(
            state = state.copy(items = remaining, currentId = newCurrentId),
            expired = expired,
            currentChanged = newCurrentId != state.currentId,
        )
    }

    private fun selectAfterRemoval(remaining: List<BallItem>, removedIndex: Int): String? {
        if (remaining.isEmpty()) return null
        return remaining[removedIndex.coerceIn(0, remaining.lastIndex)].id
    }

    private fun isExpired(item: BallItem, nowMs: Long, config: BallInboxConfig): Boolean {
        if (item.pinned) return false
        if (nowMs < item.receivedAtElapsedMs) return false
        return nowMs - item.receivedAtElapsedMs >= config.ttlFor(item.source)
    }

    private fun BallInboxState.bump(): BallInboxState = copy(revision = revision + 1)

    private data class PruneOutcome(
        val state: BallInboxState,
        val expired: List<BallItem>,
        val currentChanged: Boolean,
    ) {
        val changed: Boolean get() = expired.isNotEmpty() || currentChanged
    }
}
