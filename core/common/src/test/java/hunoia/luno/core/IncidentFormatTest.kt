package hunoia.luno.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentFormatTest {

    private val base = Incident(
        timeMs = 1_760_000_000_123L,
        kind = IncidentKind.CRASH,
        process = "main",
        thread = "main",
        tag = "Crash",
        message = "java.lang.IllegalStateException: boom\nsecond line",
        stackTrace = """
            java.lang.IllegalStateException: boom
                at com.example.Foo.bar(Foo.kt:12)
                at com.example.Baz.qux(Baz.kt:34)
        """.trimIndent(),
    )

    @Test
    fun `format and parse round trip keeps fields and stack`() {
        val parsed = IncidentFormat.parse(IncidentFormat.format(base))

        assertEquals(1, parsed.size)
        val incident = parsed.single()
        assertEquals(base.timeMs, incident.timeMs)
        assertEquals(base.kind, incident.kind)
        assertEquals(base.process, incident.process)
        assertEquals(base.thread, incident.thread)
        assertEquals(base.tag, incident.tag)
        // 单行字段里的换行会被折叠，避免破坏块结构。
        assertTrue(incident.message.contains("IllegalStateException: boom"))
        assertTrue(incident.message.contains("second line"))
        assertTrue(incident.stackTrace.contains("at com.example.Foo.bar"))
    }

    @Test
    fun `parse skips incomplete block from rotation truncation`() {
        val complete = IncidentFormat.format(base)
        val truncated = complete.substringBeforeLast(IncidentFormat.FOOTER)

        assertEquals(1, IncidentFormat.parse(complete).size)
        assertEquals(0, IncidentFormat.parse(truncated).size)
    }

    @Test
    fun `parse multiple blocks in order`() {
        val second = base.copy(
            timeMs = base.timeMs + 1,
            kind = IncidentKind.ERROR,
            tag = "Other",
        )
        val text = IncidentFormat.format(base) + IncidentFormat.format(second)
        val parsed = IncidentFormat.parse(text)

        assertEquals(2, parsed.size)
        assertEquals("Crash", parsed[0].tag)
        assertEquals("Other", parsed[1].tag)
    }

    @Test
    fun `fingerprint is stable for same tag message and top frame`() {
        val same = base.copy(timeMs = base.timeMs + 10_000)
        val differentFrame = base.copy(
            stackTrace = base.stackTrace.replace("Foo.kt:12", "Foo.kt:99"),
        )
        val differentMessage = base.copy(message = "another error")

        assertEquals(IncidentFormat.fingerprint(base), IncidentFormat.fingerprint(same))
        assertNotEquals(IncidentFormat.fingerprint(base), IncidentFormat.fingerprint(differentFrame))
        assertNotEquals(IncidentFormat.fingerprint(base), IncidentFormat.fingerprint(differentMessage))
    }
}
