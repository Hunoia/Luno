package hunoia.luno.ui.settings.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import hunoia.luno.R
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt

/**
 * 字体缩放缓层：滑杆只在松手时提交，拖动过程只改本地草稿，避免全局字体配置逐帧刷新。
 * 示例文本用 [Density] 覆盖到草稿字号，拖动时即可看到目标大小，不受当前系统字号影响。
 */
@Composable
internal fun FontScaleControls(
    current: Float,
    onCommit: (Float) -> Boolean,
) {
    var draft by remember(current) { mutableFloatStateOf(current) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .padding(PageGutter)
            .padding(bottom = ContentBottom),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        LunoGroup(title = stringResource(R.string.font_scale_sheet_group)) {
            LunoSegmentedGroup {
                item { shape -> LunoTextSlider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = {
                    // 连续滑杆按 1% 对齐，不画 149 个刻度。
                    val snapped = (draft * 100f).roundToInt() / 100f
                    draft = if (onCommit(snapped)) snapped else current
                },
                text = stringResource(R.string.font_scale_slider_label),
                valueDisplay = {
                    stringResource(R.string.font_scale_percent, (it * 100f).roundToInt())
                },
                valueRange = 0.5f..2.0f,
                shape = shape,
                ) }
                item { shape -> LunoSettingsRow(
                title = stringResource(R.string.font_scale_reset),
                shape = shape,
                onClick = {
                    draft = if (onCommit(1.0f)) 1.0f else current
                },
                ) }
            }
        }

        LunoGroup(
            title = stringResource(R.string.font_scale_sample_group),
            subtitle = stringResource(R.string.font_scale_sample_hint),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = segmentedShape(0, 1),
                color = MaterialTheme.colorScheme.surfaceBright,
            ) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, draft),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PageGutter, vertical = ListItemVerticalPadding),
                        verticalArrangement = Arrangement.spacedBy(ListItemVerticalPadding),
                    ) {
                        Text(
                            text = stringResource(R.string.font_scale_sample_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.font_scale_sample_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                }
            }
        }
    }
