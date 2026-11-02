package hunoia.luno.gesture

import hunoia.luno.config.model.DirectionActions
import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方向动作的存储形态：写盘用槽位序号，读取兼容旧版四向枚举名。
 *
 * 旧配置的枚举名顺序固定为 [Up, Left, Down, Right] → 槽 0..3，与默认四扇区布局一致。
 */
class DirectionActionsSerializationTest {

    @Test
    fun `legacy enum keys migrate to slots`() {
        val legacy = """
            {"actions":{
              "Up":{"typeId":"a"},
              "Left":{"typeId":"b"},
              "Down":{"typeId":"c"},
              "Right":{"typeId":"d"}
            }}
        """.trimIndent()
        val decoded = JsonSerializer.decodeFromString<DirectionActions>(legacy)
        assertEquals(setOf(0, 1, 2, 3), decoded.actions.keys)
        assertEquals("a", decoded.actionBy(0)?.typeId)
        assertEquals("b", decoded.actionBy(1)?.typeId)
        assertEquals("c", decoded.actionBy(2)?.typeId)
        assertEquals("d", decoded.actionBy(3)?.typeId)
    }

    @Test
    fun `numeric keys round trip`() {
        val original = DirectionActions(mapOf(2 to hunoia.luno.core.domain.StoredAction.of("x")))
        val json = JsonSerializer.encodeToString(original)
        assertTrue(json.contains("\"2\""))
        val decoded = JsonSerializer.decodeFromString<DirectionActions>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `unknown keys are dropped`() {
        val decoded = JsonSerializer.decodeFromString<DirectionActions>(
            """{"actions":{"9":{"typeId":"x"},"Sideways":{"typeId":"y"}}}""",
        )
        assertTrue(decoded.actions.isEmpty())
    }
}
