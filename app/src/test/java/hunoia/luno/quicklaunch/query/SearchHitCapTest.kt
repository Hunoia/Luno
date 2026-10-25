package hunoia.luno.quicklaunch.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 结果上限的纯函数行为：只留排序前 N，Pin 旁路保留。 */
class SearchHitCapTest {

    private fun hit(url: String, tier: Int, offset: Int = 0, usage: Double = 0.0) =
        RankedHit(QuickSearchResult.Url("https://$url"), tier, 0, offset, usage)

    private fun RankedHit.url(): String = (result as QuickSearchResult.Url).url

    @Test
    fun `cap keeps only top n by shared order`() {
        val hits = listOf(
            hit("low", tier = 0, offset = 3),
            hit("anchor", tier = 2),
            hit("degraded", tier = 1),
        )
        val capped = capHits(hits, 2, emptySet())
        assertEquals(setOf("https://anchor", "https://degraded"), capped.map { it.url() }.toSet())
    }

    @Test
    fun `pinned hit survives cap and does not consume its quota`() {
        val pinned = hit("pinned", tier = 0, offset = 9)
        val hits = listOf(hit("anchor", tier = 2), hit("degraded", tier = 1), pinned)
        val capped = capHits(hits, 1, setOf(pinned.result.stableId()))
        assertTrue("pinned should survive", capped.any { it.result === pinned.result })
        assertEquals(2, capped.size)
    }

    @Test
    fun `empty pinned set and small list returns same list`() {
        val hits = listOf(hit("a", tier = 1), hit("b", tier = 0))
        assertTrue(capHits(hits, 10, emptySet()) === hits)
    }

    @Test
    fun `bestOfFirst skips second field when first is already max`() {
        val max = TieredMatch(tier = 2, span = 1, offset = 0)
        var secondCalled = false
        val result = bestOfFirst(max) {
            secondCalled = true
            TieredMatch(tier = 0, span = 1, offset = 5)
        }
        assertEquals(max, result)
        assertFalse(secondCalled)
    }
}
