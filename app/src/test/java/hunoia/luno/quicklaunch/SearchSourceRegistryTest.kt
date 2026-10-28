package hunoia.luno.quicklaunch

import hunoia.luno.quicklaunch.query.SearchSourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SearchSourceRegistryTest {

    @Test
    fun `built-in source keys are unique and registered`() {
        val keys = SearchSourceRegistry.all.map { it.key }
        assertEquals("source key 重复：$keys", keys.size, keys.toSet().size)
        assertTrue("内置来源不应为空", keys.isNotEmpty())
    }

    @Test
    fun `duplicate source key fails fast`() {
        val existing = SearchSourceRegistry.all.first()
        try {
            SearchSourceRegistry.register(existing)
            fail("重复来源应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }
}
