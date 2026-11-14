package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.widget.WidgetLoadState
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.gridpanel.R
import androidx.compose.ui.res.stringResource
import hunoia.luno.ui.theme.containerShape
import hunoia.luno.ui.theme.panelTileContainer
import hunoia.luno.ui.theme.AppIconDecodeSize

/**
 * 网格图标固定解码上限：面板拖拽/缩放只改布局尺寸，不再让 Coil 按每个像素尺寸
 * 重新生成请求。图标源最多按这个上限解码，再交给 Compose 缩放到实际格子大小。
 * 值统一走 [AppIconDecodeSize]，应用抽屉 / 快捷启动共用同一口径。
 */

/**
 * 动作格内容：应用图标（图片型）不铺底色、占满整格（自带圆角/形状）；
 * 普通动作保留 surfaceVariant 底、图标按短边 72% 留边。
 * 手势统一由层根 gridLayerGestures 接管。
 */
@Composable
fun ActionItemCell(
    action: StoredAction,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    cellSize: Dp = 60.dp,
) {
    val model = actionIcon(action)
    val isAppIcon = model != null && model !is ImageVector && model !is TextIcon
    Box(
        modifier = modifier
            .alpha(if (dragging) 0.9f else 1f)
            .background(
                color = if (isAppIcon) {
                    Color.Transparent
                } else {
                    // 与内置部件底、快捷启动键盘键同一块 token。
                    panelTileContainer()
                },
                shape = containerShape(ItemCorner),
            ),
        contentAlignment = Alignment.Center,
    ) {
        GridItemIcon(
            model = model,
            iconSize = if (isAppIcon) cellSize else cellSize * 0.72f,
        )
    }
}

@Composable
fun GridItemIcon(model: Any?, iconSize: Dp) {
    when (model) {
        is ImageVector -> Icon(
            imageVector = model,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = gridPanelOnCardColor,
        )

        is TextIcon -> TextIconGlyph(
            text = model.text,
            size = iconSize,
            color = gridPanelOnCardColor,
        )

        else -> {
            val context = LocalContext.current
            val density = LocalDensity.current
            val decodePx = with(density) { AppIconDecodeSize.roundToPx() }
            // 请求只随图标源变化，不随面板尺寸变化；crossfade 关闭：图标重新呈现时
            // 不再叠一层淡入，拖拽缩放期间不会逐帧闪烁。
            val request = remember(model, decodePx) {
                ImageRequest.Builder(context)
                    .data(model)
                    .size(decodePx)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                imageLoader = context.imageLoader,
            )
        }
    }
}

/** 数据残缺（理论上 saveGridPanelItem 不会写出这种项）。 */
@Composable
fun BrokenItemCell(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            MaterialTheme.colorScheme.errorContainer,
            containerShape(ItemCorner),
        ),
    )
}

/** 小部件的 Loading / Failed 底衬；Ready 不垫底色，否则会出现双层底 + 圆角对不齐。 */
@Composable
fun WidgetStateOverlay(state: WidgetLoadState, modifier: Modifier = Modifier) {
    if (state == WidgetLoadState.Ready) return
    val failed = state == WidgetLoadState.Failed
    Box(
        modifier
            .background(
                if (failed) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                containerShape(ItemCorner),
            )
            .clip(containerShape(ItemCorner)),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Text(
                text = stringResource(R.string.grid_panel_widget_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 拖拽中的中心十字参考线；画在内容之下，Canvas 不参与命中测试。 */
@Composable
fun DragCrosshair(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val arm = minOf(size.width, size.height) * 0.18f
        val hair = 1.dp.toPx()
        drawLine(color, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = hair)
        drawLine(color, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = hair)
    }
}
