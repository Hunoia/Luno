package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 浮层跟随触发点定位：横向贴触发侧，竖向只夹对应侧间距、不越屏。
 * 网格面板 / 快捷启动 / 小窗共用这一份几何。
 */
class SurfacePlacementTest {

    private val placement = SurfacePlacement(sideMarginDp = 12f, topGapDp = 12f, bottomGapDp = 12f)

    private fun resolve(
        anchorX: Int = 500,
        anchorY: Int = 500,
        width: Float = 200f,
        height: Float = 200f,
        screenW: Int = 1080,
        screenH: Int = 2400,
        safeTop: Int = 100,
        safeBottom: Int = 80,
        density: Float = 1f,
        verticalAnchorFraction: Float = 0.25f,
        p: SurfacePlacement = placement,
    ) = p.resolveTopLeft(
        anchorX = anchorX,
        anchorY = anchorY,
        widthPx = width,
        heightPx = height,
        screenWidthPx = screenW,
        screenHeightPx = screenH,
        safeTopPx = safeTop,
        safeBottomPx = safeBottom,
        density = density,
        verticalAnchorFraction = verticalAnchorFraction,
    )

    @Test
    fun `left half trigger hugs left side margin`() {
        assertEquals(12f, resolve(anchorX = 100).x, 0.001f)
    }

    @Test
    fun `right half trigger hugs right side margin`() {
        assertEquals(1080f - 200f - 12f, resolve(anchorX = 900).x, 0.001f)
    }

    @Test
    fun `side flips at the exact screen center`() {
        assertEquals(12f, resolve(anchorX = 539).x, 0.001f)
        assertEquals(1080f - 200f - 12f, resolve(anchorX = 540).x, 0.001f)
    }

    @Test
    fun `vertical follows trigger with panel quarter offset`() {
        // 触发点落在面板高度 1/4 处（与旧手感一致）。
        assertEquals(450f, resolve(anchorY = 500).y, 0.001f)
    }

    @Test
    fun `mini window centers on trigger when asked`() {
        assertEquals(400f, resolve(anchorY = 500, verticalAnchorFraction = 0.5f).y, 0.001f)
    }

    @Test
    fun `top gap wins near the status bar`() {
        // 安全区上沿 100 + 上间距 12。
        assertEquals(112f, resolve(anchorY = 0).y, 0.001f)
    }

    @Test
    fun `bottom gap wins near the navigation bar`() {
        // 2400 − 80 − 200 − 12。
        assertEquals(2108f, resolve(anchorY = 2400).y, 0.001f)
    }

    @Test
    fun `panel taller than screen stays on screen`() {
        val result = resolve(anchorY = 1200, height = 3000f)
        assertEquals(0f, result.y, 0.001f)
    }

    @Test
    fun `top trigger ignores bottom gap but keeps bottom safe area`() {
        // 触发在上半屏、面板很高：底边夹到导航栏上沿，下间距不参与。
        val result = resolve(anchorY = 1100, height = 2200f)
        assertEquals(2400f - 80f - 2200f, result.y, 0.001f)
    }

    @Test
    fun `shrink top gap before giving up opposite safe area`() {
        // 上间距 12 + 上安全区 100 放不下 2250 高：先缩上间距，再让底部安全区。
        val result = resolve(anchorY = 1100, height = 2250f)
        assertEquals(150f, result.y, 0.001f)
    }

    @Test
    fun `bottom trigger ignores top gap but keeps top safe area`() {
        val result = resolve(anchorY = 2300, height = 2200f)
        assertEquals(2400f - 80f - 2200f - 12f, result.y, 0.001f)
    }

    @Test
    fun `shrink bottom gap before giving up opposite safe area`() {
        // 下间距 12 放不下 2250 高：先缩下间距，仍不够再让顶部安全区。
        val result = resolve(anchorY = 2300, height = 2250f)
        assertEquals(2400f - 80f - 2250f, result.y, 0.001f)
    }

    @Test
    fun `wider than screen never goes off screen`() {
        val left = resolve(anchorX = 100, width = 1200f)
        assertEquals(0f, left.x, 0.001f)

        val right = resolve(anchorX = 900, width = 1200f)
        assertEquals(0f, right.x, 0.001f)
    }

    @Test
    fun `no opposite gap and current gap shrinks when full`() {
        // 1080 − 1070 = 10：左侧间距从 12 收到 10，右侧收到 0，都贴屏。
        assertEquals(10f, resolve(anchorX = 100, width = 1070f).x, 0.001f)
        assertEquals(0f, resolve(anchorX = 900, width = 1070f).x, 0.001f)
    }

    @Test
    fun `dp margins scale with density`() {
        val result = resolve(anchorX = 100, density = 2f)
        assertEquals(24f, result.x, 0.001f)
    }

    @Test
    fun `out of range values are coerced for use`() {
        val weird = SurfacePlacement(sideMarginDp = -5f, topGapDp = 999f, bottomGapDp = 60.4f)
        assertEquals(SurfacePlacement(0f, 60f, 60f), weird.coerced())
        // 定位内部同样夹取，导入的越界数据不会把面板推出屏。
        assertEquals(0f, resolve(anchorX = 100, p = weird).x, 0.001f)
        assertEquals(100f + 60f, resolve(anchorY = 0, p = weird).y, 0.001f)
    }
}
