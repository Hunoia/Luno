package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.BuiltinAlertPalettes
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.PaletteDotSize
import hunoia.luno.ui.theme.PaletteDotSpacing
import hunoia.luno.ui.theme.PalettePreviewHeight

/**
 * 调色板缩略预览：非彩虹 = 一排色点（1–5 色），彩虹 = 一条色相环渐变条。
 *
 * 彩虹条与渲染端共用 `BuiltinAlertPalettes.rainbowColors`（首色在末尾再放一次让渐变
 * 首尾相接），预览与实际播放同色；位置不受字体缩放影响，是固定尺寸的色块。
 */
@Composable
internal fun PalettePreview(
    palette: AlertPalette,
    modifier: Modifier = Modifier,
) {
    if (palette.rainbow) {
        val ring = BuiltinAlertPalettes.rainbowColors.map { Color(it) }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(PalettePreviewHeight)
                .clip(CapsuleShape)
                .background(
                    brush = Brush.horizontalGradient(ring + ring.first()),
                ),
        ) {}
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(PaletteDotSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            palette.colors.take(5).forEach { argb ->
                Row(
                    modifier = Modifier
                        .size(PaletteDotSize)
                        .clip(CircleShape)
                        .background(Color(argb)),
                ) {}
            }
        }
    }
}

/** 单色色点（「跟随动效颜色」预览用）。 */
@Composable
internal fun SingleColorDot(color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .size(PaletteDotSize)
            .clip(CircleShape)
            .background(color),
    ) {}
}

/**
 * 分类设置页的「调色板」行：缩略预览（无调色板 = 单色回退）+ 当前名 + 点进选择页。
 * [fallbackColor] 是未选调色板时的单色（主题解析后），由调用页传入。
 */
@Composable
internal fun AlertPaletteSettingsRow(
    palette: AlertPalette?,
    fallbackColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    LunoSettingsRow(
        title = stringResource(R.string.float_ball_capsule_alert_palette),
        subtitle = palette?.name
            ?: stringResource(R.string.float_ball_capsule_alert_palette_none),
        shape = shape,
        leading = {
            if (palette != null) {
                PalettePreview(palette)
            } else {
                SingleColorDot(fallbackColor)
            }
        },
        onClick = onClick,
    )
}
