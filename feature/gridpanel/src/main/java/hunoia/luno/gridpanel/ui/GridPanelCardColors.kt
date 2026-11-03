package hunoia.luno.gridpanel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 面板卡片底色：跟随主题的浮层色。
 *
 * 模糊可用时由卡片按 [cardAlpha] 透出背景；模糊不可用时卡片会提高不透明度兜底，
 * 不再需要固定近黑来保证亮色模式可读。
 */
val gridPanelCardColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceContainerHigh

/** 卡片上的主内容色（图标、文字、手柄）。 */
val gridPanelOnCardColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface
