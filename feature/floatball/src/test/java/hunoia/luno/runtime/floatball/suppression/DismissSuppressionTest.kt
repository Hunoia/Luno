package hunoia.luno.runtime.floatball.suppression

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑掉抑制表：时钟、窗口长度与开关都注入，验证窗口语义、开关语义与淘汰顺序。
 */
class DismissSuppressionTest {

    private var now: Long = 0L

    private val suppression = DismissSuppression(
        nowMs = { now },
        windowMs = { 10_000L },
    )

    @Test
    fun suppressedInsideWindow() {
        now = 0L
        suppression.markDismissed("k1")
        now = 9_999L
        assertTrue(suppression.isSuppressed("k1"))
    }

    @Test
    fun notSuppressedAfterWindow() {
        now = 0L
        suppression.markDismissed("k1")
        now = 10_000L
        assertFalse(suppression.isSuppressed("k1"))
    }

    @Test
    fun expiredEntryIsDroppedOnCheck() {
        now = 0L
        suppression.markDismissed("k1")
        now = 20_000L
        assertFalse(suppression.isSuppressed("k1"))
        // 已清掉：再查一次仍是 false，不会因为旧记录残留而误压。
        assertFalse(suppression.isSuppressed("k1"))
    }

    @Test
    fun reDismissRefreshesWindow() {
        now = 0L
        suppression.markDismissed("k1")
        now = 8_000L
        suppression.markDismissed("k1")
        now = 17_000L // 距第一次滑掉已超窗，但距最近一次没有
        assertTrue(suppression.isSuppressed("k1"))
        now = 18_001L
        assertFalse(suppression.isSuppressed("k1"))
    }

    @Test
    fun windowIsReadAtQueryTimeNotAtConstruction() {
        // 通知的抑制时长是用户可调设置，必须在查询时现读，不能构造时缓存。
        var window = 10_000L
        val variable = DismissSuppression(
            nowMs = { now },
            windowMs = { window },
        )
        now = 0L
        variable.markDismissed("k1")
        now = 5_000L
        assertTrue(variable.isSuppressed("k1"))
        window = 1_000L
        assertFalse(variable.isSuppressed("k1"))
    }

    @Test
    fun disablingClearsHistorySoReenablingDoesNotSuppress() {
        var enabled = true
        val toggleable = DismissSuppression(
            nowMs = { 0L },
            windowMs = { 10_000L },
            enabled = { enabled },
        )
        enabled = false
        toggleable.markDismissed("k1") // 关闭时滑掉：不应留下记录
        enabled = true
        assertFalse(toggleable.isSuppressed("k1"))
        toggleable.markDismissed("k1") // 重新打开后才真正生效
        assertTrue(toggleable.isSuppressed("k1"))
    }

    @Test
    fun overCapacityEvictsOldest() {
        val small = DismissSuppression(
            nowMs = { 0L },
            windowMs = { 10_000L },
            maxEntries = 3,
        )
        small.markDismissed("a")
        small.markDismissed("b")
        small.markDismissed("c")
        small.markDismissed("d") // a 被淘汰
        assertFalse(small.isSuppressed("a"))
        assertTrue(small.isSuppressed("b"))
        assertTrue(small.isSuppressed("c"))
        assertTrue(small.isSuppressed("d"))
    }

    @Test
    fun keysAreIndependent() {
        suppression.markDismissed("a")
        assertTrue(suppression.isSuppressed("a"))
        assertFalse(suppression.isSuppressed("b"))
    }

    @Test
    fun nullKeyIsNeverSuppressed() {
        assertFalse(suppression.isSuppressed(null))
    }
}