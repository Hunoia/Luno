package hunoia.luno.runtime.recentspicker

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import hunoia.luno.action.model.RecentsPickerLayout
import hunoia.luno.core.domain.RecentApp
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.icon.rememberAppDrawable
import hunoia.luno.ui.icon.rememberAppLabel
import kotlin.math.absoluteValue

/** 滚轮露出多少行（奇数，选中行居中），与时间滚轮一致。 */
internal const val RecentsWheelVisibleRows = 3

/** 图标边长。 */
private val RecentsWheelIconSize = 60.dp

/** 行内图标四周留白：行沿滚动轴尺寸 = 图标 + 两侧留白。 */
private val RecentsWheelRowPadding = 10.dp

/** 行沿滚动轴尺寸。 */
internal val RecentsWheelRowSize = RecentsWheelIconSize + RecentsWheelRowPadding * 2

/** 滚轮横截面尺寸：竖向时是滚轮宽度，横向时是滚轮高度。 */
internal val RecentsWheelCrossSize = 88.dp

/** 离中心每远一行衰减多少不透明度；再远也不低于 [RecentsWheelMinAlpha]。 */
private const val RecentsWheelFadePerRow = 0.35f
private const val RecentsWheelMinAlpha = 0.25f

/** 边缘行绕滚动轴倾斜的角度（度）：给出「转到背面」的滚轮观感。 */
private const val RecentsWheelRowTiltDegrees = 10f

/** 透视距离倍数：不设会退化成无透视的平面缩放。 */
private const val RecentsWheelCameraDistance = 18f

/** 中心（当前项）放大倍数：大于 1 表示当前项放大选中。 */
private const val RecentsWheelCenterScale = 1.08f

/** 离中心每远一行缩小多少；再远也不低于 [RecentsWheelMinScale]。 */
private const val RecentsWheelScaleDropPerRow = 0.14f
private const val RecentsWheelMinScale = 0.6f

/**
 * 最近应用滚轮：`LazyListState` 由外部持有（管线按住阶段喂、overlay 抬手后直操），
 * 本组件只渲染。仅图标行 + 离中心渐隐渐斜（同 [WheelColumn] 的滚轮观感），
 * 支持竖向（LazyColumn）与横向（LazyRow）两种排列。
 *
 * 交互契约：点某一项 = [onAppTap]；拖动 = 滚动，吸附居中。行外空白不消费触摸，
 * 由外层全屏 Box 接住当「取消」。
 */
@Composable
fun RecentsWheel(
    apps: List<RecentApp>,
    listState: LazyListState,
    layout: RecentsPickerLayout,
    onAppTap: (RecentApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return
    val rowSize = RecentsWheelRowSize
    val crossSize = RecentsWheelCrossSize
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    val wheelWidth: Dp = if (layout == RecentsPickerLayout.VERTICAL) crossSize else rowSize * RecentsWheelVisibleRows
    val wheelHeight: Dp = if (layout == RecentsPickerLayout.VERTICAL) rowSize * RecentsWheelVisibleRows else crossSize
    val contentPadding = if (layout == RecentsPickerLayout.VERTICAL) {
        PaddingValues(vertical = (wheelHeight - rowSize) / 2)
    } else {
        PaddingValues(horizontal = (wheelWidth - rowSize) / 2)
    }

    Box(
        modifier = modifier.size(width = wheelWidth, height = wheelHeight),
        contentAlignment = Alignment.Center,
    ) {
        // 无选中带背景：当前项由 [RecentsWheelRow] 的离中心放大/渐隐渐斜表达。
        if (layout == RecentsPickerLayout.VERTICAL) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                flingBehavior = fling,
                contentPadding = contentPadding,
            ) {
                itemsIndexed(apps, key = { _, app -> app.blockKey }) { index, app ->
                    RecentsWheelRow(
                        app = app,
                        index = index,
                        listState = listState,
                        layout = layout,
                        rowSize = rowSize,
                        crossSize = crossSize,
                        onAppTap = onAppTap,
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                flingBehavior = fling,
                contentPadding = contentPadding,
            ) {
                itemsIndexed(apps, key = { _, app -> app.blockKey }) { index, app ->
                    RecentsWheelRow(
                        app = app,
                        index = index,
                        listState = listState,
                        layout = layout,
                        rowSize = rowSize,
                        crossSize = crossSize,
                        onAppTap = onAppTap,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentsWheelRow(
    app: RecentApp,
    index: Int,
    listState: LazyListState,
    layout: RecentsPickerLayout,
    rowSize: Dp,
    crossSize: Dp,
    onAppTap: (RecentApp) -> Unit,
) {
    val context = LocalContext.current
    val icon = rememberAppDrawable("app/${app.blockKey}") {
        hunoia.luno.bridge.system.AppIcons.drawable(
            context,
            app.packageName,
            className = "",
            userId = app.userId,
        )
    }
    // 磁贴没有文字：无障碍名用应用标签，首帧/查询失败回落包名。
    val appLabel = rememberAppLabel("label/${app.packageName}", app.packageName) {
        context.packageManager.getApplicationInfo(app.packageName, 0)
            .let { context.packageManager.getApplicationLabel(it).toString() }
    }
    val cloneLabel = stringResource(hunoia.luno.actions.R.string.app_clone_label)
    val description = if (app.userId == 0) appLabel else "$appLabel · $cloneLabel"

    Box(
        modifier = Modifier
            .size(
                width = if (layout == RecentsPickerLayout.VERTICAL) crossSize else rowSize,
                height = if (layout == RecentsPickerLayout.VERTICAL) rowSize else crossSize,
            )
            .wheelRowFalloff(listState, index, layout)
            .semantics { contentDescription = description }
            .pressClickable { onAppTap(app) },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.size(RecentsWheelIconSize),
                imageLoader = context.imageLoader,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(RecentsWheelIconSize),
            )
        }
    }
}

/**
 * 离中心越远越淡、越倾斜、越小（在绘制阶段读布局，滚动时不触发重组）。
 * 竖向绕 X 轴、横向绕 Y 轴；距离都沿滚动轴取。当前项（中心）放大表示选中。
 */
private fun Modifier.wheelRowFalloff(
    state: LazyListState,
    index: Int,
    layout: RecentsPickerLayout,
): Modifier = graphicsLayer {
    val layoutInfo = state.layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val rowsFromCenter = (item.offset + item.size / 2f - center) / item.size.coerceAtLeast(1)
    val distance = rowsFromCenter.absoluteValue.coerceAtMost(2f)
    alpha = (1f - distance * RecentsWheelFadePerRow).coerceIn(RecentsWheelMinAlpha, 1f)
    val scale = (RecentsWheelCenterScale - distance * RecentsWheelScaleDropPerRow)
        .coerceAtLeast(RecentsWheelMinScale)
    scaleX = scale
    scaleY = scale
    val tilt = rowsFromCenter.coerceIn(-2f, 2f) * RecentsWheelRowTiltDegrees
    when (layout) {
        RecentsPickerLayout.VERTICAL -> rotationX = -tilt
        RecentsPickerLayout.HORIZONTAL -> rotationY = tilt
    }
    cameraDistance = RecentsWheelCameraDistance * density
}
