package hunoia.luno.gridpanel

import androidx.compose.ui.geometry.Offset
import hunoia.luno.gridpanel.geometry.ResizeCorner
import hunoia.luno.gridpanel.geometry.choosePanelResizeCorner
import hunoia.luno.gridpanel.geometry.resolvePanelResize
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelResizeTest {

    private fun cornerFor(left: Float, top: Float) = choosePanelResizeCorner(
        cardLeft = left,
        cardTop = top,
        cardWidth = 200f,
        cardHeight = 200f,
        screenWidth = 1000f,
        screenHeight = 1000f,
        insetsTop = 0,
        insetsBottom = 0,
        marginPx = 10f,
    )

    @Test
    fun `panel at right bottom resizes from top start`() {
        assertEquals(ResizeCorner.TOP_START, cornerFor(left = 790f, top = 790f))
    }

    @Test
    fun `panel at left top resizes from bottom end`() {
        assertEquals(ResizeCorner.BOTTOM_END, cornerFor(left = 10f, top = 10f))
    }

    @Test
    fun `panel centered uses first farthest corner deterministically`() {
        // 四角余量相同：按 TOP_START、TOP_END、BOTTOM_START、BOTTOM_END 的顺序取首项。
        assertEquals(ResizeCorner.TOP_START, cornerFor(left = 400f, top = 400f))
    }

    private fun resolve(corner: ResizeCorner, total: Offset) = resolvePanelResize(
        corner = corner,
        startSize = Offset(200f, 200f),
        startPos = Offset(100f, 100f),
        total = total,
        screenW = 1000f,
        screenH = 1000f,
        insetsTop = 0,
        insetsBottom = 0,
        screenMarginPx = 10f,
        // 与渲染同一套几何（GridMetrics 用 columns=4, gap=10, pad=20, indicator=24）。
        // 高度连续跟手：top = 锚点 − 连续卡高，不再按行数量化。
        columns = 4,
        gridPad = 20f,
        indicator = 24f,
        gap = 10f,
    )

    @Test
    fun `bottom end drag keeps opposite top-left corner fixed`() {
        // 拖 BOTTOM_END 扩大：左上角（对侧角）固定，右下角随拖拽移动。
        val r = resolve(ResizeCorner.BOTTOM_END, Offset(50f, 40f))
        assertEquals(100f, r.left, 0.01f) // 对侧左上角 x 固定
        assertEquals(100f, r.top, 0.01f) // 对侧左上角 y 固定（非 top 手柄轴）
        assertEquals(250f / 1000f, r.cardWidthFraction, 0.001f) // 200+50
        assertEquals(240f / 1000f, r.cardHeightFraction, 0.001f) // 200+40
        // 右下角 = 左上 + 渲染尺寸，随拖拽移动（宽不量化，右下 x 精确）。
        assertEquals(100f + 250f, r.left + r.cardWidthFraction * 1000f, 0.01f)
    }

    @Test
    fun `top end drag keeps opposite bottom-left corner fixed`() {
        // 拖 TOP_END 扩大（向右 + 向上）：左下角（对侧角）固定。
        val r = resolve(ResizeCorner.TOP_END, Offset(30f, -50f))
        assertEquals(100f, r.left, 0.01f) // 对侧左下角 x 固定（非 left 手柄轴）
        // 高度连续：newH=250 → top = 锚点(300) − 250 = 50，渲染下边 = 50 + 250 = 300。
        assertEquals(50f, r.top, 0.01f)
        assertEquals(230f / 1000f, r.cardWidthFraction, 0.001f) // 200+30
        assertEquals(250f / 1000f, r.cardHeightFraction, 0.001f) // 200+50
    }

    @Test
    fun `small height delta moves card height continuously`() {
        // 5px 的拖动必须直接反映到卡高，而不是吸附到下一整行。
        val r = resolve(ResizeCorner.BOTTOM_END, Offset(0f, 5f))
        assertEquals(100f, r.left, 0.01f)
        assertEquals(100f, r.top, 0.01f)
        assertEquals(205f / 1000f, r.cardHeightFraction, 0.001f)
        assertEquals(100f + 205f, r.top + r.cardHeightFraction * 1000f, 0.01f)
    }

    @Test
    fun `drag beyond screen edge clamps size but never moves anchor`() {
        // 拖 BOTTOM_END 超出屏幕右下：尺寸被 clamp 到屏幕边界，左上角仍不动。
        val r = resolve(ResizeCorner.BOTTOM_END, Offset(9999f, 9999f))
        assertEquals(100f, r.left, 0.01f)
        assertEquals(100f, r.top, 0.01f)
        // 右边 = min(1000-10, ...) = 990；左边固定 100 → 宽 890。
        assertEquals(990f, r.left + r.cardWidthFraction * 1000f, 0.01f)
        assertEquals(890f / 1000f, r.cardWidthFraction, 0.001f)
        assertEquals(890f / 1000f, r.cardHeightFraction, 0.001f)
    }
}
