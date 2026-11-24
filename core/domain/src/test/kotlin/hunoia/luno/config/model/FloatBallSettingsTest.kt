package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `legacy notification capsule decodes with the default visible rows`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "notificationCapsule": { "enabled": true, "autoHideMs": 5000 }
            }
            """.trimIndent(),
        )

        assertEquals(FloatBallDefaults.CapsuleVisibleRows, decoded.notificationCapsule.visibleRows)
    }

    @Test
    fun `visible rows survive a serialization round trip`() {
        val settings = FloatBallSettings(
            notificationCapsule = NotificationCapsuleSettings(visibleRows = 5),
            resultCapsule = ResultCapsuleSettings(visibleRows = 2),
        )

        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            JsonSerializer.encodeToString(settings),
        )

        assertEquals(5, decoded.notificationCapsule.visibleRows)
        assertEquals(2, decoded.resultCapsule.visibleRows)
    }

    @Test
    fun `result ttl default is the instant-hint value and the legacy key is dropped`() {
        assertEquals(60_000L, FloatBallSettings().inbox.resultTtlMs)

        // 旧名 clipboardTtlMs 的落盘键被 ignoreUnknownKeys 丢弃：改名即迁移到新默认（§11.2）。
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            { "inbox": { "clipboardTtlMs": 300000 } }
            """.trimIndent(),
        )

        assertEquals(FloatBallDefaults.InboxResultTtlMs, decoded.inbox.resultTtlMs)
    }

    @Test
    fun `gesture behaviors normalize to slot-legal defaults`() {
        // 通知行长按不支持操作菜单；两颗球的球长按都不支持菜单；滑动手势不支持清空队列。
        val notification = NotificationCapsuleSettings(
            rowLongPress = CapsuleGestureBehavior.MENU,
            ballLongPress = CapsuleGestureBehavior.MENU,
            rowSwipeAway = CapsuleGestureBehavior.CLEAR_QUEUE,
        )
        assertEquals(CapsuleGestureBehavior.NONE, notification.rowLongPressBehavior)
        assertEquals(CapsuleGestureBehavior.NONE, notification.ballLongPressBehavior)
        assertEquals(CapsuleGestureBehavior.IGNORE_ROW, notification.rowSwipeAwayBehavior)

        // 结果行长按默认保留操作菜单：合法值原样读出。
        val result = ResultCapsuleSettings()
        assertEquals(CapsuleGestureBehavior.MENU, result.rowLongPressBehavior)
        assertEquals(
            CapsuleGestureBehavior.MENU,
            ResultCapsuleSettings(rowLongPress = CapsuleGestureBehavior.MENU).rowLongPressBehavior,
        )
        assertEquals(CapsuleGestureBehavior.NONE, ResultCapsuleSettings().ballLongPressBehavior)
    }

    @Test
    fun `old alert v2 and v3 subtrees are skipped and reset only the alert subtree`() {
        // alert_v4 是唯一挂载键；旧 alert_v2（四类样式）与 alert_v3（五槽）整块被
        // ignoreUnknownKeys 跳过 → 动效回落默认（总开关关、未选预设），素材库不受影响。
        listOf("alert_v2", "alert_v3").forEach { oldKey ->
            val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
                """
                {
                  "notificationCapsule": {
                    "enabled": true,
                    "$oldKey": {
                      "edge": { "enabled": true, "style": "EdgeBlink" },
                      "particle": { "enabled": true, "style": "Snowflake" },
                      "slots": { "edge": { "enabled": true } }
                    }
                  }
                }
                """,
            )
            val alert = decoded.notificationCapsule.alert
            assertFalse(alert.enabled)
            assertNull(alert.presetId)
            assertEquals(100, alert.intensityPercent)
            assertEquals(100, alert.speedPercent)
            assertTrue(decoded.notificationCapsule.alertLibrary.palettes.isEmpty())
            // 胶囊自身的其它字段照常解码。
            assertTrue(decoded.notificationCapsule.enabled)
        }
    }

    @Test
    fun `alert v4 decodes master switch preset and global knobs`() {
        val decoded = JsonSerializer.decodeFromString<FloatBallSettings>(
            """
            {
              "notificationCapsule": {
                "alert_v4": {
                  "enabled": true,
                  "presetId": "builtin_edge_ripple",
                  "intensityPercent": 120,
                  "speedPercent": 80,
                  "playOnEveryNotification": true
                }
              }
            }
            """,
        )
        val alert = decoded.notificationCapsule.alert
        assertTrue(alert.enabled)
        assertEquals("builtin_edge_ripple", alert.presetId)
        assertEquals(120, alert.intensityPercent)
        assertEquals(80, alert.speedPercent)
        assertTrue(alert.playOnEveryNotification)
    }
}
