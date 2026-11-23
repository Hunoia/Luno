package hunoia.luno.ui.component.row

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * 单个 `LazyColumn` item 承载的行数上限。
 *
 * 整表塞进单个 item 时，该 item 承载全部行，节点高度越过设备合成上限（真机实测 ≈8192px：
 * 34 行 ≈8058px 在限内、35 行 ≈8295px 越界），越界部分**既不可见也不可点**，且截断点在
 * item 内坐标、与屏幕位置无关——表象像「最后几行点不了 / 滚到哪都不行」，真因不是遮挡。
 *
 * 12 行 × 最坏 682px/行 ≈8184px，留余量；约 1.2 屏，惰性窗口仍足够紧凑。
 */
const val MaxRowsPerLazyItem = 12

/**
 * 长列表兜底：把 [items] 按 [MaxRowsPerLazyItem] 自动分块，每块发一个 item。
 *
 * `LazyColumn` 的惰性窗口按 item 粒度生效，单个 item 只会被整体组合 + 合成。分块后每个
 * item 只承载少量行，节点高度远低于合成上限，组合与滚动都回到块粒度。
 *
 * [chunkContent] 收到块号与该块的行列表；分段圆角用 `segmentedShape(i, chunk.size)`，
 * 每块成一张完整卡片（块间留 `ListSpacing` 间距）。跨块要连通时无用本助手，改按语义分节。
 * 第一参数是**块号**不是全表下标：需要全表下标时（如拖拽换位）自行换算
 * `chunkIndex * MaxRowsPerLazyItem + 块内下标`。
 */
fun <T> LazyListScope.chunkedItems(
    items: List<T>,
    keyPrefix: String,
    chunkContent: @Composable LazyItemScope.(chunkIndex: Int, chunk: List<T>) -> Unit,
) {
    items.chunked(MaxRowsPerLazyItem).forEachIndexed { chunkIndex, chunk ->
        item(key = "${keyPrefix}_$chunkIndex") {
            chunkContent(chunkIndex, chunk)
        }
    }
}

/**
 * 长列表分块 + 组卡一站式助手：把 [items] 自动分块，每块一个 `item` 一张 [LunoGroup] 完整卡片，
 * 页面无需再关心块号 / 圆角 / 标题（ui-guidelines §3.2 第 10 条）。
 *
 * - [title] / [subtitle] 只在**首块**显示（其余块空字符串，卡片连通成列但标题不重复）。
 * - 每行收到组件算好的 [Shape]（块内分段圆角 `segmentedShape(块内下标, chunk.size)`）与
 *   [globalIndex]（全表下标 `块号 × MaxRowsPerLazyItem + 块内下标`，拖拽换位 / 首位标记用）。
 * - [items] 为空且给了 [emptyContent]：发一个 `item("${keyPrefix}_empty")` 承载整段空态内容
 *   （页面自定，含 / 不含组标题均可）；不给则不发 item，由页面自行处理空态。
 *
 * 需要拖拽换位等**全表语义**时用 [globalIndex]；只按块内排布的（如分段圆角）直接用 [Shape]。
 */
fun <T> LazyListScope.chunkedGroup(
    items: List<T>,
    keyPrefix: String,
    title: @Composable () -> String? = { null },
    subtitle: @Composable () -> String? = { null },
    emptyContent: (@Composable () -> Unit)? = null,
    rowContent: @Composable LazyItemScope.(shape: Shape, globalIndex: Int, item: T) -> Unit,
) {
    if (items.isEmpty()) {
        if (emptyContent != null) {
            item(key = "${keyPrefix}_empty") { emptyContent() }
        }
        return
    }
    chunkedItems(items, keyPrefix) { chunkIndex, chunk ->
        LunoGroup(
            title = if (chunkIndex == 0) title().orEmpty() else "",
            subtitle = if (chunkIndex == 0) subtitle().orEmpty() else "",
        ) {
            chunk.forEachIndexed { blockIndex, item ->
                rowContent(
                    segmentedShape(blockIndex, chunk.size),
                    chunkIndex * MaxRowsPerLazyItem + blockIndex,
                    item,
                )
            }
        }
    }
}