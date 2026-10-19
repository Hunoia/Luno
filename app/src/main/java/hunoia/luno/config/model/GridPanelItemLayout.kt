package hunoia.luno.config.model

/**
 * 固定网格的落位求解。纯函数、无组合依赖 —— 拖拽预览和真正落盘都必须走同一条
 * 规则，否则会出现「看着放得下、落下却和别的项重叠」。
 *
 * 规则：**逐帧整页重排**（docs/grid-panel-moting-compare.md §11.3，一墨
 * `resolveReorderFlat`（defpackage/s09.java）的对齐版）：拖拽中每一帧把被拖项钉到
 * 指针格，任何被压住的项按方向优先级被推走、逐对碰撞收敛，**所有项（含被推项）
 * 一起返回新坐标**。推不动（有项出界 / 轮数上限）返回 null，调用方保持上一个
 * 有效目标 —— 手感上就是「靠近谁谁让位」，而不是只换一个格子。
 */
object GridPanelItemLayout {

    /** 首个能放下 [spanX] × [spanY] 的格子，按行优先。放不下返回 null。 */
    fun firstFreeSlot(
        items: List<GridPanelItem>,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
    ): GridCell? {
        val cols = columns.coerceAtLeast(1)
        val maxRows = rows.coerceAtLeast(1)
        val w = spanX.coerceIn(1, cols)
        val h = spanY.coerceIn(1, maxRows)
        for (y in 0..(maxRows - h)) {
            for (x in 0..(cols - w)) {
                if (isFree(items, x, y, w, h)) return GridCell(x, y)
            }
        }
        return null
    }

    /** [ignoreId] 自身的占位不算冲突（拖拽自己时当然和自己重叠）。 */
    fun isFree(
        items: List<GridPanelItem>,
        x: Int,
        y: Int,
        spanX: Int,
        spanY: Int,
        ignoreId: String? = null,
    ): Boolean {
        return items.none { item ->
            item.id != ignoreId && item.overlaps(x, y, spanX, spanY)
        }
    }

    /** 推让方向（一墨 s09 的 "UP/DOWN/LEFT/RIGHT" 字符串枚举的同义物）。 */
    private enum class Dir { LEFT, RIGHT, UP, DOWN }

    /**
     * 整页重排求解（拖拽预览与落盘共用，一墨 `resolveReorderFlat` 平面语义」：
     *
     * 1. 被拖项（[movingId]）钉到 clamp 后的 `(targetX, targetY)`；
     * 2. 逐对碰撞检测：一对重叠时决定「推谁」—— 一端已在已推集合则推另一端
     *    （保证连环推的方向单调），两端同状态则推 **y 更靠下**的那个（一墨同款
     *    行偏置，整体语义 = 往下/往右侧推 detached 少的）；
     * 3. 方向优先级 = 远离推点的轴优先（|dy|>=|dx| 时 DOWN/UP 优先），次序
     *    `[主方向, 次轴正方向, 次轴反方向, 逆主方向]`；每个候选先探位
     *    （放过去不与别人再撞才接受），全部不可用则**强制往下推一格**兜底；
     * 4. 任何项出界或超过轮数上限仍不收敛 → null，调用方维持上一个有效目标。
     *
     * 返回与 [items] 同序的完整列表（含被拖项与其余项的新坐标），落盘/渲染直接可用。
     */
    fun planReorder(
        items: List<GridPanelItem>,
        movingId: String,
        targetX: Int,
        targetY: Int,
        columns: Int,
        rows: Int,
    ): List<GridPanelItem>? {
        val moving = items.firstOrNull { it.id == movingId } ?: return null
        val cols = columns.coerceAtLeast(1)
        val maxRows = rows.coerceAtLeast(1)
        val tx = targetX.coerceIn(0, (cols - moving.spanX).coerceAtLeast(0))
        val ty = targetY.coerceIn(0, (maxRows - moving.spanY).coerceAtLeast(0))

        val ids = items.map { it.id }
        // 遍历顺序必须确定：收敛靠[pushed] 的「本轮已挪过」语义防震荡，
        // 顺序一变就是另一套结果（一墨的 DAO 没有 ORDER BY，靠插入顺序，
        // 那是个隐患 —— 这里显式排成 (y, x)，让级联方向符合直觉且可复现）。
        val ordered = items.sortedWith(compareBy({ it.y }, { it.x })).map { it.id }
        val spanW = items.associate { it.id to it.spanX }
        val spanH = items.associate { it.id to it.spanY }
        val pos = HashMap<String, Pair<Int, Int>>().apply {
            items.forEach { put(it.id, it.x to it.y) }
            put(movingId, tx to ty)
        }
        val pushed = mutableSetOf(movingId)

        /** 候选位是否与其它项碰撞（一墨 s09.v() 的语义）。 */
        fun collides(id: String, x: Int, y: Int): Boolean = ids.any { other ->
            other != id && run {
                val oy = pos[other]!!
                    val ospW = spanW.getValue(other)
                val ospH = spanH.getValue(other)
                x < oy.first + ospW && oy.first < x + spanW.getValue(id) &&
                    y < oy.second + ospH && oy.second < y + spanH.getValue(id)
            }
        }

        var loops = 0
        while (true) {
            loops++
            if (loops > 100) return null // 一墨上限 200 轮；Luno 网格小，100 裕量足够
            var changed = false
            for (i in ordered.indices) {
                val aId = ordered[i]
                for (j in i + 1 until ordered.size) {
                    val bId = ordered[j]
                    val pa = pos.getValue(aId)
                    val pb = pos.getValue(bId)
                    val aW = spanW.getValue(aId); val aH = spanH.getValue(aId)
                    val bW = spanW.getValue(bId); val bH = spanH.getValue(bId)
                    val overlaps = pa.first < pb.first + bW && pb.first < pa.first + aW &&
                        pa.second < pb.second + bH && pb.second < pa.second + aH
                    if (!overlaps) continue

                    // 推谁（一墨同款）：一端已推 → 推另一端；都在/都不在 → 推 y 大者。
                    val pusher: String
                    val pushee: String
                    when {
                        aId in pushed && bId !in pushed -> { pusher = aId; pushee = bId }
                        bId in pushed && aId !in pushed -> { pusher = bId; pushee = aId }
                        pa.second <= pb.second -> { pusher = aId; pushee = bId }
                        else -> { pusher = bId; pushee = aId }
                    }
                    val pp = pos.getValue(pusher)
                    val pe = pos.getValue(pushee)
                    val pw = spanW.getValue(pusher); val ph = spanH.getValue(pusher)
                    val sw = spanW.getValue(pushee); val sh = spanH.getValue(pushee)
                    val dx: Double
                    val dy: Double
                    if (pp == pe) {
                        // 完全重叠（数据兜底）：以被拖项目标为推点，把重叠项推离
                        dx = (tx - pe.first).toDouble()
                        dy = (ty - pe.second).toDouble()
                    } else {
                        dx = (pe.first + sw / 2.0) - (pp.first + pw / 2.0)
                        dy = (pe.second + sh / 2.0) - (pp.second + ph / 2.0)
                    }
                    // 方向优先级（一墨 s09 同序）：主轴 = 远离推点；|dy| 占优时行优先。
                    val order = when {
                        kotlin.math.abs(dy) >= kotlin.math.abs(dx) && dy >= 0 ->
                            listOf(Dir.DOWN, Dir.RIGHT, Dir.LEFT, Dir.UP)
                        kotlin.math.abs(dy) >= kotlin.math.abs(dx) ->
                            listOf(Dir.UP, Dir.RIGHT, Dir.LEFT, Dir.DOWN)
                        dx >= 0 -> listOf(Dir.RIGHT, Dir.DOWN, Dir.UP, Dir.LEFT)
                        else -> listOf(Dir.LEFT, Dir.DOWN, Dir.UP, Dir.RIGHT)
                    }
                    var placed = false
                    for (dir in order) {
                        val nx: Int
                        val ny: Int
                        when (dir) {
                            // 一墨：pushee 贴齐 pusher 对侧边缘（非仅一格）
                            Dir.RIGHT -> { nx = pp.first + pw; ny = pe.second; if (nx + sw > cols) continue }
                            Dir.LEFT -> { nx = pp.first - sw; ny = pe.second; if (nx < 0) continue }
                            Dir.DOWN -> { nx = pe.first; ny = pp.second + ph; if (ny + sh > maxRows) continue }
                            Dir.UP -> { nx = pe.first; ny = pp.second - sh; if (ny < 0) continue }
                        }
                        if (!collides(pushee, nx, ny)) {
                            pos[pushee] = nx to ny
                            pushed.add(pushee)
                            placed = true
                            changed = true
                            break
                        }
                    }
                    if (!placed) {
                        // 强制兜底：往下一格（一墨 fallback push），也不越界就收下；
                        // 越界就放弃这轮（轮数上限最终会判负整案）。
                        val ny = pp.second + ph
                        if (ny + sh <= maxRows) {
                            pos[pushee] = pe.first to ny
                            pushed.add(pushee)
                            changed = true
                        }
                    }
                }
            }
            if (!changed) break // 无对可推 = 已收敛
        }

        // 收敛后仍要全部在界内；一墨最后也要做同样的出界核查
        ids.forEach { id ->
            val (x, y) = pos.getValue(id)
            if (x < 0 || x + spanW.getValue(id) > cols || y < 0 || y + spanH.getValue(id) > maxRows) {
                return null
            }
        }
        return items.map { item ->
            val p = pos.getValue(item.id)
            if (p.first != item.x || p.second != item.y) item.copy(x = p.first, y = p.second) else item
        }
    }
}

/** 网格坐标（左上角格）。 */
data class GridCell(val x: Int, val y: Int)

/** 布局层用：给定矩形与任一现有项是否相交。 */
private fun GridPanelItem.overlaps(x: Int, y: Int, w: Int, h: Int): Boolean =
    this.x < x + w && x < this.x + this.spanX && this.y < y + h && y < this.y + this.spanY
