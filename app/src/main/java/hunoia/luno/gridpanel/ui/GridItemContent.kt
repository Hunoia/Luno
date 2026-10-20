package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.action.model.StoredAction
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.widget.WidgetLoadState
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.R
import androidx.compose.ui.res.stringResource

/**
 * 动作格内容：**应用图标**（app.launch / 快捷方式等图片型）不铺底色、图标占满
 * 整格 —— 应用图标自带圆角/形状，垫方块反而难看；**普通动作**（矢量/文字图标）
 * 保留 surfaceVariant 底色、图标按短边 72% 留边。手势不在这里：统一由层根
 * gridLayerGestures 接管。
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
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(ItemCorner),
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

        else -> AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            imageLoader = LocalContext.current.imageLoader,
        )
    }
}

/** 数据残缺（理论上 saveGridPanelItem 不会写出这种项）。 */
@Composable
fun BrokenItemCell(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            MaterialTheme.colorScheme.errorContainer,
            RoundedCornerShape(ItemCorner),
        ),
    )
}

/**
 * 小部件的 Loading / Failed 底衬。Ready 不垫底色 —— provider 自带背景，
 * 垫一层会出现双层底 + 圆角对不齐。
 */
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
                RoundedCornerShape(ItemCorner),
            )
            .clip(RoundedCornerShape(ItemCorner)),
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

/**
 * 拖拽中的中心十字参考线。画在内容之下，不遮内容、也不抢点击
 * （Canvas 不参与命中测试）。
 */
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
