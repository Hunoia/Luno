package hunoia.luno.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LogLevelTest {

    @Test
    fun `默认级别是 INFO`() {
        assertEquals(LogLevel.INFO, LogLevel.DEFAULT)
    }

    @Test
    fun `未知或空值回落到默认级别`() {
        assertEquals(LogLevel.DEFAULT, LogLevel.fromName(null))
        assertEquals(LogLevel.DEFAULT, LogLevel.fromName(""))
        assertEquals(LogLevel.DEFAULT, LogLevel.fromName("NOT_A_LEVEL"))
    }

    @Test
    fun `级别名可往返解析并忽略空白`() {
        LogLevel.entries.forEach { level ->
            assertEquals(level, LogLevel.fromName(" ${level.name}\n"))
        }
    }
}
