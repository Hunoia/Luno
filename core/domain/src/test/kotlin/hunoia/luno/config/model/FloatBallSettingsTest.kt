package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 悬浮球设置：三球落点、容量拆分，以及旧剪贴板球坐标的推导。 */
class FloatBallSettingsTest {

    @Test
    fun `defaults place notification and result balls on the right edge`() {
        val settings = FloatBallSettings()

        assertEquals(FloatBallDefaults.NotificationBallY, settings.notificationBall(landscape = false).y)
        assertEquals(FloatBallDefaults.NotificationBallY, settings.notificationBall(landscape = true).y)
        assertEquals(FloatBallDefaults.ResultBallY, settings.resultBall(landscape = false).y)
        assertEquals(FloatBallDefaults.ResultBallY, settings.resultBall(landscape = true).y)
        assertNull(settings.inbox.notificationCapacity)
        assertNull(settings.inbox.resultCapacity)
    }

    @Test
    fun `legacy clipboard ball position seeds the result ball`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "resultCapsule": { "x": 0.0, "y": 0.61, "respondToImages": false }
            }
            """.trimIndent(),
        )

        val position = decoded.resultBall(landscape = false)
        assertEquals(0.0f, position.x)
        assertEquals(0.61f, position.y)
    }

    @Test
    fun `explicit result ball position wins over the legacy value`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "resultCapsule": { "x": 0.0, "y": 0.61 },
              "resultBallPosition": {
                "portrait": { "x": 1.0, "y": 0.72 }
              }
            }
            """.trimIndent(),
        )

        assertEquals(0.72f, decoded.resultBall(landscape = false).y)
        assertEquals(1.0f, decoded.resultBall(landscape = false).x)
        // 横屏没写过：仍按旧值推导。
        assertEquals(0.61f, decoded.resultBall(landscape = true).y)
    }

    @Test
    fun `settings round trip keeps ball positions and capacities`() {
        val settings = FloatBallSettings(
            notificationBallPosition = CapsuleBallPosition(
                portrait = FloatBallPosition(x = 0.9f, y = 0.2f),
                landscape = FloatBallPosition(x = 0.8f, y = 0.4f),
            ),
            resultBallPosition = CapsuleBallPosition(
                portrait = FloatBallPosition(x = 0.9f, y = 0.7f),
            ),
            inbox = BallInboxSettings(
                notificationCapacity = 6,
                resultCapacity = 8,
            ),
            // 主球与两颗内容球的图标各自独立：序列化往返不能串。
            customIconPath = "/data/main.gif",
            notificationCapsule = NotificationCapsuleSettings(
                customIcon = BallCustomIcon(path = "/data/notification.png", scale = 1.2f),
                idleDimDelayMs = 2_500L,
            ),
            resultCapsule = ResultCapsuleSettings(
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
        assertEquals("/data/suggestion.webp", decoded.resultCapsule.customIcon.path)
        // 静置变暗各自独立：两颗内容球不跟随主球。
        assertEquals(2_500L, decoded.notificationCapsule.idleDimDelayMs)
        assertEquals(4_000L, decoded.resultCapsule.idleDimDelayMs)
    }
}
