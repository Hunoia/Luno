package hunoia.luno.ui.settings.action

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.config.model.WindowBounds
import hunoia.luno.config.model.withDropZone
import hunoia.luno.domain.tune.TuneConfig
import kotlinx.coroutines.launch

/**
 * 悬浮球判定区（拖入彩蛋）位置/大小调整页：交互与小窗调整页同一套
 * [BoundsAdjustCanvas]，按方向写回 [FloatBallSettings] 的判定区两份。
 * 「重置」回默认居中 1/3 × 1/4；区内画一颗真实球径的示意圆作大小参照。
 */
@Composable
fun DropZoneAdjustContent(
    initial: FloatBallSettings,
    onCommit: (landscape: Boolean, zone: WindowBounds) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoundsAdjustCanvas(
        initial = initial.dropZone(isLandscape),
        resetBounds = { FloatBallDefaults.defaultDropZone() },
        minSize = 0.15f,
        onCommit = { onCommit(isLandscape, it) },
        onCancel = onCancel,
        modifier = modifier,
        overlay = { rect ->
            // 判定看的是球心：区中心画一颗真实球径的示意圆，给大小与「入区」参照。
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = initial.sizeDp.dp.toPx() / 2f,
                center = Offset((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f),
            )
        },
    )
}

/** 动作设置页悬浮球「判定区调整」行的独立页面：自己读配置、自己落盘。 */
@Composable
fun DropZoneAdjustScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
    DropZoneAdjustContent(
        initial = actionSettings.floatBall,
        onCommit = { landscape, zone ->
            scope.launch {
                TuneConfig.updateAction { current ->
                    current.copy(floatBall = current.floatBall.withDropZone(landscape, zone))
                }
            }
            onBack()
        },
        onCancel = onBack,
    )
}
