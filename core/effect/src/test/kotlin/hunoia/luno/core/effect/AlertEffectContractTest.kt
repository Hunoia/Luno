package hunoia.luno.core.effect

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 播放请求契约：JSON 往返、默认并发策略、可选覆盖与内容。 */
class AlertEffectContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `request round trips with preset reference`() {
        val request = AlertEffectRequest(
            id = "req-1",
            presetId = "builtin_edge_ripple",
            concurrency = AlertConcurrency.Replace,
            anchor = AlertRect(0f, 0f, 1080f, 2400f),
            content = AlertContent(title = "标题", body = "正文", iconPackage = "com.example"),
            speed = 1.5f,
            intensity = 0.8f,
            seed = 42,
        )
        val decoded = json.decodeFromString<AlertEffectRequest>(json.encodeToString(request))
        assertEquals(request, decoded)
        assertEquals("builtin_edge_ripple", decoded.presetId)
        assertEquals(0.8f, decoded.intensity)
    }

    @Test
    fun `defaults are reject concurrency and preset speed`() {
        val request = AlertEffectRequest(id = "req-2", presetId = "builtin_capsule_pulse")
        assertEquals(AlertConcurrency.Reject, request.concurrency)
        assertEquals(1f, request.speed)
        assertNull(request.intensity)
        assertNull(request.anchor)
        assertNull(request.content)
        assertNull(request.seed)
    }

    @Test
    fun `json without optional fields decodes`() {
        val decoded = json.decodeFromString<AlertEffectRequest>(
            """{"id":"req-3","presetId":"builtin_screen_flash"}""",
        )
        assertEquals(AlertConcurrency.Reject, decoded.concurrency)
        assertEquals(1f, decoded.speed)
        assertNull(decoded.intensity)
    }

    @Test
    fun `content is optional and only carries short text plus icon package`() {
        val content = AlertContent(title = "t", iconPackage = "com.example")
        val decoded = json.decodeFromString<AlertContent>(json.encodeToString(content))
        assertEquals("t", decoded.title)
        assertNull(decoded.body)
        assertEquals("com.example", decoded.iconPackage)
        assertTrue(AlertConcurrency.entries.contains(AlertConcurrency.Queue))
    }
}