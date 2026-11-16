package hunoia.luno.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 打断时序的契约测试。
 *
 * 这是整个「取消源应用手势」能力里唯一不能出错的地方：漏掉 CANCEL 会让源应用永久停在
 * 「按下了但没抬起」（长按菜单 / 按压态 / 拖拽全被挂住），比不打断更糟。所以逐条钉死。
 */
class TouchInterruptionSequenceTest {

    private class Recorder(
        private val downResult: Boolean = true,
        private val cancelResult: Boolean = true,
        private val current: Boolean = true,
        private val downThrows: Boolean = false,
    ) {
        val events = mutableListOf<String>()
        fun isCurrent(): Boolean {
            events += "check"
            return current
        }
        fun down(): Boolean {
            events += "down"
            if (downThrows) throw IllegalStateException("injection blew up")
            return downResult
        }
        fun cancel(): Boolean {
            events += "cancel"
            return cancelResult
        }
        fun pause() {
            events += "pause"
        }

        fun run(): TouchInterruption = TouchInterruptionSequence.run(
            isCurrent = ::isCurrent,
            down = ::down,
            cancel = ::cancel,
            pause = ::pause,
        )
    }

    @Test
    fun `accepted down and cancel reports interrupted`() {
        val recorder = Recorder()
        assertEquals(TouchInterruption.Interrupted, recorder.run())
        assertEquals(listOf("check", "down", "pause", "cancel"), recorder.events)
    }

    @Test
    fun `guard rejection injects nothing`() {
        val recorder = Recorder(current = false)
        assertEquals(TouchInterruption.NotAttempted, recorder.run())
        // 守卫拒绝时一个事件都不能发：发了 DOWN 就必须有 CANCEL，而调用方已经放弃了。
        assertEquals(listOf("check"), recorder.events)
    }

    @Test
    fun `rejected down still sends cancel`() {
        val recorder = Recorder(downResult = false)
        assertEquals(TouchInterruption.Failed, recorder.run())
        // DOWN 被拒时仍要补 CANCEL：我们无法确定系统是否部分接受了它。
        assertEquals(listOf("check", "down", "cancel"), recorder.events)
        assertFalse(recorder.events.contains("pause"))
    }

    @Test
    fun `rejected cancel is not reported as interrupted`() {
        val recorder = Recorder(cancelResult = false)
        assertEquals(TouchInterruption.Failed, recorder.run())
        assertEquals(listOf("check", "down", "pause", "cancel"), recorder.events)
    }

    @Test
    fun `exception during down still attempts cancel`() {
        val recorder = Recorder(downThrows = true)
        var thrown: Throwable? = null
        try {
            recorder.run()
        } catch (error: IllegalStateException) {
            thrown = error
        }
        // 异常要往上抛（不能吞），但 CANCEL 必须已经发出去。
        assertTrue(thrown != null)
        assertEquals(listOf("check", "down", "cancel"), recorder.events)
    }

    @Test
    fun `guard is consulted before down only`() {
        // 守卫只在 DOWN 之前有意义：放行过 DOWN 之后必须走完 CANCEL，
        // 哪怕这期间守卫已经失效（协程被取消）。
        var current = true
        val events = mutableListOf<String>()
        val result = TouchInterruptionSequence.run(
            isCurrent = { events += "check"; current },
            down = { events += "down"; current = false; true },
            cancel = { events += "cancel"; true },
            pause = { events += "pause" },
        )
        assertEquals(TouchInterruption.Interrupted, result)
        assertEquals(listOf("check", "down", "pause", "cancel"), events)
    }

    @Test
    fun `pause sits between down and cancel`() {
        val recorder = Recorder()
        recorder.run()
        val downIndex = recorder.events.indexOf("down")
        val pauseIndex = recorder.events.indexOf("pause")
        val cancelIndex = recorder.events.indexOf("cancel")
        assertTrue("pause 必须在 down 之后", pauseIndex > downIndex)
        assertTrue("cancel 必须在 pause 之后", cancelIndex > pauseIndex)
    }

    // ---- 接管顺序：注入必须先于挂盾 ----

    /**
     * [TouchTakeoverOrder.run] 是 suspend（生产里跨 IO/Main 切线程）。这里在 JVM 单测
     * 用自带事件循环驱动：顺序断言与线程调度无关，只需要它按调用顺序执行。
     */
    private fun order(
        inject: () -> TouchInterruption,
        attachShield: () -> Unit,
    ): TouchInterruption = runBlockingSuspend { TouchTakeoverOrder.run(inject, attachShield) }

    private fun <T> runBlockingSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        kotlinx.coroutines.runBlocking { outcome = runCatching { block() } }
        return outcome!!.getOrThrow()
    }

    @Test
    fun `shield is attached only after injection finished`() {
        val events = mutableListOf<String>()
        var injectReturned = false
        order(
            inject = { events += "inject"; injectReturned = true; TouchInterruption.Interrupted },
            attachShield = { events += if (injectReturned) "shield" else "shield-before-inject" },
        )
        assertEquals(listOf("inject", "shield"), events)
    }

    @Test
    fun `shield is still attached when injection was not attempted`() {
        // 注入打不出去时更需要盾：否则拖动会同时被源应用和我们处理。
        val events = mutableListOf<String>()
        val result = order(
            inject = { events += "inject"; TouchInterruption.NotAttempted },
            attachShield = { events += "shield" },
        )
        assertEquals(TouchInterruption.NotAttempted, result)
        assertEquals(listOf("inject", "shield"), events)
    }

    @Test
    fun `shield is still attached when injection failed`() {
        val events = mutableListOf<String>()
        val result = order(
            inject = { events += "inject"; TouchInterruption.Failed },
            attachShield = { events += "shield" },
        )
        assertEquals(TouchInterruption.Failed, result)
        assertEquals(listOf("inject", "shield"), events)
    }

    @Test
    fun `injection result is reported to the caller`() {
        val results = TouchInterruption.entries.map { expected ->
            order(inject = { expected }, attachShield = {})
        }
        assertEquals(TouchInterruption.entries, results)
    }
}
