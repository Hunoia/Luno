package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureAngle
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.domain.StoredAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进入子手势会话的三种非成功结果必须是**可分辨**的：禁用与悬空引用以前都返回 false，
 * 于是执行层把它们当 `Action.SubGesture` 空操作，表现为「点了没反应」。
 */
class SubGestureEntryTest {

    private fun ref(id: String) = StoredAction.of("internal.subGesture", "id" to id)

    private fun state(vararg gestures: SubGesture) = SubGestureState(
        scope = CoroutineScope(Dispatchers.Unconfined),
        subGestureSettings = SubGestureSettings(gestures.toList()),
        onModeChanged = { _, _, _, _ -> },
    )

    @Test
    fun `enabled sub gesture enters the session`() {
        val state = state(SubGesture(id = "sg"))
        assertEquals(SubGestureEntry.Entered, state.tryEnterSubGesture(ref("sg"), Offset.Zero))
        assertTrue(state.isActive)
    }

    @Test
    fun `disabled sub gesture is reported instead of silently ignored`() {
        val state = state(SubGesture(id = "sg", enabled = false))
        assertEquals(SubGestureEntry.Disabled, state.tryEnterSubGesture(ref("sg"), Offset.Zero))
        assertFalse(state.isActive)
    }

    @Test
    fun `dangling reference is reported as missing`() {
        val state = state(SubGesture(id = "sg"))
        assertEquals(SubGestureEntry.Missing, state.tryEnterSubGesture(ref("gone"), Offset.Zero))
        assertFalse(state.isActive)
    }

    @Test
    fun `other actions fall through`() {
        val state = state(SubGesture(id = "sg"))
        assertEquals(
            SubGestureEntry.Ignored,
            state.tryEnterSubGesture(StoredAction.of("accessibility.back"), Offset.Zero),
        )
        assertFalse(state.isActive)
    }

    @Test
    fun `angle guide passes sub gesture when enabled and hides when disabled`() {
        val boundaries = listOf(0f, 0.25f, 0.5f)
        val captured = mutableListOf<SubGesture?>()
        fun capturingState(gesture: SubGesture) = SubGestureState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            subGestureSettings = SubGestureSettings(listOf(gesture)),
            onModeChanged = { _, _, sg, _ -> captured += sg },
        )
        // 默认开启：回调带整个子手势，overlay 据此画边界线 + 范围圆。
        val enabled = SubGesture(id = "sg", angle = SubGestureAngle(boundaries))
        capturingState(enabled).tryEnterSubGesture(ref("sg"), Offset.Zero)
        assertEquals(enabled, captured.last())
        // 关闭：回调仍带子手势（绘制层凭 showAngleGuide 决定不画），保持触摸源。
        captured.clear()
        val disabled = SubGesture(id = "sg", angle = SubGestureAngle(boundaries), showAngleGuide = false)
        capturingState(disabled).tryEnterSubGesture(ref("sg"), Offset.Zero)
        assertEquals(disabled, captured.last())
        assertFalse(captured.last()?.showAngleGuide ?: true)
    }

    @Test
    fun `release entry is reported so the window can take over the second press`() {
        val releases = mutableListOf<Boolean>()
        fun capturingState() = SubGestureState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            subGestureSettings = SubGestureSettings(listOf(SubGesture(id = "sg"))),
            onModeChanged = { inSubGesture, _, _, release -> if (inSubGesture) releases += release },
        )
        // 手指已抬（抬手触发 / 面板点选 / 悬浮球点按）：触摸窗要接管第二次按下 → 全屏。
        capturingState().tryEnterSubGesture(ref("sg"), Offset.Zero, holdFire = false)
        // 按着进入（触钮停留 / 面板项长按）：手指还在主窗上拖，子手势窗只做视觉 → 小方块。
        capturingState().tryEnterSubGesture(ref("sg"), Offset.Zero, holdFire = true)
        assertEquals(listOf(true, false), releases)
    }

    @Test
    fun `re-press reports the new judgment center`() {
        val centers = mutableListOf<Offset>()
        val state = SubGestureState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            subGestureSettings = SubGestureSettings(listOf(SubGesture(id = "sg"))),
            onModeChanged = { _, _, _, _ -> },
            onCenterChanged = { centers += it },
        )
        state.tryEnterSubGesture(ref("sg"), Offset(10f, 20f))
        state.onDragStart(Offset(100f, 200f))
        assertEquals(listOf(Offset(100f, 200f)), centers)
    }
}
