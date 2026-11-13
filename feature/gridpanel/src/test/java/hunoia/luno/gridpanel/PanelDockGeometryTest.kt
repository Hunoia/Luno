package hunoia.luno.gridpanel

import hunoia.luno.gridpanel.geometry.gridPanelCardHeight
import hunoia.luno.gridpanel.geometry.needsScroll
import hunoia.luno.gridpanel.geometry.resolveDockAnchoredCard
import hunoia.luno.gridpanel.geometry.resolvePanelDockPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏几何：底栏是卡的兄弟、贴在卡下方，并且**不能越出屏幕边距**。
 *
 * 同时钉住「底栏当额外底部边距」的落点：常规面板（0.62）高度不变，
 * 只有本来就顶到屏幕边的配置才会被可用高度夹住。两处公式必须同源，
 * 否则会出现「卡被夹了、底栏却按没夹的高度定位」的错位。
 */
class PanelDockGeometryTest {

    private fun height(fraction: Float, maxCardHeightPx: Float = Float.POSITIVE_INFINITY) =
        gridPanelCardHeight(
            columns = 4,
            cardW = 720f,
            cardHeightFraction = fraction,
            screenH = 1000f,
            gridPad = 10f,
            indicator = 24f,
            gap = 8f,
            maxCardHeightPx = maxCardHeightPx,
        )

    @Test
    fun `card height only shrinks when it would not fit`() {
        // 常规面板：上限比它高，结果与没有底栏时完全一致。
        assertEquals(620f, height(0.62f), 0.01f)
        assertEquals(620f, height(0.62f, maxCardHeightPx = 900f), 0.01f)
        // 顶到屏幕边的面板：夹到可用高度。
        assertEquals(880f, height(0.90f, maxCardHeightPx = 880f), 0.01f)
    }

    @Test
    fun `card height never goes below one row`() {
        // 上限比一行还矮时仍保住一行，不出 0 高卡片。
        val oneRow = height(0f)
        assertEquals(oneRow, height(0.90f, maxCardHeightPx = 10f), 0.01f)
        assertTrue(oneRow > 0f)
    }

    @Test
    fun `dock matches the card width and keeps the card off-centre`() {
        val placement = resolvePanelDockPlacement(
            cardX = 100f,
            cardY = 200f,
            cardWidthPx = 800f,
            cardHeightPx = 600f,
            gapPx = 8f,
            tabCount = 4,
            maxTabWidthPx = 200f,
            minTabWidthPx = 48f,
            insetPx = 8f,
        )
        // 底栏与卡同宽、中心与卡中心重合：卡贴触发侧时不会被拉到屏幕中间。
        assertEquals(800f, placement.widthPx, 0.01f)
        assertEquals(500f, placement.centerX, 0.01f)
        assertEquals(808f, placement.topY, 0.01f)
        // 4 × 196 + 2 × 8 = 800：正好铺满。
        assertEquals(196f, placement.tabWidthPx, 0.01f)
        assertEquals(800f, placement.contentWidthPx, 0.01f)
        assertEquals(false, placement.needsScroll)
    }

    @Test
    fun `few tabs stay capped and centred inside the card width`() {
        val placement = resolvePanelDockPlacement(
            cardX = 0f,
            cardY = 0f,
            cardWidthPx = 800f,
            cardHeightPx = 300f,
            gapPx = 8f,
            tabCount = 2,
            maxTabWidthPx = 64f,
            minTabWidthPx = 48f,
            insetPx = 6f,
        )
        // tab 不被拉伸：内容比底栏窄，靠渲染层居中。
        assertEquals(64f, placement.tabWidthPx, 0.01f)
        assertTrue(placement.contentWidthPx < placement.widthPx)
        assertEquals(false, placement.needsScroll)
    }

    @Test
    fun `too many tabs for a narrow card fall back to scrolling`() {
        val placement = resolvePanelDockPlacement(
            cardX = 0f,
            cardY = 0f,
            cardWidthPx = 200f,
            cardHeightPx = 300f,
            gapPx = 8f,
            tabCount = 6,
            maxTabWidthPx = 64f,
            minTabWidthPx = 48f,
            insetPx = 6f,
        )
        // 底栏不撑宽：tab 吃到命中区下限，超出的靠横向滚动。
        assertEquals(200f, placement.widthPx, 0.01f)
        assertEquals(48f, placement.tabWidthPx, 0.01f)
        assertTrue(placement.contentWidthPx > placement.widthPx)
        assertEquals(true, placement.needsScroll)
    }

    @Test
    fun `switching panels keeps the card bottom glued to the frozen dock`() {
        // 底栏在打开时冻结，切换后的面板只换高度：底边永远 = 底栏上沿 − 间距，
        // 高面板向上长，不会盖住底栏。
        val dockTop = 700f
        val gap = 8f
        listOf(200f, 420f, 600f).forEach { cardHeight ->
            val offset = resolveDockAnchoredCard(
                dockCenterX = 500f,
                dockTopY = dockTop,
                cardWidthPx = 720f,
                cardHeightPx = cardHeight,
                screenWidthPx = 1000f,
                sideMarginPx = 12f,
                gapPx = gap,
                safeTopPx = 50f,
            )
            assertEquals(dockTop - gap, offset.y + cardHeight, 0.01f)
            assertEquals(500f, offset.x + 360f, 0.01f)
        }
    }

    @Test
    fun `card hugging the trigger side is not pulled to the centre`() {
        // 回归：底栏曾比卡宽，中心被夹到屏幕中线，卡又反过来以底栏中心定位 → 卡被拉到中间。
        // 现在底栏 = 卡宽，中心 = 卡中心，卡原地不动。
        val cardX = 12f
        val cardW = 400f
        val dock = resolvePanelDockPlacement(
            cardX = cardX,
            cardY = 200f,
            cardWidthPx = cardW,
            cardHeightPx = 500f,
            gapPx = 8f,
            tabCount = 5,
            maxTabWidthPx = 64f,
            minTabWidthPx = 48f,
            insetPx = 6f,
        )
        val card = resolveDockAnchoredCard(
            dockCenterX = dock.centerX,
            dockTopY = dock.topY,
            cardWidthPx = cardW,
            cardHeightPx = 500f,
            screenWidthPx = 1000f,
            sideMarginPx = 12f,
            gapPx = 8f,
            safeTopPx = 0f,
        )
        assertEquals(cardX, card.x, 0.01f)
        assertEquals(cardX, dock.centerX - dock.widthPx / 2f, 0.01f)
    }

    @Test
    fun `dock anchored card clamps horizontally and never passes the safe top`() {
        // 底栏靠右：窄卡仍居中于底栏，但不能越过右边距。
        val right = resolveDockAnchoredCard(
            dockCenterX = 990f,
            dockTopY = 700f,
            cardWidthPx = 400f,
            cardHeightPx = 300f,
            screenWidthPx = 1000f,
            sideMarginPx = 12f,
            gapPx = 8f,
            safeTopPx = 50f,
        )
        assertEquals(600f, right.x, 0.01f)

        // 放不下的高卡：优先保住卡顶不越安全区（底边可临时压到底栏上）。
        val tall = resolveDockAnchoredCard(
            dockCenterX = 500f,
            dockTopY = 400f,
            cardWidthPx = 720f,
            cardHeightPx = 900f,
            screenWidthPx = 1000f,
            sideMarginPx = 12f,
            gapPx = 8f,
            safeTopPx = 50f,
        )
        assertEquals(50f, tall.y, 0.01f)
    }
}
