package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.LinkDispatchRule
import hunoia.luno.config.model.LinkMatchType
import hunoia.luno.runtime.floatball.inbox.SuggestionPrimaryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情景识别补的真实链接（Shizuku 读 XWeb 日志）与既有分发规则的关系。
 *
 * 口径：补来的链接只是一条链接候选，出不出建议仍由「链接分发规则」说了算。
 */
class SuggestionFactorySceneLinkTest {

    private val noTextRules = SuggestionRuleEngine.compile(emptyList())

    private fun rule(vararg patterns: String) = listOf(
        LinkDispatchRule(
            name = "小红书",
            matchType = LinkMatchType.DOMAIN,
            pattern = patterns.first(),
            targetPackage = "com.xingin.xhs",
        ),
    )

    private val noteUrl = "https://www.xiaohongshu.com/discovery/item/6ac872c4?xsec_token=abc#pushState"

    private fun scene(
        text: String,
        sceneUrl: String?,
        linkRules: List<LinkDispatchRule> = rule("xiaohongshu.com"),
        enabled: Boolean = true,
    ) = SuggestionFactory.fromScene(
        text = text,
        screenKey = "key1",
        rules = noTextRules,
        linkRules = linkRules,
        linkDispatchEnabled = enabled,
        nowMs = 1_000,
        sceneUrl = sceneUrl,
    )

    @Test
    fun `refined url becomes a link suggestion when a dispatch rule matches`() {
        val items = scene(text = "小红书笔记正文", sceneUrl = noteUrl)

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals(SuggestionFactory.LinkLabel, item.title)
        assertEquals(SuggestionPrimaryAction.OPEN, item.action)
        assertEquals(listOf(noteUrl), item.urls)
        assertEquals("key1", item.sceneKey)
    }

    @Test
    fun `refined url without a matching rule yields nothing`() {
        assertTrue(scene(text = "小红书笔记正文", sceneUrl = noteUrl, linkRules = rule("bilibili.com")).isEmpty())
    }

    @Test
    fun `dispatch switch off keeps the url out`() {
        assertTrue(scene(text = "小红书笔记正文", sceneUrl = noteUrl, enabled = false).isEmpty())
    }

    @Test
    fun `url already present in page text is not suggested twice`() {
        val items = scene(text = "分享自 $noteUrl", sceneUrl = noteUrl)

        assertEquals(1, items.size)
        assertEquals(listOf(noteUrl), items.first().urls)
    }

    @Test
    fun `scene url alone still produces a suggestion on a blank screen text`() {
        val items = scene(text = "", sceneUrl = noteUrl)

        assertEquals(1, items.size)
    }
}
