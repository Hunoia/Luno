package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 右下角保存/新增 FAB：64dp、无点击 ripple、避让导航栏，全 app 的编辑页与列表页共用一套观感。
 * 内部自带 fillMaxSize Box，LunoScreen（BoxScope 槽）与 LunoScreenLazy（普通槽）都能直接放。
 */
@Composable
fun LunoFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(64.dp),
                shape = FloatingActionButtonDefaults.shape,
                containerColor = containerColor,
            ) {
                Icon(imageVector = icon, contentDescription = contentDescription)
            }
        }
    }
}
