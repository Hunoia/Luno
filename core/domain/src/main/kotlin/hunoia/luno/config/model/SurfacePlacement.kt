package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * 浮层跟随触发点的三个边距（dp）：左右边距、上间距、下间距。
 *
 * 网格面板 / 快捷启动 / 小窗各自、各自朝向各存一份（挂在对应功能的几何或小窗设置里），
 * 定位算法统一走 [resolveTopLeft]，避免三处各写一套 clamp。
 */
@Serializable
@Keep
data class SurfacePlacement(
    val sideMarginDp: Float = SurfacePlacementDefaults.MarginDp,
    val topGapDp: Float = SurfacePlacementDefaults.MarginDp,
    val bottomGapDp: Float = SurfacePlacementDefaults.MarginDp,
)

object SurfacePlacementDefaults {
    const val MarginDp = 12f
    const val MinMarginDp = 0f
    const val MaxMarginDp = 60f
}

/** 浮层左上角（屏幕绝对坐标，px）。 */
data class SurfaceOffset(val x: Float, val y: Float)

/**
 * 触发点定位：横向贴触发点所在侧（左右边距），竖向跟随触发点并满足对应侧间距。
 *
 * - 横向：触发点在屏幕左半 → 贴左边距；右半 → 贴右边距。对侧间距不参与；
 *   面板比可用宽度还宽时只缩触发侧间距，左右缘始终不越屏。
 * - 竖向：按触发点在上/下半屏只取上/下间距，[verticalAnchorFraction] 决定触发点
 *   落在可见高度的哪个位置（面板 1/4，小窗 1/2 = 中心）。对侧间距不参与。
 * - 放不下时依次让位：当前侧间距 → 对侧安全区 → 当前侧安全区。
 * - 安全区由调用方传入屏幕绝对坐标的可见状态栏 / 导航栏高度（全屏无系统栏时为 0）。
 * - [widthPx]/[heightPx] 必须是**可见尺寸**；MIUI freeform 等帧尺寸补偿由调用方处理。
 */
fun SurfacePlacement.resolveTopLeft(
    anchorX: Int,
    anchorY: Int,
    widthPx: Float,
    heightPx: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
    safeTopPx: Int = 0,
    safeBottomPx: Int = 0,
    density: Float = 1f,
    verticalAnchorFraction: Float = 0.25f,
): SurfaceOffset {
    val sidePx = sideMarginDp.coerceIn(
        SurfacePlacementDefaults.MinMarginDp,
        SurfacePlacementDefaults.MaxMarginDp,
    ) * density
    val topPx = topGapDp.coerceIn(
        SurfacePlacementDefaults.MinMarginDp,
        SurfacePlacementDefaults.MaxMarginDp,
    ) * density
    val bottomPx = bottomGapDp.coerceIn(
        SurfacePlacementDefaults.MinMarginDp,
        SurfacePlacementDefaults.MaxMarginDp,
    ) * density

    val maxX = (screenWidthPx - widthPx).coerceAtLeast(0f)
    val x = if (anchorX * 2 < screenWidthPx) {
        sidePx.coerceIn(0f, maxX)
    } else {
        (screenWidthPx - widthPx - sidePx).coerceIn(0f, maxX)
    }

    val targetY = anchorY - heightPx * verticalAnchorFraction.coerceIn(0f, 1f)
    val screenH = screenHeightPx.toFloat()
    val topSafe = safeTopPx.toFloat()
    val y = if (anchorY * 2 < screenHeightPx) {
        // 上半屏：只用上间距，底部间距不参与；对侧底部安全区能避就避。
        var lo = topSafe + topPx
        var hi = screenH - safeBottomPx - heightPx
        if (lo > hi) lo = topSafe
        if (lo > hi) hi = screenH - heightPx
        if (lo > hi) lo = 0f
        if (lo > hi) {
            lo = 0f
            hi = (screenH - heightPx).coerceAtLeast(0f)
        }
        targetY.coerceIn(lo, hi)
    } else {
        // 下半屏：只用下间距，顶部间距不参与；对侧顶部安全区能避就避。
        var lo = topSafe
        var hi = screenH - safeBottomPx - heightPx - bottomPx
        if (lo > hi) hi = screenH - safeBottomPx - heightPx
        if (lo > hi) lo = 0f
        if (lo > hi) hi = screenH - heightPx
        if (lo > hi) {
            lo = 0f
            hi = (screenH - heightPx).coerceAtLeast(0f)
        }
        targetY.coerceIn(lo, hi)
    }
    return SurfaceOffset(x = x, y = y)
}

/** 边距统一夹到可调范围（设置页数量级，防止导入的旧数据越界）。 */
fun SurfacePlacement.coerced(): SurfacePlacement = SurfacePlacement(
    sideMarginDp = sideMarginDp.coerceIn(
        SurfacePlacementDefaults.MinMarginDp,
        SurfacePlacementDefaults.MaxMarginDp,
    ),
    topGapDp = topGapDp.coerceIn(
        SurfacePlacementDefaults.MinMarginDp,
        SurfacePlacementDefaults.MaxMarginDp,
    ),
    bottomGapDp = bottomGapDp.coerceIn(
        SurfacePlacementDefaults.MinMarginDp,
        SurfacePlacementDefaults.MaxMarginDp,
    ),
)

/** 滑杆量化：按 1dp 落盘。 */
fun Float.placementStep(): Float = roundToInt().toFloat()
