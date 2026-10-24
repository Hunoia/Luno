package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import hunoia.luno.gridpanel.geometry.IndicatorHeight

/**
 * 页指示条。槽位由 [pageDotSlots] 给出：页数超过窗口时只画当前页附近的一段，
 * 两端用省略号占位；拖拽命中的几何与这里完全共用（见 [pageDotCentersPx]）。
 */
@Composable
internal fun GridPageIndicator(
    slots: List<PageDotSlot>,
    pageCount: Int,
    activePage: Int,
    onSelectPage: (Int) -> Unit,
    /** 拖拽到页指示点上时高亮的目标页（可能 = pageCount 表示新建页）。 */
    pendingPage: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IndicatorHeight)
            .padding(horizontal = PageIndicatorPadding),
        horizontalArrangement = Arrangement.spacedBy(PageDotGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEach { slot ->
            when (slot) {
                is PageDotSlot.Ellipsis -> PageEllipsisDots(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(PageEllipsisSize),
                )

                is PageDotSlot.Page -> {
                    val index = slot.index
                    // 编辑态末尾的虚页（未建页）：空心加号样式，区别于实体页。
                    val isVirtual = index >= pageCount
                    val active = index == activePage
                    val pending = !active && index == pendingPage
                    if (isVirtual) {
                        Box(
                            Modifier
                                .size(PageVirtualDotSize)
                                .clip(RoundedCornerShape(50))
                                .border(
                                    width = if (pending) 2.dp else 1.5.dp,
                                    color = if (active || pending) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    shape = RoundedCornerShape(50),
                                )
                                .clickable { onSelectPage(index) },
                        )
                    } else {
                        Box(
                            Modifier
                                .size(PageRealDotSize)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when {
                                        active -> MaterialTheme.colorScheme.primary
                                        pending -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    },
                                )
                                // 目标页加一圈描边：落在当前页 / 相邻页的差别一眼能看出来。
                                .then(
                                    if (pending) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(50),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onSelectPage(index) },
                        )
                    }
                }
            }
        }
    }
}
