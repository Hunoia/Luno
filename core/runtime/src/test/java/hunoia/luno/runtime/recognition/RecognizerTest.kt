package hunoia.luno.runtime.recognition

import hunoia.luno.config.model.LinkMatchType
import hunoia.luno.config.model.LinkMissMode
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionDefaults
import hunoia.luno.config.model.RecognitionMatch
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.config.model.RecognitionSource
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 识别器门禁：**输入 → 规则 → 输出**的唯一判定路径。
 *
 * 钉死的是「谁都不许自己判规则」：规则顺序、来源过滤、链接提取、输出透传、
 * 敏感判定、动作参数替换。消费者（胶囊 / 识屏工具条 / 外部入口）只消费这些结论。
 */
class RecognizerTest {

    private fun textRule(
        id: String = "r-text",
        pattern: String,
        sources: Set<RecognitionSource> = RecognitionSource.Default,
        output: RecognitionOutput = RecognitionOutput.Builtin(RecognitionBuiltin.Copy),
        enabled: Boolean = true,
    ) = RecognitionRule(
        id = id,
        name = id,
        enabled = enabled,
        sources = sources,
        match = RecognitionMatch.Text(pattern),
        output = output,
    )

    private fun linkRule(
        id: String = "r-link",
        pattern: String,
        matchType: LinkMatchType = LinkMatchType.CONTAINS,
        sources: Set<RecognitionSource> = RecognitionSource.Default,
        output: RecognitionOutput = RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink),
        template: String = "",
        excludes: List<String> = emptyList(),
        missAction: LinkMissMode? = null,
        enabled: Boolean = true,
    ) = RecognitionRule(
        id = id,
        name = id,
        enabled = enabled,
        sources = sources,
        match = RecognitionMatch.Link(
            matchType = matchType,
            pattern = pattern,
            template = template,
            excludes = excludes,
            missAction = missAction,
        ),
        output = output,
    )

    private fun compiled(vararg rules: RecognitionRule) =
        Recognizer.compile(RecognitionSettings(rules = rules.toList()))

    // ---- 基本形态 ----

    @Test
    fun `text rule takes the first capture group`() {
        val items = compiled(textRule(pattern = "验证码\\s*(\\d{6})"))
            .match(RecognitionInput(RecognitionSource.Clipboard, "验证码 123456"))

        assertEquals(1, items.size)
        assertEquals("123456", items.single().value)
        assertTrue(items.single().sensitive)
        assertTrue(items.single().urls.isEmpty())
    }

    @Test
    fun `text rule without a group takes the whole match`() {
        val items = compiled(textRule(pattern = "订单\\d{6}"))
            .match(RecognitionInput(RecognitionSource.Clipboard, "您的订单123456已发货"))

        assertEquals("订单123456", items.single().value)
    }

    @Test
    fun `link rule yields urls and value`() {
        val items = compiled(linkRule(pattern = "b23.tv"))
            .match(RecognitionInput(RecognitionSource.Clipboard, "看这个 https://b23.tv/abc 好玩"))

        assertEquals(listOf("https://b23.tv/abc"), items.single().urls)
        assertEquals("https://b23.tv/abc", items.single().value)
        assertFalse(items.single().sensitive)
    }

    @Test
    fun `empty link pattern matches any dispatchable link`() {
        val items = compiled(linkRule(pattern = ""))
            .match(RecognitionInput(RecognitionSource.Clipboard, "https://example.com/x"))

        assertEquals(1, items.size)
    }

    @Test
    fun `multiple links collapse into a single item with all urls`() {
        val items = compiled(linkRule(pattern = "example.com"))
            .match(
                RecognitionInput(
                    RecognitionSource.Clipboard,
                    "https://example.com/a 和 https://example.com/b",
                ),
            )

        assertEquals(1, items.size)
        assertEquals(2, items.single().urls.size)
        assertEquals("https://example.com/a", items.single().value)
    }

    // ---- 顺序与会话 ----

    @Test
    fun `rules run in table order`() {
        val items = compiled(
            textRule(id = "first", pattern = "(\\d{4})"),
            textRule(id = "second", pattern = "(\\d{4})"),
        ).match(RecognitionInput(RecognitionSource.Clipboard, "code 5678"))

        assertEquals(listOf("first", "second"), items.map { it.ruleId })
    }

    @Test
    fun `text rule hit consumes the url so the link rule does not repeat it`() {
        val url = "https://b23.tv/abc"
        val items = compiled(
            textRule(id = "grab", pattern = "(https://b23\\.tv/\\S+)"),
            linkRule(pattern = "b23.tv"),
        ).match(RecognitionInput(RecognitionSource.Clipboard, "看看 $url"))

        assertEquals(listOf("grab"), items.map { it.ruleId })
    }

    @Test
    fun `sources gate the rule`() {
        val rule = textRule(pattern = "(\\d{6})", sources = setOf(RecognitionSource.Notification))
        val recognizer = compiled(rule)

        assertTrue(
            recognizer.match(RecognitionInput(RecognitionSource.Clipboard, "123456")).isEmpty(),
        )
        assertEquals(
            1,
            recognizer.match(RecognitionInput(RecognitionSource.Notification, "123456")).size,
        )
    }

    @Test
    fun `disabled rules and invalid regex are skipped without throwing`() {
        val recognizer = compiled(
            textRule(id = "off", pattern = "(\\d+)", enabled = false),
            textRule(id = "bad", pattern = "([unclosed"),
            textRule(id = "ok", pattern = "(\\d+)"),
        )
        val items = recognizer.match(RecognitionInput(RecognitionSource.Clipboard, "42"))

        assertEquals(listOf("ok"), items.map { it.ruleId })
    }

    @Test
    fun `empty rule table recognizes nothing`() {
        // 没有总开关：规则全删 = 什么都不识别（少一个状态，语义唯一）。
        val recognizer = Recognizer.compile(RecognitionSettings(rules = emptyList()))
        assertTrue(recognizer.isEmpty)
        assertTrue(
            recognizer.match(RecognitionInput(RecognitionSource.Clipboard, "验证码 123456")).isEmpty(),
        )
    }

    // ---- 链接语义 ----

    @Test
    fun `domain match rejects lookalike suffix domains`() {
        val rule = linkRule(pattern = "bilibili.com", matchType = LinkMatchType.DOMAIN)
        assertEquals(
            1,
            compiled(rule).match(
                RecognitionInput(RecognitionSource.Clipboard, "https://www.bilibili.com/x"),
            ).size,
        )
        assertTrue(
            compiled(rule).match(
                RecognitionInput(RecognitionSource.Clipboard, "https://bilibili.com.evil.com/x"),
            ).isEmpty(),
        )
    }

    @Test
    fun `excludes skip the rule`() {
        val rule = linkRule(pattern = "example.com", excludes = listOf("/img/"))
        assertTrue(
            compiled(rule).match(
                RecognitionInput(RecognitionSource.Clipboard, "https://example.com/img/1"),
            ).isEmpty(),
        )
    }

    @Test
    fun `template rewrites the value used for dispatch`() {
        val items = compiled(linkRule(pattern = "b23.tv", template = "app://open?u={url_enc}"))
            .match(RecognitionInput(RecognitionSource.Clipboard, "https://b23.tv/abc"))

        assertTrue(items.single().value.startsWith("app://open?u="))
        // 原链接仍在 urls 里：分发 / 选择页需要真实地址。
        assertEquals(listOf("https://b23.tv/abc"), items.single().urls)
    }

    @Test
    fun `bare domains are allowed for scene and screen pick but not clipboard`() {
        val rule = linkRule(pattern = "")
        val text = "看 github.com/Hunoia/Luno"
        assertTrue(
            compiled(rule).match(RecognitionInput(RecognitionSource.Clipboard, text)).isEmpty(),
        )
        assertEquals(
            1,
            compiled(rule).match(RecognitionInput(RecognitionSource.Scene, text)).size,
        )
        assertEquals(
            1,
            compiled(rule).match(RecognitionInput(RecognitionSource.ScreenPick, text)).size,
        )
    }

    // ---- 输出 ----

    @Test
    fun `action output is passed through untouched`() {
        val action = StoredAction.of("app.launch", "packageName" to "com.example")
        val items = compiled(
            textRule(pattern = "(\\d{6})", output = RecognitionOutput.Action(action)),
        ).match(RecognitionInput(RecognitionSource.Clipboard, "123456"))

        assertEquals(action, items.single().action)
        assertNull(items.single().builtin)
    }

    @Test
    fun `fillRecognition substitutes the captured value into params`() {
        val action = StoredAction.of(
            "intent.openUrl",
            "url" to "https://example.com/code?c=%q&h={host}",
        )
        val item = RecognizedItem(
            ruleId = "r",
            ruleName = "r",
            value = "123456",
            urls = listOf("https://example.com/x"),
            output = RecognitionOutput.Action(action),
        )

        val filled = action.fillRecognition(item)
        val url = filled.params["url"]!!.jsonPrimitive.content
        assertEquals("https://example.com/code?c=123456&h=example.com", url)
    }

    @Test
    fun `encoded placeholder is url encoded`() {
        val action = StoredAction.of("intent.shareText", "text" to "%s")
        val item = RecognizedItem(
            ruleId = "r",
            ruleName = "r",
            value = "a b&c",
            urls = emptyList(),
            output = RecognitionOutput.Action(action),
        )

        assertEquals("a+b%26c", action.fillRecognition(item).params["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `builtin output kinds survive projection`() {
        val recognizer = compiled(
            textRule(
                id = "copy",
                pattern = "(\\d+)",
                output = RecognitionOutput.Builtin(RecognitionBuiltin.Copy),
            ),
            linkRule(id = "open", pattern = "example.com"),
        )
        val items = recognizer.match(
            RecognitionInput(RecognitionSource.Clipboard, "42 https://example.com/x"),
        )

        assertEquals(RecognitionBuiltin.Copy, items.first { it.ruleId == "copy" }.builtin)
        assertEquals(RecognitionBuiltin.OpenLink, items.first { it.ruleId == "open" }.builtin)
    }

    @Test
    fun `hasLinkRulesFor tells whether link extraction is worth doing`() {
        val onlyText = compiled(textRule(pattern = "(\\d+)"))
        assertFalse(onlyText.hasLinkRulesFor(RecognitionSource.Clipboard))

        val sceneLink = compiled(linkRule(pattern = "", sources = setOf(RecognitionSource.Scene)))
        assertTrue(sceneLink.hasLinkRulesFor(RecognitionSource.Scene))
        assertFalse(sceneLink.hasLinkRulesFor(RecognitionSource.Clipboard))
    }

    // ---- 默认规则 ----

    @Test
    fun `default rules recognize codes and links out of the box`() {
        val recognizer = Recognizer.compile(RecognitionSettings())

        val code = recognizer.match(RecognitionInput(RecognitionSource.Clipboard, "验证码 654321"))
        assertEquals(RecognitionDefaults.CodeRuleIdZh, code.single().ruleId)

        val link = recognizer.match(
            RecognitionInput(RecognitionSource.Clipboard, "https://example.com/a"),
        )
        assertEquals(RecognitionDefaults.LinkRuleId, link.single().ruleId)
    }

    @Test
    fun `default sources are narrowed per match kind`() {
        // 文本规则不含外部 Intent（那里只有链接）；链接规则不含通知（点了走系统自己那套）。
        assertFalse(RecognitionSource.ExternalIntent in RecognitionSource.TextDefault)
        assertFalse(RecognitionSource.Notification in RecognitionSource.LinkDefault)
        assertTrue(RecognitionSource.Clipboard in RecognitionSource.TextDefault)
        assertTrue(RecognitionSource.Clipboard in RecognitionSource.LinkDefault)

        val recognizer = Recognizer.compile(RecognitionSettings())
        assertTrue(
            recognizer.match(RecognitionInput(RecognitionSource.ExternalIntent, "验证码 123456"))
                .isEmpty(),
        )
    }

    @Test
    fun `json round trip of rules keeps match sources and output`() {
        val rule = RecognitionRule(
            id = "r1",
            name = "取件码",
            sources = setOf(RecognitionSource.Clipboard, RecognitionSource.Notification),
            match = RecognitionMatch.Link(
                matchType = LinkMatchType.DOMAIN,
                pattern = "b23.tv",
                missAction = LinkMissMode.BROWSER,
            ),
            output = RecognitionOutput.Action(StoredAction.of("app.launch")),
        )
        val json = JsonSerializer.encodeToString(listOf(rule))
        val decoded = JsonSerializer.decodeFromString<List<RecognitionRule>>(json)

        assertEquals(listOf(rule), decoded)
    }
}
