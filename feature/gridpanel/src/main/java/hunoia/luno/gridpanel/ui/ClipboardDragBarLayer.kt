package hunoia.luno.gridpanel.ui

import android.view.WindowManager
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import hunoia.luno.bridge.ui.SystemBarsSafeArea
import hunoia.luno.bridge.ui.systemBarsSafeAreaOrNull
import hunoia.luno.gridpanel.GridPanelDragGhost
import hunoia.luno.ui.component.overlay.EdgeActionBar
import hunoia.luno.ui.overlay.EdgeBarLayout

/**
 * 剪贴板部件拖拽动作条：手指拖到屏幕边缘（带宽内）时浮出一排槽位。
 *
 * 外观 / 渲染 / 命中全部走 `:ui:design` 的 [EdgeActionBar] + [EdgeBarLayout]
 * （与识屏工具条同一份实现）。本层只做两件事：把根层的屏幕几何写回幽灵、把当前槽位
 * 喂给渲染。不吃触摸 —— 拖拽手势在条目的 `itemDrag` 上，
 * [GridPanelDragGhost.resolveDrag] 用同一份几何结算，渲染与判定不分叉。
 */
@Composable
fun ClipboardDragBarLayer(ghost: GridPanelDragGhost, modifier: Modifier = Modifier) {
    if (!ghost.dragActive) return
    val density = LocalDensity.current
    val context = LocalContext.current
    // 系统栏避让：读一次（拖拽期间屏幕配置不变），条体与沿边范围都要让开。
    val insets = remember {
        context.getSystemService(WindowManager::class.java)
            ?.systemBarsSafeAreaOrNull() ?: SystemBarsSafeArea.None
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sizePx = IntSize(
            with(density) { maxWidth.roundToPx() },
            with(density) { maxHeight.roundToPx() },
        )
        // 根层把屏幕尺寸 / 密度 / 系统栏避让量写回幽灵：条体计算（moveDrag）与渲染同源。
        // 写入即重算条体——首次拖拽时 beginDrag 早于本层组合，不重算就要等下一次移动才浮出。
        SideEffect {
            ghost.setDragScreenMetrics(
                size = sizePx,
                density = density.density,
                insets = EdgeBarLayout.Insets(insets.top.toFloat(), insets.bottom.toFloat()),
            )
        }
        val bar = ghost.dragBar ?: return@BoxWithConstraints
        EdgeActionBar(
            bar = bar,
            slots = ghost.dragSlots,
            activeSlot = EdgeBarLayout.hit(bar, ghost.dragScreenPos) ?: -1,
            screenWidthPx = sizePx.width.toFloat(),
            screenHeightPx = sizePx.height.toFloat(),
        )
    }
}
