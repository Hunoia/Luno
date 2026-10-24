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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import hunoia.luno.gridpanel.geometry.IndicatorHeight

@Composable
fun GridPageIndicator(
    pageCount: Int,
    activePage: Int,
    onSelectPage: (Int) -> Unit,
    /** 编辑态末尾的虚页（未建页）：空心加号样式，区别于实体页。 */
    virtualLast: Boolean = false,
    /** 拖拽贴边时松手会挪到的目标页（可能 = pageCount 表示新建页）：画成高亮环。 */
    pendingPage: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IndicatorHeight)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount + if (virtualLast) 1 else 0) { index ->
            if (index > 0) {
                Box(Modifier.width(6.dp))
            }
            val isVirtual = virtualLast && index == pageCount
            val active = index == activePage
            val pending = !active && index == pendingPage
            if (isVirtual) {
                Box(
                    Modifier
                        .size(9.dp)
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
                        .size(7.dp)
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
