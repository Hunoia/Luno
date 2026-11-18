package hunoia.luno.runtime.floatball.inbox

import hunoia.luno.config.model.RecognitionDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情景识别在收件箱里查询「这一屏的项」的纯谓词。
 *
 * 回归重点：内置链接项**就是**链接规则命中的项（[ResultItem.ruleId] 恒非空，见
 * [ResultFactory.toResultItem]），判据必须是「ruleId 等于链接规则」而不是「ruleId 为空」。
 * 旧实现用空判定，导致链接补充的原位替换快路径永远走不到。
 */
class SceneLinkPredicateTest {

    private val scanKey = "screen:com.example.app/12345"

    private fun resultItem(
        ruleId: String,
        sceneKey: String? = scanKey,
        urls: List<String> = emptyList(),
    ) = ResultItem(
        sourceKey = "scene:$sceneKey",
        title = "标题-$ruleId",
        text = "正文-$ruleId",
        receivedAtElapsedMs = 0L,
        ruleId = ruleId,
        urls = urls,
        sceneKey = sceneKey,
    )

    private val linkItem = resultItem(
        ruleId = RecognitionDefaults.LinkRuleId,
        urls = listOf("https://example.com/a"),
    )

    private val codeItem = resultItem(
        ruleId = RecognitionDefaults.CodeRuleIdZh,
        urls = listOf("https://example.com/short"),
    )

    @Test
    fun builtInLinkIndex_findsLinkRuleItem() {
        val items = listOf(codeItem, linkItem)
        assertEquals(1, builtInSceneLinkIndex(items, scanKey))
        assertTrue(hasBuiltInSceneLink(items, scanKey))
    }

    @Test
    fun builtInLinkIndex_textRuleOnly_hasNoLinkItem() {
        assertFalse(hasBuiltInSceneLink(listOf(codeItem), scanKey))
    }

    @Test
    fun builtInLinkIndex_ignoresOtherScreen() {
        val otherScreen = resultItem(
            ruleId = RecognitionDefaults.LinkRuleId,
            sceneKey = "screen:com.other/999",
        )
        assertFalse(hasBuiltInSceneLink(listOf(otherScreen), scanKey))
    }

    @Test
    fun usedRuleUrls_excludesLinkItemIncludesTextRule() {
        val urls = usedSceneRuleUrls(listOf(linkItem, codeItem), scanKey)
        assertEquals(setOf("https://example.com/short"), urls)
    }

    @Test
    fun hasSceneItems_matchesScreenKeyOnly() {
        assertTrue(hasSceneItems(listOf(linkItem, codeItem), scanKey))
        assertFalse(hasSceneItems(emptyList(), scanKey))
    }
}