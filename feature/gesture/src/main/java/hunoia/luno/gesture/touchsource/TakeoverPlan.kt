package hunoia.luno.gesture.touchsource

import kotlin.math.roundToInt

/**
 * 一个**可见窗口**的屏幕几何（无障碍窗口层级的纯值快照）。
 *
 * 为什么不用 `AccessibilityWindowInfo`：接管落点选择是纯几何，必须能在 JVM 单测里钉住。
 * 「长按菜单盖满全屏 → 第二段注定打空」这类结论只有用固定几何才复现得出来
 * （真机上它随应用 / ROM 版本变，抓一次日志就要凭运气）。
 */
data class WindowBox(
    val id: Int,
    val layer: Int,
    val type: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom

    /** 日志用：一个窗口的层级 / 类型 / id，足以判断「注入注给了谁」。 */
    fun describe(): String = "type=$type layer=$layer id=$id"

    companion object {
        /**
         * 与 `AccessibilityWindowInfo.TYPE_APPLICATION` 同值镜像（同值镜像的口径见 architecture §7.5）。
         *
         * 纯几何层不 import 平台类型：JVM 单测里 android.jar 是桩，引用平台常量会让这条
         * 「能不能算出落点」的规则没法单测。
         */
        const val TYPE_APPLICATION = 1
    }
}

/**
 * 一次接管注入的落点计划。
 *
 * @param pressWindow 按下点当前的顶层窗口
 * @param appWindow 按下点所在窗口里 layer 最低的**应用**窗口（真实流的归属概率最高）
 * @param safe 第二段落点：应用主窗口内、且顶层窗口就是它的点；`null` = 没有这样的点
 */
data class TakeoverPlan(
    val pressWindow: WindowBox,
    val appWindow: WindowBox,
    val safe: Pair<Int, Int>?,
) {
    /**
     * 按下点是否被别的窗口盖住（长按菜单 / 弹窗 / 面板）。
     *
     * 盖住时按下点的注入只会关掉那个覆盖层，取消不了主窗口那条真实流，才需要第二段。
     */
    val pressCovered: Boolean get() = pressWindow.id != appWindow.id

    /** 日志用：这一次注入到底打算打给谁。 */
    fun describe(): String =
        "press=${pressWindow.describe()} app=${appWindow.describe()} covered=$pressCovered"
}

/**
 * 算一次接管的注入落点。
 *
 * 为什么要按窗口层级算：注入走正常窗口命中测试，落在谁身上就影响谁的手势。长按菜单
 * （弹窗）常常正好盖在按下点上，于是按下点的注入只能关菜单、取消不了主窗口那条真实流；
 * 第二段因此必须换到「应用主窗口内、且没有更高层窗口盖住」的点。实测反例：联系人的长按
 * 菜单 `[16,82]-[1174,2658]` 几乎占满全屏，任何固定对角点都在菜单内 —— 第二段注定打空，
 * 却被记成「已接管成功」。所以这里按实际层级找点，找不到就返回 `null`，由调用方跳过第二段
 * 并如实记账（architecture §7.4「触发接管触摸」）。
 *
 * 落点只在窗口内缩 [insetFraction] 之后的网格上取，避免贴屏幕边缘（系统手势条 / 导航条不是
 * 无障碍窗口，缩进来才能保证不把手势打进系统手势区）。
 *
 * @param windows 当前可见窗口，顺序无关；同 layer 视为并列
 * @return `null` = 拿不到足够信息（按下点不在任何窗口内 / 没有应用窗口），调用方退回「只注入按下点」
 */
fun planTakeover(
    pressX: Int,
    pressY: Int,
    windows: List<WindowBox>,
    insetFraction: Float = 0.1f,
    gridSteps: Int = 4,
): TakeoverPlan? {
    if (windows.isEmpty()) return null
    val pressWindow = windows.filter { it.contains(pressX, pressY) }.maxByOrNull { it.layer } ?: return null
    val appWindow = windows
        .filter { it.type == WindowBox.TYPE_APPLICATION && it.contains(pressX, pressY) }
        .minByOrNull { it.layer } ?: return null
    if (appWindow.id == pressWindow.id) {
        // 按下点就在主窗口上：那一次注入命中的就是真实流的窗口，不需要第二段。
        return TakeoverPlan(pressWindow, appWindow, safe = null)
    }
    return TakeoverPlan(
        pressWindow = pressWindow,
        appWindow = appWindow,
        safe = farthestClearPoint(pressX, pressY, appWindow, windows, insetFraction, gridSteps),
    )
}

/** 应用主窗口内最远离按下点、且顶层窗口就是它的点；一个都没有（被菜单盖满）返回 `null`。 */
private fun farthestClearPoint(
    pressX: Int,
    pressY: Int,
    appWindow: WindowBox,
    windows: List<WindowBox>,
    insetFraction: Float,
    gridSteps: Int,
): Pair<Int, Int>? = farthestGridPoint(pressX, pressY, appWindow, insetFraction, gridSteps) { x, y ->
    // 只有「顶层窗口就是应用主窗口」的点才真的把事件交到主窗口手里。
    windows.filter { it.contains(x, y) }.maxByOrNull { it.layer }?.id == appWindow.id
}

/**
 * 第二段的**盲打**落点：应用主窗口内离按下点最远的网格点，不管当时有谁盖着。
 *
 * 只在「算不出清晰点」时用：长按菜单常常在阶段 1 之后才开始退出动画（还在窗口树里，
 * 但已经不再接受点击），此时按快照算永远是「被盖住」。盲打一个离菜单中心最远的点，
 * 在菜单已经收掉的情况下能打中主窗口；没打中也不会比单段更差。
 */
fun takeoverFallbackPoint(
    pressX: Int,
    pressY: Int,
    appWindow: WindowBox,
    insetFraction: Float = 0.1f,
    gridSteps: Int = 4,
): Pair<Int, Int>? = farthestGridPoint(pressX, pressY, appWindow, insetFraction, gridSteps) { _, _ -> true }

/** 在 [appWindow] 内缩后的网格上找离按下点最远、且 [accept] 认可的点。 */
private inline fun farthestGridPoint(
    pressX: Int,
    pressY: Int,
    appWindow: WindowBox,
    insetFraction: Float,
    gridSteps: Int,
    accept: (Int, Int) -> Boolean,
): Pair<Int, Int>? {
    val steps = gridSteps.coerceAtLeast(2)
    val insetX = (appWindow.width * insetFraction).roundToInt()
    val insetY = (appWindow.height * insetFraction).roundToInt()
    val left = appWindow.left + insetX
    val right = appWindow.right - insetX
    val top = appWindow.top + insetY
    val bottom = appWindow.bottom - insetY
    if (right <= left || bottom <= top) return null
    var best: Pair<Int, Int>? = null
    var bestDistance = -1L
    for (i in 0 until steps) {
        val x = left + (right - left) * i / (steps - 1)
        for (j in 0 until steps) {
            val y = top + (bottom - top) * j / (steps - 1)
            if (x == pressX && y == pressY) continue
            if (!accept(x, y)) continue
            val dx = (x - pressX).toLong()
            val dy = (y - pressY).toLong()
            val distance = dx * dx + dy * dy
            if (distance > bestDistance) {
                bestDistance = distance
                best = x to y
            }
        }
    }
    return best
}
