package hunoia.luno.gridpanel.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** 只拦惯性、不拦拖动：列表在顶部继续拖仍然要能滑面板。 */
class PanelFlingGateTest {

    @Test
    fun `scrollable list eats leftover fling velocity`() = runBlocking {
        val gate = PanelFlingGate { true }
        gate.onPreFling(Velocity(0f, 1000f))

        assertEquals(
            Velocity(0f, 800f),
            gate.onPostFling(Velocity(0f, 200f), Velocity(0f, 800f)),
        )
    }

    @Test
    fun `short list lets the panel fling`() = runBlocking {
        val gate = PanelFlingGate { false }
        gate.onPreFling(Velocity(0f, 1000f))

        assertEquals(Velocity.Zero, gate.onPostFling(Velocity(0f, 1000f), Velocity.Zero))
    }

    @Test
    fun `fling leftover scroll frames are consumed`() = runBlocking {
        val gate = PanelFlingGate { true }
        gate.onPreFling(Velocity(0f, 1000f))
        val leftover = Offset(0f, 60f)

        assertEquals(
            leftover,
            gate.onPostScroll(Offset.Zero, leftover, NestedScrollSource.SideEffect),
        )
    }

    @Test
    fun `drag beyond the edge is not consumed`() {
        val gate = PanelFlingGate { true }
        val leftover = Offset(0f, 120f)

        assertEquals(
            Offset.Zero,
            gate.onPostScroll(Offset.Zero, leftover, NestedScrollSource.UserInput),
        )
    }
}
