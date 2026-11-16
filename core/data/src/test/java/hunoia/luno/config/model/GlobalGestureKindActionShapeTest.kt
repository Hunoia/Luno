package hunoia.luno.config.model

import hunoia.luno.core.domain.StoredAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全屏触发形态的「动作存放在哪」契约。
 *
 * 这一组断言是事故回归：拆分「长按拖放」为独立 kind 时，动作读取、动作写回、识别过滤
 * 三处各自写了一份 `kind == LongPress || ...` 的条件，新增的 kind 漏在条件外，症状是
 * 「动作选择页永远显示无」+「选了动作也不触发」。所以这里把三方的共同判据
 * `isArmedDrag` 与它的两个消费者钉在一起。
 *
 * 结论沉淀：接管触摸不是一种手势形态，而是触发上的 `consumeTouch` 开关（见下）。
 */
class GlobalGestureKindActionShapeTest {

    private fun trigger(
        kind: GlobalGestureKind,
        directionActions: DirectionActions = DirectionActions(),
        action: StoredAction? = null,
        fireOnHold: Boolean = false,
    ) = GlobalGestureTrigger(
        id = "t",
        kind = kind,
        fireOnHold = fireOnHold,
        action = action,
        directionActions = directionActions,
    )

    @Test
    fun `armed drag covers exactly long press`() {
        assertTrue(GlobalGestureKind.LongPress.isArmedDrag)
        // 方向形态不是「按住后拖」：它们的动作本来就来自扇区，但识别流程不同。
        assertFalse(GlobalGestureKind.Slide.isArmedDrag)
        assertFalse(GlobalGestureKind.SlideHold.isArmedDrag)
        assertFalse(GlobalGestureKind.LongSlide.isArmedDrag)
        assertFalse(GlobalGestureKind.LongSlideHold.isArmedDrag)
        assertFalse(GlobalGestureKind.Tap.isArmedDrag)
        assertFalse(GlobalGestureKind.DoubleTap.isArmedDrag)
    }

    @Test
    fun `consume touch is a trigger switch not a gesture kind`() {
        // 接管不是形态：任何 kind 都能配，默认关（只读档）。
        val plain = GlobalGestureTrigger(id = "t", kind = GlobalGestureKind.LongPress)
        assertFalse(plain.consumeTouch)
        val taking = plain.copy(consumeTouch = true)
        assertTrue(taking.consumeTouch)
        // 开关不影响动作形状：开了接管仍然是「按住后拖 → 扇区取动作」。
        assertEquals(plain.kind, taking.kind)
        assertTrue(taking.kind.isArmedDrag)
        val slotAction = StoredAction.of("test.slot")
        assertEquals(
            plain.copy(directionActions = DirectionActions(mapOf(0 to slotAction))).actionFor(0),
            taking.copy(directionActions = DirectionActions(mapOf(0 to slotAction))).actionFor(0),
        )
    }

    @Test
    fun `armed drag reads its action from the direction slot`() {
        val slotAction = StoredAction.of("test.slot")
        val single = StoredAction.of("test.single")
        val subject = trigger(
            kind = GlobalGestureKind.LongPress,
            directionActions = DirectionActions(mapOf(0 to slotAction)),
            // 单动作字段是按下即触发的形态用的；长按后拖不该读它。
            action = single,
        )
        assertEquals(slotAction, subject.actionFor(0))
        assertTrue(subject.hasAnyAction)
    }

    @Test
    fun `armed drag without any direction action is filtered out`() {
        // 这正是「加了触发但完全没反应」的根因：没有动作 → 不参与识别。
        val subject = trigger(kind = GlobalGestureKind.LongPress)
        assertFalse(subject.hasAnyAction)
    }

    @Test
    fun `armed drag ignores the single action field`() {
        // 动作选择页写动作时必须写进 directionActions，否则选了也不生效。
        val subject = trigger(
            kind = GlobalGestureKind.LongPress,
            action = StoredAction.of("test.single"),
        )
        assertFalse(subject.hasAnyAction)
    }

    @Test
    fun `long press with fire on hold reads the single action`() {
        val single = StoredAction.of("test.single")
        val subject = trigger(
            kind = GlobalGestureKind.LongPress,
            action = single,
            fireOnHold = true,
        )
        assertEquals(single, subject.actionFor(0))
        assertTrue(subject.hasAnyAction)
    }

    @Test
    fun `long press waiting for drag reads the direction slot`() {
        val slotAction = StoredAction.of("test.slot")
        val subject = trigger(
            kind = GlobalGestureKind.LongPress,
            directionActions = DirectionActions(mapOf(0 to slotAction)),
            fireOnHold = false,
        )
        assertEquals(slotAction, subject.actionFor(0))
        assertTrue(subject.hasAnyAction)
    }

    @Test
    fun `newly created armed drag triggers are usable out of the box`() {
        // create() 不给动作的话，用户新增一条触发只会得到「没反应」。
        listOf(GlobalGestureKind.LongPress).forEach { kind ->
            val created = GlobalGestureTrigger.create(kind)
            assertTrue("$kind must be usable after create()", created.hasAnyAction)
            assertEquals(1, created.fingers)
            assertEquals(1, created.angle.count)
        }
    }

    @Test
    fun `default zone trigger still carries screen pick in the direction slot`() {
        val trigger = GlobalGestureDefaults.Zones.single().triggers.single()
        assertTrue(trigger.hasAnyAction)
        assertEquals("accessibility.screenPick", trigger.actionFor(0)?.typeId)
    }
}
