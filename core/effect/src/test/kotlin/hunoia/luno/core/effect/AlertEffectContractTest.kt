package hunoia.luno.core.effect

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 契约的序列化口径：请求要能跨进程序列化，且对未知字段兼容（调用方 / 服务版本可能错开）。
 */
class AlertEffectContractTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `request round trips with layers overrides and content`() {
        val request = AlertEffectRequest(
            id = "req-1",
            layers = listOf(
                AlertEffectLayer(
                    presetId = "builtin_rain",
                    host = AlertEffectHost.Stage,
                    overrides = AlertOverrides(
                        color = AlertColorOverride(colors = listOf(0xFFE53935.toInt())),
                        intensity = 0.7f,
                        speed = 1.2f,
                    ),
                ),
                AlertEffectLayer(
                    presetId = "builtin_pulse",
                    host = AlertEffectHost.Capsule,
                ),
            ),
            concurrency = AlertConcurrency.Replace,
            anchor = AlertRect(10f, 20f, 110f, 220f),
            content = AlertContent(title = "标题", body = "正文", iconPackage = "com.example"),
            speed = 1.5f,
            seed = 42,
        )

        val decoded = json.decodeFromString<AlertEffectRequest>(json.encodeToString(request))

        assertEquals(request, decoded)
        assertEquals(AlertEffectMaxLayers, 5)
        assertEquals(AlertEffectHost.Capsule, decoded.layers[1].host)
        assertEquals(0xFFE53935.toInt(), decoded.layers[0].overrides.color?.colors?.single())
    }

    @Test
    fun `defaults keep reject concurrency and no anchor`() {
        val decoded = json.decodeFromString<AlertEffectRequest>(
            """{"id":"r","layers":[{"presetId":"p","host":"Stage"}]}""",
        )

        assertEquals(AlertConcurrency.Reject, decoded.concurrency)
        assertNull(decoded.anchor)
        assertNull(decoded.content)
        assertEquals(1f, decoded.speed, 0.0001f)
        assertNull(decoded.seed)
        assertEquals(AlertOverrides(), decoded.layers.single().overrides)
    }

    @Test
    fun `unknown fields are tolerated for forward compatibility`() {
        val decoded = json.decodeFromString<AlertEffectRequest>(
            """
            {
              "id": "r",
              "layers": [{ "presetId": "p", "host": "Stage", "futureField": true }],
              "futureTopLevel": { "x": 1 }
            }
            """,
        )

        assertTrue(decoded.layers.isNotEmpty())
        assertEquals("p", decoded.layers.single().presetId)
    }

    @Test
    fun `color override keeps empty colors when only palette id is set`() {
        val decoded = json.decodeFromString<AlertColorOverride>(
            """{"paletteId":"builtin_dracula"}""",
        )

        assertEquals("builtin_dracula", decoded.paletteId)
        assertTrue(decoded.colors.isEmpty())
    }
}