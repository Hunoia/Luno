package hunoia.luno.gridpanel.habit

import hunoia.luno.config.model.HabitDayState
import org.junit.Assert.assertEquals
import org.junit.Test

/** 状态 → 视觉语义的唯一映射回归：部件与编辑页日历必须同色同形、同标记。 */
class HabitDayVisualTest {

    @Test
    fun `checked day is solid primary with a check`() {
        val visual = HabitDayState.CHECKED.visual()
        assertEquals(HabitDayRole.PRIMARY, visual.fillRole)
        assertEquals(HabitDayRole.PRIMARY, visual.borderRole)
        assertEquals(1f, visual.fillAlpha, 0f)
        assertEquals(HabitDayMark.CHECK, visual.mark)
    }

    @Test
    fun `due day is a hollow primary ring`() {
        val visual = HabitDayState.DUE.visual()
        assertEquals(null, visual.fillRole)
        assertEquals(HabitDayRole.PRIMARY, visual.borderRole)
        assertEquals(HabitDayMark.NONE, visual.mark)
    }

    @Test
    fun `skipped day is translucent primary with a minus`() {
        val visual = HabitDayState.SKIPPED.visual()
        assertEquals(HabitDayRole.PRIMARY, visual.fillRole)
        assertEquals(HabitDayRole.PRIMARY, visual.borderRole)
        assertEquals(0.3f, visual.fillAlpha, 0f)
        assertEquals(HabitDayMark.MINUS, visual.mark)
    }

    @Test
    fun `overdue day is neutral with an error border`() {
        val visual = HabitDayState.OVERDUE.visual()
        assertEquals(HabitDayRole.NEUTRAL, visual.fillRole)
        assertEquals(HabitDayRole.ERROR, visual.borderRole)
        assertEquals(HabitDayMark.NONE, visual.mark)
    }

    @Test
    fun `inactive day has no error or mark`() {
        val visual = HabitDayState.INACTIVE.visual()
        assertEquals(HabitDayRole.NEUTRAL, visual.fillRole)
        assertEquals(HabitDayMark.NONE, visual.mark)
        assertEquals(false, visual.borderRole == HabitDayRole.ERROR)
    }
}
