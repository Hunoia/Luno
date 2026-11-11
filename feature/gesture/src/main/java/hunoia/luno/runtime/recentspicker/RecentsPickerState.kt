package hunoia.luno.runtime.recentspicker

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import hunoia.luno.action.model.RecentsPickerLayout
import hunoia.luno.core.domain.RecentApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * 最近应用滚动选择器的会话状态：滚轮数据、排列方向、触点锚点与共享滚动状态。
 *
 * 由手势管线持有；按住类触发时管线把 dragAmount 喂进 [onDrag] 实时滚动，
 * 抬手后全屏 overlay 直接接管共享 [listState]。滚轮窗口与管线共用同一个
 * [listState]，保证「按住中途浮现 → 随手指滚动 → 松手预览 → 窗口内滚动/点选」
 * 全程一条滚动轨，不会出现两套滚动位置打架。
 */
class RecentsPickerState(
    private val scope: CoroutineScope,
) {
    var isActive by mutableStateOf(false)
        private set

    /** 可选的最近应用（旧→新，末尾最新，已过滤当前前台与黑名单）。 */
    var apps by mutableStateOf<List<RecentApp>>(emptyList())
        private set

    var layout by mutableStateOf(RecentsPickerLayout.VERTICAL)
        private set

    /** 触发锚点（屏幕 px）：滚轮中心落在此处，夹取屏内。 */
    var anchor by mutableStateOf(Offset.Zero)
        private set

    /**
     * 共享滚动状态：每次 [activate] 重建，初始居中「最新一条」（历史旧→新，末尾最新）。
     * 重建而非复用：上次会话可能停在别的项，复用会带出残留滚动位置。
     */
    var listState by mutableStateOf(LazyListState())
        private set

    private var pendingDelta = 0f
    private var scrollJob: Job? = null

    fun activate(apps: List<RecentApp>, layout: RecentsPickerLayout, anchor: Offset) {
        this.apps = apps
        this.layout = layout
        this.anchor = anchor
        isActive = true
        pendingDelta = 0f
        scrollJob?.cancel()
        scrollJob = null
        listState = LazyListState(firstVisibleItemIndex = apps.lastIndex.coerceAtLeast(0))
    }

    /** 按住阶段喂拖动：按排列方向取轴分量，滚轮实时跟随手指。 */
    fun onDrag(dragAmount: Offset) {
        if (!isActive) return
        val delta = when (layout) {
            RecentsPickerLayout.VERTICAL -> dragAmount.y
            RecentsPickerLayout.HORIZONTAL -> dragAmount.x
        }
        if (delta == 0f) return
        pendingDelta += delta
        if (scrollJob?.isActive != true) {
            scrollJob = scope.launch {
                while (pendingDelta != 0f) {
                    val d = pendingDelta
                    pendingDelta = 0f
                    // overlay 首帧布局未就绪时 scrollBy 可能还没法滚，跳过这一段，
                    // 后续拖动会重新累计；不抛崩管线。
                    runCatching { listState.scrollBy(d) }
                }
            }
        }
    }

    /** 松手：吸附到居中项，窗口保留为预览（不启动，等待点选）。 */
    fun onDragEnd() {
        if (!isActive) return
        pendingDelta = 0f
        scrollJob?.cancel()
        scrollJob = null
        scope.launch {
            val index = centeredItemIndex(listState)
            if (index != listState.firstVisibleItemIndex) {
                runCatching { listState.animateScrollToItem(index) }
            }
        }
    }

    /** 取消（来电/系统抢焦点）：重置会话，overlay 由协调器收口。 */
    fun onDragCancel() {
        isActive = false
        pendingDelta = 0f
        scrollJob?.cancel()
        scrollJob = null
        apps = emptyList()
    }

    fun clear() = onDragCancel()
}

/** 停在视口中心的那一项；布局未就绪时回落首项。纵向/横向共用（沿滚动轴取中心）。 */
internal fun centeredItemIndex(state: LazyListState): Int {
    val layout = state.layoutInfo
    if (layout.visibleItemsInfo.isEmpty()) return state.firstVisibleItemIndex
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    return layout.visibleItemsInfo
        .minByOrNull { (it.offset + it.size / 2f - center).absoluteValue }
        ?.index
        ?: state.firstVisibleItemIndex
}
