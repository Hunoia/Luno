package hunoia.luno.runtime.automation

import hunoia.luno.core.domain.ScreenEventType
import hunoia.luno.core.domain.blankRuntimeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EventSourceRegistryTest {

    @Test
    fun `pulse keys are collected from registered sources`() {
        val context = blankRuntimeSnapshot().copy(
            appChangedTo = "com.example",
            screenEvent = ScreenEventType.SCREEN_ON,
        )
        assertEquals("com.example", context.appChangedTo)
        assertEquals("builtin app", setOf("app"), EventSourceRegistry.pulseKeysFrom("app", context))
        assertTrue("ids=${EventSourceRegistry.ids}", EventSourceRegistry.ids.contains("app"))
        assertEquals(
            setOf(AutomationEventKey.APP, AutomationEventKey.SCREEN),
            EventSourceRegistry.pulseKeysIn(context),
        )
    }

    @Test
    fun `no pulse yields empty set`() {
        assertTrue(EventSourceRegistry.pulseKeysIn(blankRuntimeSnapshot()).isEmpty())
    }

    @Test
    fun `duplicate source id fails fast`() {
        val id = EventSourceRegistry.ids.first()
        try {
            EventSourceRegistry.register(id) { emptySet() }
            fail("重复事件源 id 应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }
}
