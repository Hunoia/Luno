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
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubGestureFallbackTest {

    private fun actions(): DirectionActions =
        DirectionActions((0..7).associateWith { StoredAction.of(TEST_TYPE) })

    private fun state(subGesture: SubGesture): SubGestureState = SubGestureState(
        scope = CoroutineScope(Dispatchers.Unconfined),
        subGestureSettings = SubGestureSettings(listOf(subGesture)),
        onModeChanged = { _, _, _ -> },
    ).also { state ->
        state.tryEnterSubGesture(
            StoredAction.of("internal.subGesture", "id" to subGesture.id),
            Offset.Unspecified,
        )
        state.onDragStart(Offset.Zero)
    }

    private fun subGesture(
        slideActions: DirectionActions = DirectionActions(),
        longSlideActions: DirectionActions = DirectionActions(),
    ) = SubGesture(
        id = "sg",
        triggerDistance = 100,
        longSlideTriggerDistance = 300,
        slideActions = slideActions,
        longSlideActions = longSlideActions,
    )

    @Test
    fun `远距离缺少长滑动作时回退到滑动动作`() {
        val state = state(subGesture(slideActions = actions()))

        state.onDrag(Offset(400f, 0f))
        val result = state.onDragEnd()

        assertNotNull(result)
        assertEquals(GestureTriggerType.Slide, result!!.triggerType)
        assertEquals(TEST_TYPE, result.action?.typeId)
    }

    @Test
    fun `远距离存在长滑动作时优先长滑动作`() {
        val state = state(
            subGesture(
                slideActions = actions(),
                longSlideActions = actions(),
            )
        )

        state.onDrag(Offset(400f, 0f))
        val result = state.onDragEnd()

        assertNotNull(result)
        assertEquals(GestureTriggerType.LongSlide, result!!.triggerType)
        assertEquals(TEST_TYPE, result.action?.typeId)
    }

    @Test
    fun `短距离缺少滑动动作时回退到长滑动作`() {
        val state = state(subGesture(longSlideActions = actions()))

        state.onDrag(Offset(150f, 0f))
        val result = state.onDragEnd()

        assertNotNull(result)
        assertEquals(GestureTriggerType.LongSlide, result!!.triggerType)
        assertEquals(TEST_TYPE, result.action?.typeId)
    }

    @Test
    fun `按住类回退顺序与主手势一致`() {
        assertEquals(
            listOf(
                GestureTriggerType.LongSlideHold,
                GestureTriggerType.SlideHold,
                GestureTriggerType.Slide,
                GestureTriggerType.LongSlide,
            ),
            GestureTriggerType.LongSlideHold.sameDirectionFallbackOrder(),
        )
        assertEquals(
            listOf(
                GestureTriggerType.SlideHold,
                GestureTriggerType.LongSlideHold,
                GestureTriggerType.Slide,
                GestureTriggerType.LongSlide,
            ),
            GestureTriggerType.SlideHold.sameDirectionFallbackOrder(),
        )
    }

    private companion object {
        const val TEST_TYPE = "test.subGestureFallback"
    }
}
