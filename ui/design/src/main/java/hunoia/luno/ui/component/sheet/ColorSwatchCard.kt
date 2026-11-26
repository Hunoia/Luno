package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.design.R
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.CanvasStrokeWidth
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.trackSurfaceColor

/** 卡片色块高度：卡片主体，比旧版 36dp 方块大一圈。 */
private val SwatchBlockHeight = 56.dp

/** 选中角标直径。 */
private val SwatchCheckSize = 18.dp

/**
 * 调色板里的一个颜色卡：上部大色块 + 下部名称与色值。
 *
 * 三个 tab（标准 / 主题 / 提取）共用这一个组件，保证视觉一致。旧版是 36dp 纯色方块、
 * 零信息，用户只能靠猜；这里把名称和 hex 都摆出来。
 *
 * 选中态用两层表达：外框加粗成 `primary`，色块右上角叠对勾。对勾的描边色按色块自身
 * 亮度取黑或白——浅色块上白勾看不见，深色块上黑勾看不见。
 */
@Composable
internal fun ColorSwatchCard(
    color: Color,
    name: String,
    hex: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val selectedDesc = stringResource(R.string.color_picker_swatch_selected, name)
    val checkTint = if (color.luminance() > 0.5f) Color.Black else Color.White
    Column(
        modifier = modifier
            .clip(LargeShape)
            .background(
                if (selected) scheme.primaryContainer else trackSurfaceColor(),
            )
            .border(
                width = if (selected) CanvasStrokeWidth else CanvasStrokeWidth / 2,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = LargeShape,
            )
            .pressClickable(onClick = onClick, role = Role.RadioButton)
            .semantics {
                if (selected) contentDescription = selectedDesc
            }
            .padding(CardInnerSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SwatchBlockHeight)
                .clip(LargeShape)
                .background(color)
                // 色块自身也描一圈：纯白 / 纯黑色块在浅色卡片上否则没有边界。
                .border(CanvasStrokeWidth / 2, scheme.outlineVariant, LargeShape),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(SwatchCheckSize)
                        .clip(CircleShape)
                        .background(checkTint.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (checkTint == Color.White) Color.Black else Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(TileInnerPadding))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = hex,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                scheme.onPrimaryContainer.copy(alpha = 0.75f)
            } else {
                scheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 三列卡片网格。不用 `LazyVerticalGrid`：sheet 内容在 `verticalScroll` 里，
 * Lazy 网格嵌在无限高度约束下会崩，这里用 `chunked(3)` + `Row` 手工分行，
 * 与既有代码风格一致。
 */
@Composable
internal fun ColorSwatchGrid(
    count: Int,
    modifier: Modifier = Modifier,
    card: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        (0 until count).chunked(Columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                row.forEach { index ->
                    card(index, Modifier.weight(1f))
                }
                // 末行不满时补空位，避免最后几张卡被拉宽。
                if (row.size < Columns) {
                    repeat(Columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 卡片网格列数。 */
private const val Columns = 3
