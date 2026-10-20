package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.background
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

/**
 * 多页时的页指示点。原先的顶栏（日期/时间、编辑中提示、加项按钮）已整个删除：
 * 时间在状态栏里有；编辑态的加项入口改在卡片右下角 FAB（GridPanelCard）。
 */
@Composable
fun GridPageIndicator(
    pageCount: Int,
    activePage: Int,
    onSelectPage: (Int) -> Unit,
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
        repeat(pageCount) { index ->
            if (index > 0) {
                Box(Modifier.width(6.dp))
            }
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (index == activePage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    )
                    .clickable { onSelectPage(index) },
            )
        }
    }
}
