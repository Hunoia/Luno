package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 历史重复标识的归一化：保留最后一条（最新），其余清空直达标识。 */
class QuickSearchSettingsTriggerTest {

    private fun engine(id: String, trigger: String) = SearchEngine(
        id = id,
        name = id,
        urlTemplate = "https://example.com/?q=%s",
        trigger = trigger,
    )

    @Test
    fun `duplicate custom triggers keep the last engine`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(
                engine("old", "b"),
                engine("new", "B"),
            ),
        )
        val normalized = settings.withUniqueEngineTriggers()
        assertEquals("", normalized.customEngines[0].trigger)
        assertEquals("B", normalized.customEngines[1].trigger)
    }

    @Test
    fun `builtin trigger is reserved for builtin engine`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(engine("custom", "bb"), engine("other", "x")),
        )
        val normalized = settings.withUniqueEngineTriggers()
        assertEquals("", normalized.customEngines[0].trigger)
        assertEquals("x", normalized.customEngines[1].trigger)
    }

    @Test
    fun `whitespace-only trigger is normalized to empty`() {
        val settings = QuickSearchSettings(customEngines = listOf(engine("blank", "  ")))
        val normalized = settings.withUniqueEngineTriggers()
        assertEquals("", normalized.customEngines[0].trigger)
    }

    @Test
    fun `clean settings are returned unchanged`() {
        val settings = QuickSearchSettings(
            customEngines = listOf(engine("a", "aa"), engine("b", "bb2")),
        )
        assertEquals(settings, settings.withUniqueEngineTriggers())
    }
}
