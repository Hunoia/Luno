package hunoia.luno.runtime.floatball.routing

import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.ResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知去路判定：命中规则就不双弹、未命中进通知胶囊、通知胶囊也关了就什么都不弹。
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

    private fun resultItem(key: String = "scene:abc") = ResultItem(
        sourceKey = key,
        title = "验证码",
        text = "123456",
        receivedAtElapsedMs = now,
        ruleId = "rule-code",
    )

    @Test
    fun ruleHitGoesToResultCapsuleOnly() {
        val items = listOf(resultItem())
        val route = PulseRouter.route(
            pulse = pulse(),
            resultItems = items,
            matchedRules = true,
            notificationCapsuleEnabled = true,
            nowMs = now,
        )
        assertTrue(route is PulseRoute.Result)
        assertEquals(items, (route as PulseRoute.Result).items)
    }

    @Test
    fun ruleHitWinsEvenWhenNotificationCapsuleIsOff() {
        val route = PulseRouter.route(
            pulse = pulse(),
            resultItems = listOf(resultItem()),
            matchedRules = true,
            notificationCapsuleEnabled = false,
            nowMs = now,
        )
        assertTrue(route is PulseRoute.Result)
    }

    /** 直执行档不产出胶囊条目，但它已经「处理过」这条通知：仍然不进通知胶囊。 */
    @Test
    fun directRuleStillSuppressesNotificationCapsule() {
        val route = PulseRouter.route(
            pulse = pulse(),
            resultItems = emptyList(),
            matchedRules = true,
            notificationCapsuleEnabled = true,
            nowMs = now,
        )
        assertTrue(route is PulseRoute.Result)
        assertEquals(emptyList<ResultItem>(), (route as PulseRoute.Result).items)
    }

    @Test
    fun noRuleHitGoesToNotificationCapsule() {
        val route = PulseRouter.route(
            pulse = pulse(),
            resultItems = emptyList(),
            matchedRules = false,
            notificationCapsuleEnabled = true,
            nowMs = now,
        )
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
        val route = PulseRouter.route(
            pulse = pulse(),
            resultItems = emptyList(),
            matchedRules = false,
            notificationCapsuleEnabled = false,
            nowMs = now,
        )
        assertTrue(route is PulseRoute.Drop)
    }

    @Test
    fun notificationKeyFallsBackToPackageAndTitleWhenSystemKeyMissing() {
        val route = PulseRouter.route(
            pulse = pulse(title = "我的标题", key = null),
            resultItems = emptyList(),
            matchedRules = false,
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
