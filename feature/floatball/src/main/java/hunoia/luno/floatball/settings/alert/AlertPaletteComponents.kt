package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoSettingsRow

/** 色点直径（设置页调色板预览 / 逐色编辑共用）。 */
internal val PaletteDotSize = 18.dp

/**
 * 调色板缩略预览：非彩虹 = 一排色点（1–5 色），彩虹 = 一条色相环渐变条。
 * [heightFraction] 让内嵌预览在行里居中不撑高；独立预览页传 1f。
 */
@Composable
internal fun PalettePreview(
    palette: AlertPalette,
    modifier: Modifier = Modifier,
) {
    if (palette.rainbow) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835),
                                Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA),
                            ),
                        ),
                    ),
            ) {}
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
