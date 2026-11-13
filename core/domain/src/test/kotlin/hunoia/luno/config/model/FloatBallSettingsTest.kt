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
        )

        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            JsonSerializer.encodeToString(settings),
        )

        assertEquals(settings, decoded)
    }
}
