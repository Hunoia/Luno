package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionSource
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.SuggestionItem
import hunoia.luno.runtime.floatball.inbox.SuggestionPrimaryAction
import hunoia.luno.runtime.recognition.CompiledRecognizer
import hunoia.luno.runtime.recognition.RecognitionInput
import hunoia.luno.runtime.recognition.RecognizedItem

/**
 * 建议项构造：识别器结果 → 胶囊条目。
 *
 * **这里不判任何规则**：匹配、链接挑选、敏感判定都在 `:core:runtime` 的 `Recognizer`
 * 里做完了，本对象只做「识别结果 → 收起箱状态」的纯投影（方便 JVM 单测）。
 */
internal object SuggestionFactory {

    private const val GenericLabel = "建议"

    /** 通知：标题 + 正文（bigText 存在时优先，避免正文与展开文本重复）。 */
    fun fromNotification(
        pulse: NotificationPulse,
        recognizer: CompiledRecognizer,
        sourceKey: String,
        nowMs: Long,
    ): List<SuggestionItem> = project(
        source = RecognitionSource.Notification,
        text = notificationText(pulse),
        recognizer = recognizer,
        sourceKey = sourceKey,
        nowMs = nowMs,
        sceneKey = null,
    )

    /** 剪贴板：规则命中 + 内置链接（两者都由识别器同一次匹配给出）。 */
    fun fromClipboard(
        text: String,
        recognizer: CompiledRecognizer,
        sourceKey: String,
        nowMs: Long,
    ): List<SuggestionItem> = project(
        source = RecognitionSource.Clipboard,
        text = text,
        recognizer = recognizer,
        sourceKey = sourceKey,
        nowMs = nowMs,
        sceneKey = null,
    )

    /**
     * 情景识别（当前界面文本）：只认识别器结果。
     *
     * 「界面上链接太多、只有命中分发规则才提示」这个旧闸门现在由规则表自己表达
     * （内置「链接」规则默认对 `Scene` 生效；用户可从该来源摘掉它，或依赖具体应用规则），
     * 代码里不再有 `linkRules.isNotEmpty()` 之类的隐式判断。
     *
     * [sceneUrl] 是特权通道（Shizuku 读 XWeb 日志）拿到的当前页真实链接：它只是多一段
     * 链接候选，直接拼进待识别文本，判不判仍由规则说了算。
     */
    fun fromScene(
        text: String,
        screenKey: String,
        recognizer: CompiledRecognizer,
        nowMs: Long,
        sceneUrl: String? = null,
    ): List<SuggestionItem> {
        val scan = if (sceneUrl.isNullOrBlank()) text else "$text\n$sceneUrl"
        if (scan.isBlank()) return emptyList()
        return project(
            source = RecognitionSource.Scene,
            text = scan,
            recognizer = recognizer,
            sourceKey = "scene:$screenKey",
            nowMs = nowMs,
            sceneKey = screenKey,
        )
    }

    /**
     * 情景识别补到真实链接后的重算：只重算链接项。
     *
     * 返回 null = 这条链接没有命中任何链接规则（调用方什么都不用做）。
     */
    fun sceneLinkItem(
        text: String,
        screenKey: String,
        recognizer: CompiledRecognizer,
        nowMs: Long,
        sceneUrl: String?,
        excludeUrls: Set<String> = emptySet(),
    ): SuggestionItem? {
        if (sceneUrl.isNullOrBlank() || sceneUrl in excludeUrls) return null
        return fromScene(
            text = text,
            screenKey = screenKey,
            recognizer = recognizer,
            nowMs = nowMs,
            sceneUrl = sceneUrl,
        ).firstOrNull { item ->
            item.urls.any { it == sceneUrl } && item.urls.all { it !in excludeUrls }
        }
    }

    private fun project(
        source: RecognitionSource,
        text: String,
        recognizer: CompiledRecognizer,
        sourceKey: String,
        nowMs: Long,
        sceneKey: String?,
    ): List<SuggestionItem> = recognizer
        .match(RecognitionInput(source, text, sourceKey))
        .map { item -> toSuggestion(item, sourceKey, nowMs, sceneKey) }

    fun toSuggestion(
        item: RecognizedItem,
        sourceKey: String,
        nowMs: Long,
        sceneKey: String?,
    ): SuggestionItem {
        val action = primaryActionOf(item)
        // OPEN 但值不是链接：创建阶段就降级成 COPY（与旧口径一致，避免点了没反应）。
        val effective = if (action == SuggestionPrimaryAction.OPEN && item.urls.isEmpty()) {
            SuggestionPrimaryAction.COPY
        } else {
            action
        }
        return SuggestionItem(
            sourceKey = sourceKey,
            title = item.ruleName.ifBlank { GenericLabel },
            text = item.value,
            receivedAtElapsedMs = nowMs,
            ruleId = item.ruleId,
            action = effective,
            fromCapture = item.urls.isEmpty(),
            urls = item.urls,
            recognized = item,
            sceneKey = sceneKey,
        )
    }

    private fun primaryActionOf(item: RecognizedItem): SuggestionPrimaryAction =
        when (val output = item.output) {
            is RecognitionOutput.Builtin -> when (output.kind) {
                RecognitionBuiltin.Copy, RecognitionBuiltin.CopyAndClear ->
                    SuggestionPrimaryAction.COPY
                RecognitionBuiltin.OpenLink -> SuggestionPrimaryAction.OPEN
                RecognitionBuiltin.Share -> SuggestionPrimaryAction.SHARE
            }
            // 动作 / 通知输出：主动作就是「跑这条规则」，执行细节交给 RecognitionExecutor。
            is RecognitionOutput.Action, is RecognitionOutput.Notify ->
                SuggestionPrimaryAction.RUN_ACTION
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
