package hunoia.luno.runtime.recognition

import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionDisplay
import hunoia.luno.config.model.RecognitionOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结果分流：胶囊档进队列、直执行档落地一次、同一段值在去重窗内不重复执行。
 *
 * 这条不变量是「直接执行」能安全存在的前提：剪贴板轮询（2s）与情景重扫会把同一段文本
 * 反复喂进来，没有去重表就会反复跑动作。
 */
class RecognitionDeliveryTest {

    private val window = 10_000L

    private fun delivery() = RecognitionDelivery(windowMs = { window })

    private fun item(
        ruleId: String = "rule-code",
        value: String = "123456",
        display: RecognitionDisplay = RecognitionDisplay.Direct,
        output: RecognitionOutput = RecognitionOutput.Builtin(RecognitionBuiltin.CopyAndClear),
    ) = RecognizedItem(
        ruleId = ruleId,
        ruleName = "验证码",
        value = value,
        urls = emptyList(),
        output = output,
        display = display,
    )

    @Test
    fun `capsule items always route to the capsule`() {
        val routed = delivery().route(
            items = listOf(item(display = RecognitionDisplay.Capsule)),
            sourceKey = "clip",
            nowMs = 1_000,
        )

        assertEquals(1, routed.capsule.size)
        assertTrue(routed.direct.isEmpty())
        assertTrue(routed.matchedAnything)
    }

    @Test
    fun `capsule items are never deduplicated`() {
        val delivery = delivery()
        val capsule = item(display = RecognitionDisplay.Capsule)

        val first = delivery.route(listOf(capsule), "clip", 1_000)
        val second = delivery.route(listOf(capsule), "clip", 1_001)

        assertEquals(1, first.capsule.size)
        assertEquals(1, second.capsule.size)
    }

    @Test
    fun `direct item executes once and is suppressed inside the window`() {
        val delivery = delivery()
        val direct = item()

        val first = delivery.route(listOf(direct), "clip", 1_000)
        val second = delivery.route(listOf(direct), "clip", 2_000)

        assertEquals(listOf(direct), first.direct)
        assertTrue(second.direct.isEmpty())
        assertEquals(listOf(direct), second.suppressed)
        // 被去重挡住的仍算「命中过规则」：通知不该再落到通知胶囊。
        assertTrue(second.matchedAnything)
    }

    @Test
    fun `same value after the window executes again`() {
        val delivery = delivery()
        val direct = item()

        delivery.route(listOf(direct), "clip", 1_000)
        val later = delivery.route(listOf(direct), "clip", 1_000 + window)

        assertEquals(listOf(direct), later.direct)
    }

    @Test
    fun `different value in the same rule executes again`() {
        val delivery = delivery()

        delivery.route(listOf(item(value = "111111")), "clip", 1_000)
        val second = delivery.route(listOf(item(value = "222222")), "clip", 1_500)

        assertEquals(1, second.direct.size)
    }

    @Test
    fun `different source key executes again`() {
        val delivery = delivery()
        val direct = item()

        delivery.route(listOf(direct), "clip", 1_000)
        val otherSource = delivery.route(listOf(direct), "scene:home", 1_500)

        assertEquals(listOf(direct), otherSource.direct)
    }

    @Test
    fun `mixed batch keeps order and splits by display`() {
        val capsule = item(ruleId = "rule-link", display = RecognitionDisplay.Capsule)
        val direct = item(ruleId = "rule-code")

        val routed = delivery().route(listOf(capsule, direct), "clip", 1_000)

        assertEquals(listOf(capsule), routed.capsule)
        assertEquals(listOf(direct), routed.direct)
    }

    @Test
    fun `empty input routes nothing`() {
        val routed = delivery().route(emptyList(), "clip", 1_000)

        assertFalse(routed.matchedAnything)
        assertTrue(routed.routes.isEmpty())
    }

    @Test
    fun `dedup table is bounded and evicts the oldest entry`() {
        val delivery = RecognitionDelivery(windowMs = { Long.MAX_VALUE }, maxEntries = 2)

        delivery.route(listOf(item(ruleId = "a")), "clip", 1_000)
        delivery.route(listOf(item(ruleId = "b")), "clip", 1_001)
        delivery.route(listOf(item(ruleId = "c")), "clip", 1_002)
        // a 已被挤出表：同一段值可以再执行。
        val again = delivery.route(listOf(item(ruleId = "a")), "clip", 1_003)

        assertEquals(1, again.direct.size)
    }
}
