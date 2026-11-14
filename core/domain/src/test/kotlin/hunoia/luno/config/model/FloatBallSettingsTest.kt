package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 悬浮球设置迁移：三球落点、容量拆分，以及旧剪贴板球坐标的推导。 */
class FloatBallSettingsTest {

    @Test
    fun `defaults place notification and suggestion balls on the right edge`() {
        val settings = FloatBallSettings()

        assertEquals(FloatBallDefaults.NotificationBallY, settings.notificationBall(landscape = false).y)
        assertEquals(FloatBallDefaults.NotificationBallY, settings.notificationBall(landscape = true).y)
        assertEquals(FloatBallDefaults.SuggestionBallY, settings.suggestionBall(landscape = false).y)
        assertEquals(FloatBallDefaults.SuggestionBallY, settings.suggestionBall(landscape = true).y)
        assertNull(settings.inbox.notificationCapacity)
        assertNull(settings.inbox.suggestionCapacity)
    }

    @Test
    fun `legacy clipboard ball position seeds the suggestion ball`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "clipboardBall": { "enabled": true, "x": 0.0, "y": 0.61, "respondToImages": false }
            }
            """.trimIndent(),
        )

        val position = decoded.suggestionBall(landscape = false)
        assertEquals(0.0f, position.x)
        assertEquals(0.61f, position.y)
    }

    @Test
    fun `explicit suggestion ball position wins over the legacy value`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "clipboardBall": { "x": 0.0, "y": 0.61 },
              "suggestionBallPosition": {
                "portrait": { "x": 1.0, "y": 0.72 }
              }
            }
            """.trimIndent(),
        )

        assertEquals(0.72f, decoded.suggestionBall(landscape = false).y)
        assertEquals(1.0f, decoded.suggestionBall(landscape = false).x)
        // 横屏没写过：仍按旧值推导。
        assertEquals(0.61f, decoded.suggestionBall(landscape = true).y)
    }

    @Test
    fun `settings round trip keeps ball positions and capacities`() {
        val settings = FloatBallSettings(
            notificationBallPosition = CapsuleBallPosition(
                portrait = FloatBallPosition(x = 0.9f, y = 0.2f),
                landscape = FloatBallPosition(x = 0.8f, y = 0.4f),
            ),
            suggestionBallPosition = CapsuleBallPosition(
                portrait = FloatBallPosition(x = 0.9f, y = 0.7f),
            ),
            inbox = BallInboxSettings(
                notificationCapacity = 6,
                suggestionCapacity = 8,
            ),
            // 主球与两颗内容球的图标各自独立：序列化往返不能串。
            customIconPath = "/data/main.gif",
            notificationCapsule = NotificationCapsuleSettings(
                customIcon = BallCustomIcon(path = "/data/notification.png", scale = 1.2f),
                idleDimDelayMs = 2_500L,
            ),
            clipboardBall = ClipboardBallSettings(
                customIcon = BallCustomIcon(
                    path = "/data/suggestion.webp",
                    scale = 1.4f,
                    offsetX = 0.1f,
                    offsetY = -0.2f,
                    alpha = 0.8f,
                ),
                idleDimDelayMs = 4_000L,
            ),
        )

        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            JsonSerializer.encodeToString(settings),
        )

        assertEquals(settings, decoded)
        // 内容球图标互不跟随：各读各的 path。
        assertEquals("/data/main.gif", decoded.customIconPath)
        assertEquals("/data/notification.png", decoded.notificationCapsule.customIcon.path)
        assertEquals("/data/suggestion.webp", decoded.clipboardBall.customIcon.path)
        // 静置变暗各自独立：两颗内容球不跟随主球。
        assertEquals(2_500L, decoded.notificationCapsule.idleDimDelayMs)
        assertEquals(4_000L, decoded.clipboardBall.idleDimDelayMs)
    }

    @Test
    fun `legacy settings without rules fall back to the default code rules`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "clipboardBall": { "enabled": true }
            }
            """.trimIndent(),
        )

        assertEquals(
            SuggestionRuleDefaults.rules.map { it.id },
            decoded.clipboardBall.rules.map { it.id },
        )
    }

    @Test
    fun `suggestion rules round trip and support upsert remove`() {
        val rule = SuggestionRule(
            id = "custom",
            name = "取件码",
            pattern = "取件码\\s*(\\S+)",
            action = SuggestionRuleAction.SHARE,
            enabled = false,
        )
        val settings = FloatBallSettings(
            clipboardBall = ClipboardBallSettings(rules = listOf(rule)),
        )

        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            JsonSerializer.encodeToString(settings),
        )

        assertEquals(listOf(rule), decoded.clipboardBall.rules)
        val updated = decoded.clipboardBall
            .upsertRule(rule.copy(name = "已改名"))
            .upsertRule(SuggestionRule(id = "new", pattern = "x"))
        assertEquals(listOf("custom", "new"), updated.rules.map { it.id })
        assertEquals("已改名", updated.rules.first().name)
        assertEquals(listOf("new"), updated.removeRule("custom").rules.map { it.id })
    }
}
