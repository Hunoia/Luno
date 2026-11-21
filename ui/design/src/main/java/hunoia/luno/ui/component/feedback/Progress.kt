package hunoia.luno.ui.component.feedback

import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 加载 / 进度指示器的唯一出口（ui-guidelines §4.5）。
 *
 * M3 1.5 的 wavy 指示器是稳定 API，但只允许在 `ui:design` 组合：页面 / feature 直接用
 * M3 `*ProgressIndicator` 会被 `UiBoundaryTest` 拦截，避免又出现「一处圆环、一处波浪」。
 *
 * 颜色默认取主题 primary；需要跟容器走时显式传色，不在这里猜。
 */
@Composable
fun LunoLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularWavyProgressIndicator(modifier = modifier, color = color)
}

/**
 * 确定进度条：轨道常驻，进度按 [progress]（0f..1f）绘制。
 *
 * 高度由调用方给（波浪需要实际高度，建议用 `ProgressBarHeight` token）；不要塞进
 * 固定 2–3dp 的自绘线槽位，否则波幅无处可去。
 */
@Composable
fun LunoLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}
