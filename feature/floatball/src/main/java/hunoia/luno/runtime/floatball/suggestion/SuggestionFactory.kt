package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.LinkDispatchRule
import hunoia.luno.config.model.SuggestionRuleAction
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.link.LinkUrls
import hunoia.luno.runtime.floatball.inbox.SuggestionItem
import hunoia.luno.runtime.floatball.inbox.SuggestionPrimaryAction
import hunoia.luno.runtime.suggestion.CompiledSuggestionRules
import hunoia.luno.runtime.suggestion.SuggestionRules
import hunoia.luno.runtime.suggestion.SuggestionSlot

/**
 * 建议项构造：通知 / 剪贴板 / 当前界面文本按规则抓取，剪贴板与情景识别再补内置链接识别。
 * 纯逻辑（链接目标应用解析留给服务层），方便 JVM 单测。
 */
internal object SuggestionFactory {

    const val LinkLabel = "链接"
    private const val GenericLabel = "建议"

    /**
     * 通知规则扫描：标题 + 正文（bigText 存在时优先，避免正文与展开文本重复）。
     * 规则命中多少条就生成多少条，顺序与规则表一致。
     */
    fun fromNotification(
        pulse: NotificationPulse,
        rules: CompiledSuggestionRules,
        sourceKey: String,
        nowMs: Long,
    ): List<SuggestionItem> {
        val text = notificationText(pulse)
        return SuggestionRules.slotsOf(text, rules).map { slot -> ruleItem(slot, sourceKey, nowMs) }
    }

    /**
     * 剪贴板规则扫描 + 内置链接识别。
     *
     * 规则捕获到的 URL 会从链接建议里剔除：同一链接不再既生成规则建议又生成链接建议。
     * 规则全部命中失败时，行为与旧版一致，只生成链接建议。
     */
    fun fromClipboard(
        text: String,
        rules: CompiledSuggestionRules,
        sourceKey: String,
        nowMs: Long,
    ): List<SuggestionItem> {
        val slots = SuggestionRules.slotsOf(text, rules)
        val hitUrls = slots
            .flatMap { LinkUrls.extractUrls(it.value) }
            .toSet()
        val linkUrls = LinkUrls.extractUrls(text).filterNot { it in hitUrls }
        val items = slots.mapTo(ArrayList(slots.size + 1)) { slot ->
            ruleItem(slot, sourceKey, nowMs)
        }
        if (linkUrls.isNotEmpty()) {
            items += SuggestionItem(
                sourceKey = sourceKey,
                title = LinkLabel,
                text = linkUrls.first(),
                receivedAtElapsedMs = nowMs,
                ttlMs = null,
                action = SuggestionPrimaryAction.OPEN,
                sensitive = false,
                urls = linkUrls,
            )
        }
        return items
    }

    /**
     * 情景识别（当前界面文本）：规则命中 + 只提示可分发的链接。
     *
     * 链接口径比剪贴板窄：剪贴板是用户主动复制的，任何 http(s) 都值得提示；界面里几乎处处是
     * 链接（浏览器、聊天页、网页正文），未命中「链接分发」规则的只能走分享回退，弹出来只是噪声。
     * 所以 [linkRules] 为空或分发总开关关闭时不出链接建议 —— 用户先配规则才有「打开链接」。
     *
     * [sceneUrl] 是特权通道（Shizuku 读 XWeb 日志）拿到的当前页真实链接，已由生产者校验过
     * 「属于这一屏」。它只是链接候选的头一条，判不判分发给 [linkRules] 说了算——与界面文本里
     * 抽出来的链接完全同一个口径，不单开一条快捷路径。
     */
    fun fromScene(
        text: String,
        screenKey: String,
        rules: CompiledSuggestionRules,
        linkRules: List<LinkDispatchRule>,
        linkDispatchEnabled: Boolean,
        nowMs: Long,
        sceneUrl: String? = null,
    ): List<SuggestionItem> {
        if (text.isBlank() && sceneUrl == null) return emptyList()
        val sourceKey = "scene:$screenKey"
        val slots = SuggestionRules.slotsOf(text, rules)
        val hitUrls = slots
            .flatMap { LinkUrls.extractUrls(it.value) }
            .toSet()
        val items = ArrayList<SuggestionItem>(slots.size + 1)
        slots.forEach { slot ->
            items += ruleItem(slot, sourceKey, nowMs).copy(sceneKey = screenKey)
        }
        if (linkDispatchEnabled && linkRules.isNotEmpty()) {
            sceneLinkItem(
                text = text,
                screenKey = screenKey,
                linkRules = linkRules,
                nowMs = nowMs,
                sceneUrl = sceneUrl,
                excludeUrls = hitUrls,
            )?.let { items += it }
        }
        return items
    }

    /**
     * 内置链接项：从「界面文本里的 URL + 特权通道补到的真实 URL」里挑出命中分发规则的。
     *
     * 单独抽出来供链接补充只重算这一项（[fromScene] 的规则匹配部分在这条路径上完全跳过）。
     */
    fun sceneLinkItem(
        text: String,
        screenKey: String,
        linkRules: List<LinkDispatchRule>,
        nowMs: Long,
        sceneUrl: String?,
        excludeUrls: Set<String> = emptySet(),
    ): SuggestionItem? {
        if (linkRules.isEmpty()) return null
        val candidates = buildList {
            sceneUrl?.let(::add)
            addAll(LinkUrls.extractUrls(text))
        }
        val linkUrls = candidates
            .filterNot { it in excludeUrls }
            .filter { LinkUrls.firstMatch(it, linkRules) != null }
            .distinct()
        if (linkUrls.isEmpty()) return null
        return SuggestionItem(
            sourceKey = "scene:$screenKey",
            title = LinkLabel,
            text = linkUrls.first(),
            receivedAtElapsedMs = nowMs,
            ttlMs = null,
            action = SuggestionPrimaryAction.OPEN,
            sensitive = false,
            urls = linkUrls,
            sceneKey = screenKey,
        )
    }

    private fun ruleItem(
        slot: SuggestionSlot,
        sourceKey: String,
        nowMs: Long,
    ): SuggestionItem {
        val raw = slot.value
        val urls = LinkUrls.extractUrls(raw)
        val exactUrl = urls.firstOrNull { it == raw }
        val openUrl = if (slot.action == SuggestionRuleAction.OPEN) urls.firstOrNull() else null
        val action = when {
            slot.action == SuggestionRuleAction.OPEN && openUrl == null ->
                SuggestionPrimaryAction.COPY
            slot.action == SuggestionRuleAction.OPEN -> SuggestionPrimaryAction.OPEN
            slot.action == SuggestionRuleAction.SHARE -> SuggestionPrimaryAction.SHARE
            else -> SuggestionPrimaryAction.COPY
        }
        val itemText = if (openUrl != null) openUrl else raw
        val itemUrls = when {
            openUrl != null -> listOf(openUrl)
            urls.isNotEmpty() -> urls
            else -> emptyList()
        }
        return SuggestionItem(
            sourceKey = sourceKey,
            title = slot.label.ifBlank { GenericLabel },
            text = itemText,
            receivedAtElapsedMs = nowMs,
            ttlMs = null,
            ruleId = slot.ruleId,
            action = action,
            sensitive = openUrl == null && exactUrl == null,
            urls = itemUrls,
        )
    }

    internal fun notificationText(pulse: NotificationPulse): String {
        val text = pulse.text
        val bigText = pulse.bigText
        val body = when {
            bigText.isNullOrBlank() -> text
            text.isBlank() -> bigText
            bigText.contains(text) -> bigText
            else -> "$text\n$bigText"
        }
        return listOf(pulse.title, body)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}
