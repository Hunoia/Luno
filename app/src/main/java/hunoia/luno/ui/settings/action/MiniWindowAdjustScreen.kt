package hunoia.luno.ui.settings.action

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.config.model.withMiniWindowSettings
import hunoia.luno.domain.tune.TuneConfig
import kotlinx.coroutines.launch

/**
 * 小窗位置/大小的调整界面（薄壳）：按方向切片 / 回写小窗配置域，
 * 画布与拖拽交互在 [BoundsAdjustCanvas]（与悬浮球判定区页共用）。
 * 独立页面而非全屏 overlay：纯配置任务，不需看见真实屏幕，页内画示意图即可。
 * 运行时按方向抵消 HyperOS 的 freeformScale（竖屏 0.7 / 横屏 0.56）——
 * 共享画布预览铺满整屏、比例 1:1，两边预览都等于实际。
 */
@Composable
fun MiniWindowAdjustContent(
    initial: MiniWindowSettings,
    onCommit: (MiniWindowSettings) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoundsAdjustCanvas(
        initial = initial.forOrientation(isLandscape),
        resetBounds = { MiniWindowSettings().forOrientation(isLandscape) },
        onCommit = { onCommit(initial.withOrientation(isLandscape, it)) },
        onCancel = onCancel,
        modifier = modifier,
    )
}

/**
 * 动作设置页「小窗」行的独立页面：自己读配置、自己落盘。
 * 整屏接管且无 TopBar，出口是顶部那排透明圆形按钮。
 */
@Composable
fun MiniWindowAdjustScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val advanced by TuneConfig.advanced.collectAsStateWithLifecycle(
        initialValue = TuneConfig.latestAdvanced()
    )
    MiniWindowAdjustContent(
        initial = advanced.miniWindowSettings(),
        onCommit = { next ->
            scope.launch { TuneConfig.updateAdvanced { it.withMiniWindowSettings(next) } }
            onBack()
        },
        onCancel = onBack,
    )
}
