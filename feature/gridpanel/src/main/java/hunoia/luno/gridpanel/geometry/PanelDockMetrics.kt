package hunoia.luno.gridpanel.geometry

import hunoia.luno.config.model.SurfaceOffset
import kotlin.math.min

/**
 * 底栏定位结果（px，屏幕坐标）。
 *
 * [widthPx] 恒等于**打开那一刻的面板宽度**：底栏是卡的下巴，比卡宽或比卡窄都会读成
 * 「另一个东西」。tab 放不下时 [contentWidthPx] 超过 [widthPx]，由渲染层横向滚动。
 * [centerX] 就是卡的中心，不做夹取 —— 夹取会把贴在触发侧的卡拉到屏幕中间。
 */
data class PanelDockPlacement(
    val tabWidthPx: Float,
    val centerX: Float,
    val topY: Float,
    val widthPx: Float,
    /** 所有 tab 排开后的总宽（含内边距）；大于 [widthPx] 表示需要横向滚动。 */
    val contentWidthPx: Float,
)

/** 内容宽于底栏 = 需要横向滚动。 */
val PanelDockPlacement.needsScroll: Boolean
    get() = contentWidthPx > widthPx + 0.5f

/**
 * 底栏定位（纯函数）：贴卡片底边、**与卡片同宽同中心**、tab 按可用宽度收窄。
 *
 * - 宽度：恒等于 [cardWidthPx]。入口多时 tab 先缩到 [minTabWidthPx]（命中区下限），
 *   再放不下就靠横向滚动，绝不把底栏撑得比卡宽。
 * - 水平：不做夹取。底栏中心 = 卡中心，切换面板时卡保持贴触发侧的位置。
 * - 垂直：卡底边 + [gapPx]。
 *
 * 放在 geometry 而不是 ui 包：这里只有算术，JVM 单测直接跑。
 */
fun resolvePanelDockPlacement(
    cardX: Float,
    cardY: Float,
    cardWidthPx: Float,
    cardHeightPx: Float,
    gapPx: Float,
    tabCount: Int,
    maxTabWidthPx: Float,
    minTabWidthPx: Float,
    insetPx: Float,
): PanelDockPlacement {
    val count = tabCount.coerceAtLeast(1)
    val widthPx = cardWidthPx.coerceAtLeast(1f)
    val innerPx = (widthPx - insetPx * 2f).coerceAtLeast(1f)
    val tabWidthPx = min(maxTabWidthPx, innerPx / count).coerceAtLeast(min(minTabWidthPx, innerPx))
    return PanelDockPlacement(
        tabWidthPx = tabWidthPx,
        centerX = cardX + widthPx / 2f,
        topY = cardY + cardHeightPx + gapPx,
        widthPx = widthPx,
        contentWidthPx = tabWidthPx * count + insetPx * 2f,
    )
}

/**
 * 底栏锚定的卡片位置（纯函数）。
 *
 * 底栏一旦在打开时冻结，卡片就只从它派生：**底边永远 = 底栏上沿 − 间距**，水平居中于底栏。
 * 切换面板时底栏不动，新面板向上长 / 向下缩，不会出现「卡盖住底栏」或「底栏悬空」。
 * 水平夹到屏幕边距内（卡宽大于可用宽度时只贴一侧，不缩宽）；垂直下界只防越顶，
 * 正常情况下高度上限已经保证放得下。
 */
fun resolveDockAnchoredCard(
    dockCenterX: Float,
    dockTopY: Float,
    cardWidthPx: Float,
    cardHeightPx: Float,
    screenWidthPx: Float,
    sideMarginPx: Float,
    gapPx: Float,
    safeTopPx: Float,
): SurfaceOffset {
    val maxX = (screenWidthPx - cardWidthPx).coerceAtLeast(0f)
    val minX = minOf(sideMarginPx, maxX)
    val maxY = dockTopY - gapPx
    return SurfaceOffset(
        x = (dockCenterX - cardWidthPx / 2f).coerceIn(minX, maxX),
        y = (maxY - cardHeightPx).coerceAtLeast(minOf(safeTopPx, maxY)),
    )
}
