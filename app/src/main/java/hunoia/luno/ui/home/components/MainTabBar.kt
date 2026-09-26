package hunoia.luno.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.ExpressiveMotion

enum class MainTab(val label: String, val icon: ImageVector) {
    Home("主页", Icons.Default.Home),
    ActionLibrary("动作库", Icons.AutoMirrored.Filled.LibraryBooks),
}

@Composable
fun MainTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capsuleShape = RoundedCornerShape(24.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .clip(capsuleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .animateContentSize(
                            animationSpec = ExpressiveMotion.slowSpatialSpec()
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onTabSelected(tab) }
                        .padding(horizontal = if (isSelected) 22.dp else 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
