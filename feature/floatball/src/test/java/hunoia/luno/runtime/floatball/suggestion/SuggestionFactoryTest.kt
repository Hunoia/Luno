package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.LinkMatchType
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionDefaults
import hunoia.luno.config.model.RecognitionMatch
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.config.model.RecognitionSource
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.SuggestionPrimaryAction
import hunoia.luno.runtime.recognition.Recognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionFactoryTest {

    private fun pulse(
        title: String = "标题",
        text: String = "",
        bigText: String? = null,
    ) = NotificationPulse(
        packageName = "com.test",
        title = title,
        text = text,
        bigText = bigText,
        key = "k1",
    )

    private fun recognizer(vararg rules: RecognitionRule) =
        Recognizer.compile(RecognitionSettings(rules = rules.toList()))

    private fun textRule(
        id: String = "custom",
        name: String = "验证码",
        pattern: String,
        sources: Set<RecognitionSource> = RecognitionSource.Default,
        builtin: RecognitionBuiltin = RecognitionBuiltin.Copy,
    ) = RecognitionRule(
        id = id,
        name = name,
        sources = sources,
        match = RecognitionMatch.Text(pattern),
        output = RecognitionOutput.Builtin(builtin),
    )

    private fun linkRule(
        pattern: String,
        type: LinkMatchType = LinkMatchType.CONTAINS,
        sources: Set<RecognitionSource> = RecognitionSource.Default,
    ) = RecognitionRule(
        id = "link-$pattern",
        name = "链接",
        sources = sources,
        match = RecognitionMatch.Link(matchType = type, pattern = pattern),
        output = RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink),
    )

    @Test
    fun `notification hits the default verification code rule`() {
        val items = SuggestionFactory.fromNotification(
            pulse = pulse(text = "验证码 778899，5 分钟内有效"),
            recognizer = recognizer(*RecognitionDefaults.rules.toTypedArray()),
            sourceKey = "k1",
            nowMs = 1_000,
        )

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("default-code-zh", item.ruleId)
        assertEquals("验证码", item.title)
        assertEquals("778899", item.text)
        assertEquals(SuggestionPrimaryAction.COPY, item.action)
        assertTrue(item.fromCapture)
        assertEquals(1_000L, item.receivedAtElapsedMs)
        assertEquals(null, item.ttlMs)
    }

    @Test
    fun `notification without a rule hit yields no suggestion`() {
        val items = SuggestionFactory.fromNotification(
            pulse = pulse(text = "订单已发货"),
            recognizer = recognizer(textRule(pattern = "验证码\\s*(\\d{4,8})")),
            sourceKey = "k1",
            nowMs = 1_000,
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `big text wins over duplicate body text`() {
        val items = SuggestionFactory.fromNotification(
            pulse = pulse(text = "验证码 123456", bigText = "标题\n验证码 123456，5 分钟内有效"),
            recognizer = recognizer(*RecognitionDefaults.rules.toTypedArray()),
            sourceKey = "k1",
            nowMs = 1_000,
        )

        assertEquals("123456", items.single().text)
    }

    @Test
    fun `clipboard text rule hit is not duplicated by the link rule`() {
        val url = "https://example.com/a"
        val items = SuggestionFactory.fromClipboard(
            text = "打开 $url",
            recognizer = recognizer(
                textRule(
                    id = "grab-url",
                    name = "抓链接",
                    pattern = "(https://example\\.com/\\S+)",
                ),
                *RecognitionDefaults.rules.toTypedArray(),
            ),
            sourceKey = "clip",
            nowMs = 1_000,
        )

        // 文本规则先跑并吃掉这条链接：两个默认验证码规则不命中，内置链接规则不再出项。
        assertEquals(listOf("grab-url"), items.map { it.ruleId })
        assertEquals(url, items.single().text)
        assertFalse(items.single().fromCapture)
    }

    @Test
    fun `clipboard link without a rule hit still yields the builtin link item`() {
        val items = SuggestionFactory.fromClipboard(
            text = "见 https://example.com/x",
            recognizer = recognizer(*RecognitionDefaults.rules.toTypedArray()),
            sourceKey = "clip",
            nowMs = 1_000,
        )

        assertEquals(1, items.size)
        assertEquals(RecognitionDefaults.LinkRuleId, items.single().ruleId)
        assertEquals(SuggestionPrimaryAction.OPEN, items.single().action)
        assertEquals(listOf("https://example.com/x"), items.single().urls)
    }

    @Test
    fun `rule sources gate which inputs can hit`() {
        val rule = textRule(
            pattern = "验证码\\s*(\\d{4,8})",
            sources = setOf(RecognitionSource.Notification),
        )
        val settings = recognizer(rule)

        assertTrue(
            SuggestionFactory.fromClipboard(
                text = "验证码 123456",
                recognizer = settings,
                sourceKey = "clip",
                nowMs = 1,
            ).none { it.ruleId == "custom" },
        )
        assertEquals(
            "custom",
            SuggestionFactory.fromNotification(
                pulse = pulse(text = "验证码 123456"),
                recognizer = settings,
                sourceKey = "k1",
                nowMs = 1,
            ).single().ruleId,
        )
    }

    @Test
    fun `scene scan uses the same rule table and keeps the screen key`() {
        val items = SuggestionFactory.fromScene(
            text = "取件码 1234-5678",
            screenKey = "screen-1",
            recognizer = recognizer(
                textRule(id = "pickup", name = "取件码", pattern = "取件码\\s*(\\S+)"),
            ),
            nowMs = 1_000,
        )

        assertEquals(1, items.size)
        assertEquals("screen-1", items.single().sceneKey)
        assertEquals("1234-5678", items.single().text)
    }

    @Test
    fun `scene link only appears when a link rule covers it`() {
        val url = "https://www.xiaohongshu.com/discovery/item/abc"
        // 只有针对别家域名的规则：这条链接不该出建议。
        val otherOnly = recognizer(linkRule("b23.tv"))
        assertTrue(
            SuggestionFactory.fromScene(
                text = "页面标题",
                screenKey = "s1",
                recognizer = otherOnly,
                nowMs = 1,
                sceneUrl = url,
            ).isEmpty(),
        )

        val covered = recognizer(linkRule("xiaohongshu.com"))
        val items = SuggestionFactory.fromScene(
            text = "页面标题",
            screenKey = "s1",
            recognizer = covered,
            nowMs = 1,
            sceneUrl = url,
        )
        assertEquals(listOf(url), items.single().urls)
    }

    @Test
    fun `scene link refresh skips urls already used by rules`() {
        val url = "https://b23.tv/abc"
        val rules = recognizer(linkRule("b23.tv"))
        val refreshed = SuggestionFactory.sceneLinkItem(
            text = "页面标题",
            screenKey = "s1",
            recognizer = rules,
            nowMs = 2,
            sceneUrl = url,
            excludeUrls = setOf(url),
        )

        assertEquals(null, refreshed)
    }
}
