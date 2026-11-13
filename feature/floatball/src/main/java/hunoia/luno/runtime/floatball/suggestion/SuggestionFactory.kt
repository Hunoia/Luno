package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.link.LinkUrls
import hunoia.luno.runtime.floatball.inbox.SuggestionItem
import hunoia.luno.runtime.floatball.inbox.SuggestionKind

/**
 * 建议项构造：通知里的验证码、剪贴板里的链接。
 * 纯逻辑（链接目标应用解析留给服务层），方便 JVM 单测。
 */
internal object SuggestionFactory {

    /** 验证码建议的标题；界面可再叠加「验证码」文案。 */
    const val CodeLabel = "验证码"
    const val LinkLabel = "链接"

    /**
     * 从通知里提取验证码建议；没有唯一候选时返回 null。
     * [ttlMs] 由调用方传入固定 5 分钟。
     */
    fun verificationCode(
        pulse: NotificationPulse,
        sourceKey: String,
        nowMs: Long,
        ttlMs: Long,
    ): SuggestionItem? {
        val code = VerificationCodeParser.parse(pulse.title, pulse.text, pulse.bigText)
            ?: return null
        return SuggestionItem(
            kind = SuggestionKind.CODE,
            sourceKey = sourceKey,
            title = CodeLabel,
            text = code,
            receivedAtElapsedMs = nowMs,
            ttlMs = ttlMs,
            code = code,
        )
    }

    /**
     * 从剪贴板文本里提取链接建议；没有可分发链接时返回 null。
     * 链接建议不依赖「链接分发」总开关，目标应用由服务层按规则另行解析。
     */
    fun link(
        text: String,
        sourceKey: String,
        nowMs: Long,
    ): SuggestionItem? {
        val urls = LinkUrls.extractUrls(text)
        if (urls.isEmpty()) return null
        return SuggestionItem(
            kind = SuggestionKind.LINK,
            sourceKey = sourceKey,
            title = LinkLabel,
            text = urls.first(),
            receivedAtElapsedMs = nowMs,
            ttlMs = null,
            urls = urls,
        )
    }
}
