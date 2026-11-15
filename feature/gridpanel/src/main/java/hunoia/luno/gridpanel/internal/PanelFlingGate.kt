package hunoia.luno.gridpanel.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * 只拦「惯性」的嵌套滚动闸门：部件内列表 fling 到边后，剩余位移/速度不再带动面板页。
 *
 * 手指直接拖到边界仍然按原行为把位移交给面板（`UserInput` 不拦）——用户要保留
 * 「列表在顶部继续滑还能滑面板」；只避免一次快速 fling 冲过列表顶部/底部后，面板
 * 又跟着滑一段。
 *
 * [scrollable] 在 fling 开始时求值：等 fling 结束再看 `canScroll*` 已经到边，
 * 会把「本来有内容可滚」误判成不可滚，闸门就失效了。
 */
internal class PanelFlingGate(
    private val scrollable: () -> Boolean,
) : NestedScrollConnection {

    private var consumeFling = false

    /**
     * 惯性是一帧一帧派发的：列表滚到边后，fling 的剩余位移会以 [NestedScrollSource.SideEffect]
     * 走 onPostScroll 继续往上传（不是等 fling 结束才一次性把速度交给面板）。
     * Compose 1.12 的 fling 用 SideEffect（旧版本叫 Fling）；手指拖动是 UserInput，
     * 仍然交给面板。
     */
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!consumeFling || source != NestedScrollSource.SideEffect) return Offset.Zero
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        consumeFling = scrollable()
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!consumeFling) return Velocity.Zero
        consumeFling = false
        // 返回 available = 这部分剩余速度由本节点吃掉，不再传给面板页。
        return available
    }
}
