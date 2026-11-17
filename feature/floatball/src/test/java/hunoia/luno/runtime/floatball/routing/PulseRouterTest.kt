package hunoia.luno.runtime.floatball.routing

import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.SuggestionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知去路判定：规则命中只进建议胶囊（不双弹）、普通通知进通知胶囊、都关了就什么都不弹。
 */
class PulseRouterTest {

    private val now = 12_345L

    private fun pulse(
        title: String = "标题",
        text: String = "正文",
        key: String? = "pkg:0:1",
    ) = NotificationPulse(
        packageName = "com.test",
        title = title,
        text = text,
        key = key,
    )

    private fun suggestion(key: String = "scene:abc") = SuggestionItem(
        sourceKey = key,
        title = "验证码",
        text = "123456",
        receivedAtElapsedMs = now,
        ruleId = "rule-code",
    )

    @Test
    fun ruleHitGoesToSuggestionCapsuleOnly() {
        val items = listOf(suggestion())
        val route = PulseRouter.route(pulse(), items, notificationCapsuleEnabled = true, nowMs = now)
        assertTrue(route is PulseRoute.Suggestion)
        assertEquals(items, (route as PulseRoute.Suggestion).items)
    }

    @Test
    fun ruleHitWinsEvenWhenNotificationCapsuleIsOff() {
        val route = PulseRouter.route(
            pulse(),
            listOf(suggestion()),
            notificationCapsuleEnabled = false,
            nowMs = now,
        )
        assertTrue(route is PulseRoute.Suggestion)
    }

    @Test
    fun noRuleHitGoesToNotificationCapsule() {
        val route = PulseRouter.route(pulse(), emptyList(), notificationCapsuleEnabled = true, nowMs = now)
        assertTrue(route is PulseRoute.Notification)
        val item = (route as PulseRoute.Notification).item
        assertEquals("pkg:0:1", item.notificationKey)
        assertEquals("com.test", item.packageName)
        assertEquals("标题", item.title)
        assertEquals("正文", item.text)
        assertEquals(now, item.receivedAtElapsedMs)
    }

    @Test
    fun nothingPopsWhenNotificationCapsuleIsOff() {
        val route = PulseRouter.route(pulse(), emptyList(), notificationCapsuleEnabled = false, nowMs = now)
        assertTrue(route is PulseRoute.Drop)
    }

    @Test
    fun notificationKeyFallsBackToPackageAndTitleWhenSystemKeyMissing() {
        val route = PulseRouter.route(
            pulse(title = "我的标题", key = null),
            emptyList(),
            notificationCapsuleEnabled = true,
            nowMs = now,
        )
        val item = (route as PulseRoute.Notification).item
        assertEquals("com.test:我的标题", item.notificationKey)
    }

    @Test
    fun notificationKeyUsesSystemKeyWhenPresent() {
        assertEquals("pkg:0:1", PulseRouter.notificationKey(pulse()))
        assertEquals("com.test:标题", PulseRouter.notificationKey(pulse(key = null)))
    }
}