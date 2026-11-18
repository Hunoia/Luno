package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 常用引擎预设的门禁：表是唯一事实源（与识别器的 RecognitionDefaults 同口径），
 * 判重按 urlTemplate——同一引擎换了名字 / 标识仍算已添加。
 */
class SearchEnginePresetsTest {

    @Test
    fun `every preset has a unique https template name icon and trigger`() {
        val templates = SearchEnginePresets.all.map { it.urlTemplate.trim() }
        assertEquals(templates.size, templates.toSet().size)
        assertTrue(templates.all { it.startsWith("https://") && "%s" in it })
        assertTrue(SearchEnginePresets.all.all { it.name.isNotBlank() && it.iconText.isNotBlank() })
        val triggers = SearchEnginePresets.all.map { it.trigger.lowercase() }
        assertEquals(triggers.size, triggers.toSet().size)
    }

    @Test
    fun `addable filters by template and keeps table order`() {
        val existing = listOf(
            SearchEngine(name = "我的必应", urlTemplate = "https://www.bing.com/search?q=%s"),
        )
        val addable = SearchEnginePresets.addable(existing)
        assertFalse(addable.any { it.name == "必应" })
        assertTrue(addable.first().name == SearchEnginePresets.all.first { it.name != "必应" }.name)
        assertEquals(
            SearchEnginePresets.all.size - 1,
            addable.size,
        )
    }

    @Test
    fun `added preset round trips through trigger normalization`() {
        val preset = SearchEnginePresets.all.first()
        val settings = QuickSearchSettings(customEngines = listOf(preset))
        val normalized = settings.withUniqueEngineTriggers()
        assertEquals(1, normalized.customEngines.size)
        // 内容派生 id 稳定：两次解码同一引擎得到同一个 id。
        assertEquals(
            normalized.customEngines.single().id,
            normalized.copy().withUniqueEngineTriggers().customEngines.single().id,
        )
    }
}
