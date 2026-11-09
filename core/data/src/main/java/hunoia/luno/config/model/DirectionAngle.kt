package hunoia.luno.config.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * 方向角度布局的通用外壳：具体配置类型只保留 `@Serializable` 的 `boundaries` 形状，
 * 算法统一委托到本接口，避免 [GestureButtonAngle] / [SubGestureAngle] 各抄一份。
 */
interface DirectionLayout {
    val boundaries: List<Float>

    val count: Int get() = boundaries.size

    fun directionOf(offset: Offset): Int = directionSlotOf(boundaries, offset)

    fun slotWidthDegrees(index: Int): Float = sectorWidthDegrees(boundaries, index)

    fun slotCenterDegrees(index: Int): Float = sectorCenterDegrees(boundaries, index)

    fun zeroWidthSlots(): Set<Int> = zeroWidthSlots(boundaries)

    /** 改方向数后的边界；数量不变时返回原列表（调用方据此返回自身）。 */
    fun boundariesWithCount(count: Int): List<Float> {
        if (count == boundaries.size) return boundaries
        val anchor = sectorCenterAngle(boundaries, 0)
        return evenBoundaries(count, anchor - 0.5f / count)
    }
}

/**
 * 方向角度布局的公共数学：扇区数量 1..8，角度归一化到 [0,1)，0 = 正右、逆时针为正。
 *
 * 布局是一串**扇区起点**：槽 i 覆盖 [boundaries[i], boundaries[i+1])；
 * 槽的身份只有序号，显示用的名称与图标由 [sectorCenterDegrees] 派生。
 * 允许相邻边界重合（零宽扇区），语义是「这个方向暂时关掉」。
 */
object DirectionLayoutDefaults {
    const val MinCount = 1
    const val MaxCount = 8

    /** 默认 4 等分的起点：45°（槽 0 覆盖 45°..135°，即屏幕正上方）。 */
    const val DefaultStart = 0.125f

    val DefaultBoundaries: List<Float> = evenBoundaries(4, DefaultStart)

    /**
     * 该方向数的「罗盘对齐」默认布局：槽 0 中心固定在正上方（90°）。
     * 4 向时等价于 [DefaultStart]；8 向时中心正好落在八向罗盘上。
     */
    fun defaultBoundaries(count: Int): List<Float> {
        val n = count.coerceIn(MinCount, MaxCount)
        return evenBoundaries(n, 0.25f - 0.5f / n)
    }
}

fun normalizeUnit(value: Float): Float = ((value % 1f) + 1f) % 1f

fun validateDirectionBoundaries(boundaries: List<Float>) {
    require(boundaries.size in DirectionLayoutDefaults.MinCount..DirectionLayoutDefaults.MaxCount) {
        "direction count must be within ${DirectionLayoutDefaults.MinCount}..${DirectionLayoutDefaults.MaxCount}"
    }
    require(boundaries.all { it >= 0f && it < 1f })
}

/** N 等分圆周；[start] 是槽 0 的起点（保留整体旋转用）。 */
fun evenBoundaries(count: Int, start: Float = DirectionLayoutDefaults.DefaultStart): List<Float> {
    val n = count.coerceIn(DirectionLayoutDefaults.MinCount, DirectionLayoutDefaults.MaxCount)
    val s = normalizeUnit(start)
    return List(n) { index -> normalizeUnit(s + index.toFloat() / n) }
}

/** 命中扇区序号。N=1 时不区分方向，恒返回 0。 */
fun directionSlotOf(boundaries: List<Float>, offset: Offset): Int {
    if (boundaries.size <= 1) return 0
    val norm = normalizedAngle(offset)
    for (i in boundaries.indices) {
        if (isInSector(boundaries, i, norm)) return i
    }
    // 全是零宽扇区（或极端浮点边界）：退化为「离起点顺时针最近」的非零扇区。
    val candidates = boundaries.indices.filter { !isZeroWidthSector(boundaries, it) }
        .ifEmpty { boundaries.indices.toList() }
    return candidates.minByOrNull { clockwiseDistanceFromStart(boundaries, it, norm) } ?: 0
}

/** 扇区宽度（度）。N=1 恒为 360。 */
fun sectorWidthDegrees(boundaries: List<Float>, index: Int): Float {
    if (boundaries.size <= 1) return 360f
    val start = boundaries[index]
    val end = boundaries[(index + 1) % boundaries.size]
    val width = if (end >= start) end - start else end + 1f - start
    return width * 360f
}

/** 扇区中心角（归一化 [0,1)）。 */
fun sectorCenterAngle(boundaries: List<Float>, index: Int): Float =
    normalizeUnit(boundaries[index] + sectorWidthDegrees(boundaries, index) / 720f)

/** 扇区中心角（度，[0,360)）。 */
fun sectorCenterDegrees(boundaries: List<Float>, index: Int): Float =
    sectorCenterAngle(boundaries, index) * 360f

fun zeroWidthSlots(boundaries: List<Float>): Set<Int> =
    boundaries.indices.filter { isZeroWidthSector(boundaries, it) }.toSet()

internal fun isZeroWidthSector(boundaries: List<Float>, index: Int): Boolean =
    boundaries.size > 1 && sectorWidthDegrees(boundaries, index) == 0f

private fun isInSector(boundaries: List<Float>, index: Int, norm: Float): Boolean {
    val start = boundaries[index]
    val end = boundaries[(index + 1) % boundaries.size]
    return if (start <= end) norm >= start && norm < end else norm >= start || norm < end
}

private fun clockwiseDistanceFromStart(boundaries: List<Float>, index: Int, norm: Float): Float {
    val d = norm - boundaries[index]
    return if (d < 0f) d + 1f else d
}

/** 归一化角度（[0,1)，0 = 正右、逆时针为正）：[center] 为旋转中心，缺省屏幕原点。 */
fun normalizedAngle(offset: Offset): Float = normalizedAngle(offset, Offset.Zero)

fun normalizedAngle(offset: Offset, center: Offset): Float {
    val angle = atan2(-(offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())
    var norm = (angle / (2 * PI)).toFloat()
    if (norm < 0f) norm += 1f
    return norm
}

/**
 * 拖动一个扇区起点：N>2 时夹在相邻边界之间（保持环序），N<=2 时自由旋转。
 * 允许出现零宽扇区（等价于关闭该方向）。
 */
fun copyDirectionAngleBoundary(boundaries: List<Float>, index: Int, newBoundary: Float): List<Float> {
    val boundary = normalizeUnit(newBoundary)
    val n = boundaries.size
    if (n <= 2) {
        return boundaries.toMutableList().apply { set(index, boundary) }
    }
    val previous = boundaries[(index + n - 1) % n]
    val next = boundaries[(index + 1) % n]
    val distToPrevious = minOf(abs(boundary - previous), abs(boundary + 1f - previous), abs(boundary - 1f - previous))
    val distToNext = minOf(abs(boundary - next), abs(boundary + 1f - next), abs(boundary - 1f - next))
    val inRange = if (previous < next) boundary in previous..next else boundary >= previous || boundary <= next
    val clamped = if (inRange) boundary else if (distToPrevious < distToNext) previous else next
    return boundaries.toMutableList().apply { set(index, clamped) }
}

/**
 * 水平镜像整盘角度（θ → 180° − θ），返回「新 boundaries」与「新槽 = 旧槽的映射」。
 *
 * 扇区宽度不变、顺序按镜像后的角度重排，因此调用方要同时按映射重排动作
 * （`DirectionActions.remapSlots { old -> newSlotOfOld[old] }`）。
 */
fun mirrorDirectionBoundaries(boundaries: List<Float>): Pair<List<Float>, IntArray> {
    val n = boundaries.size
    if (n <= 1) return boundaries to IntArray(n) { 0 }
    val mirroredCenters = List(n) { normalizeUnit(0.5f - sectorCenterAngle(boundaries, it)) }
    val order = mirroredCenters.indices.sortedBy { mirroredCenters[it] }
    val newBoundaries = order.map { old ->
        normalizeUnit(mirroredCenters[old] - sectorWidthDegrees(boundaries, old) / 720f)
    }
    val newSlotOfOld = IntArray(n)
    order.forEachIndexed { newSlot, old -> newSlotOfOld[old] = newSlot }
    return newBoundaries to newSlotOfOld
}
