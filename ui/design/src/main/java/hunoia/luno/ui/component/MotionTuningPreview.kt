package hunoia.luno.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.design.R
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.LocalMotionProfile
import hunoia.luno.ui.theme.MotionProfile
import hunoia.luno.ui.theme.MotionSpec
import hunoia.luno.ui.theme.floatingSpec
import hunoia.luno.ui.theme.overlaySpec
import hunoia.luno.ui.theme.panelSpec
import hunoia.luno.ui.theme.tween

/**
 * 动效档位预览（ui-guidelines §5.2）：三块小样分别用真实调用点的同一套 spec 播一遍。
 *
 * 不写全局档位：只在子树里 `provide` 预览 profile，点「重播」或任一档位变化时重放。
 */
@Composable
fun MotionTuningPreview(
    profile: MotionProfile,
    modifier: Modifier = Modifier,
) {
    var replay by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalMotionProfile provides profile) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(LargeShape)
                .background(MaterialTheme.colorScheme.surfaceBright)
                .padding(horizontal = CardInnerSpacing * 2, vertical = CardInnerSpacing * 2),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing * 2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.motion_preview_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                PressTextButton(onClick = { replay++ }) {
                    Text(stringResource(R.string.motion_preview_replay))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing * 2),
            ) {
                val panel = panelSpec()
                val overlay = overlaySpec()
                val floating = floatingSpec()
                MotionSample(
                    label = stringResource(R.string.motion_preview_panel),
                    spec = panel,
                    replay = replay,
                    initialScale = 0.25f,
                    initialAlpha = 0.3f,
                    modifier = Modifier.weight(1f),
                )
                MotionSample(
                    label = stringResource(R.string.motion_preview_overlay),
                    spec = overlay,
                    replay = replay,
                    initialScale = 1f,
                    initialAlpha = 0f,
                    modifier = Modifier.weight(1f),
                )
                MotionSample(
                    label = stringResource(R.string.motion_preview_floating),
                    spec = floating,
                    replay = replay,
                    initialScale = 0.85f,
                    initialAlpha = 0.4f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MotionSample(
    label: String,
    spec: MotionSpec,
    replay: Int,
    initialScale: Float,
    initialAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(spec, replay) {
        progress.snapTo(0f)
        progress.animateTo(1f, spec.tween())
    }
    val fraction = progress.value
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 40.dp)
                .graphicsLayer {
                    val scale = initialScale + (1f - initialScale) * fraction
                    scaleX = scale
                    scaleY = scale
                    alpha = initialAlpha + (1f - initialAlpha) * fraction
                    transformOrigin = TransformOrigin.Center
                }
                .clip(LargeShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
