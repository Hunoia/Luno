package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.domain.StoredAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 按住类触发（SlideHold / LongSlideHold）的时长判定。
 *
 * 时间源由 [SubGestureState] 注入：`unitTests.isReturnDefaultValues = true` 会让
 * `SystemClock.uptimeMillis()` 恒为 0，旧实现用 0 作“未开始计时”哨兵，按住分支永远
 * 无法在 JVM 里推进。这里用假时钟覆盖“到点前不触发、到点后触发”。
 */
class SubGestureHoldTriggerTest {

    private var now = START_MS

    private fun actions(): DirectionActions =
        DirectionActions((0..7).associateWith { StoredAction.of(TEST_TYPE) })

    private fun state(subGesture: SubGesture): SubGestureState = SubGestureState(
        scope = CoroutineScope(Dispatchers.Unconfined),
        subGestureSettings = SubGestureSettings(listOf(subGesture)),
        onModeChanged = { _, _, _ -> },
        nowMs = { now },
    ).also { state ->
        state.tryEnterSubGesture(
            StoredAction.of("internal.subGesture", "id" to subGesture.id),
            Offset.Unspecified,
        )
        state.onDragStart(Offset.Zero)
    }

    private fun subGesture(
        slideHoldActions: DirectionActions = DirectionActions(),
        longSlideHoldActions: DirectionActions = DirectionActions(),
    ) = SubGesture(
        id = "sg",
        triggerDistance = 100,
        longSlideTriggerDistance = 300,
        slideHoldTriggerDelayMs = 120,
        longSlideHoldTriggerDelayMs = 120,
        slideHoldActions = slideHoldActions,
        longSlideHoldActions = longSlideHoldActions,
    )

    @Test
    fun `滑动按住未到阈值前不触发`() {
        val state = state(subGesture(slideHoldActions = actions()))

        assertNull(state.onDrag(Offset(150f, 0f)))
        now += 119
        assertNull(state.onDrag(Offset.Zero))
    }

    @Test
    fun `滑动按住到阈值后触发 SlideHold`() {
        val state = state(subGesture(slideHoldActions = actions()))

        assertNull(state.onDrag(Offset(150f, 0f)))
        now += 120
        val result = state.onDrag(Offset.Zero)

        assertEquals(GestureTriggerType.SlideHold, result?.triggerType)
        assertEquals(TEST_TYPE, result?.action?.typeId)
    }

    @Test
    fun `长滑按住到阈值后触发 LongSlideHold`() {
        val state = state(subGesture(longSlideHoldActions = actions()))

        assertNull(state.onDrag(Offset(400f, 0f)))
        now += 120
        val result = state.onDrag(Offset.Zero)

        assertEquals(GestureTriggerType.LongSlideHold, result?.triggerType)
    }

    @Test
    fun `距离回落到阈值内会重置按住计时`() {
        val state = state(subGesture(slideHoldActions = actions()))

        assertNull(state.onDrag(Offset(150f, 0f)))
        now += 119
        // 回到触发距离内：计时清零
        assertNull(state.onDrag(Offset(-150f, 0f)))
        // 再次进入按住区，从此刻重新计时
        assertNull(state.onDrag(Offset(150f, 0f)))
        now += 119
        assertNull(state.onDrag(Offset.Zero))
        now += 1
        assertEquals(GestureTriggerType.SlideHold, state.onDrag(Offset.Zero)?.triggerType)
    }

    @Test
    fun `时钟从 0 开始时按住计时仍然生效`() {
        now = 0L
        val state = state(subGesture(slideHoldActions = actions()))

        assertNull(state.onDrag(Offset(150f, 0f)))
        now += 120
        assertEquals(GestureTriggerType.SlideHold, state.onDrag(Offset.Zero)?.triggerType)
    }

    private companion object {
        const val TEST_TYPE = "test.subGestureHold"
        const val START_MS = 1_000L
    }
}
