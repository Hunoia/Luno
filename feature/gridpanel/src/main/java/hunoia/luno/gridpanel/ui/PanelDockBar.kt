package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import hunoia.luno.bridge.ui.hapticTick
import hunoia.luno.gridpanel.PanelDockEntry
import hunoia.luno.gridpanel.PanelDockIcon
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.geometry.PanelDockPlacement
import hunoia.luno.gridpanel.geometry.needsScroll
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.pressScale
import hunoia.luno.ui.theme.PanelFadeTailFraction
import hunoia.luno.ui.theme.containerShape
import kotlin.math.roundToInt

/** 底栏与卡片之间的间距。 */
val PanelDockGap = 8.dp

/** 底栏整体高度（含内边距）：开启底栏时从面板可用高度里扣掉它 + [PanelDockGap]。 */
val PanelDockHeight = 56.dp

/** 底栏内边距：选中格四周留一圈，让「底」和「选中的块」分成两层。 */
val PanelDockPadding = 6.dp

/** 单个 tab 的最大宽度：入口少时 tab 不被拉得太散（整条底栏仍与卡同宽）。 */
val PanelDockTabMaxWidth = 64.dp

/** 单个 tab 的最小宽度：命中区硬下限（低于 48dp 点不准），再放不下就横向滚动。 */
val PanelDockTabMinWidth = 48.dp

/** tab 上的图标边长。 */
private val PanelDockIconSize = 22.dp

/**
 * 卡片向底栏上报的定位信息：底栏在**打开时**按卡片冻结一次位置，之后只跟着
 * 出现进度做进出场，切换面板不再重算（见 [PanelDockSession]）。
 *
 * 值都在 layout / draw 阶段读（[Modifier.offset] 与 `graphicsLayer`），逐帧跟随不触发重组。
 */
@Stable
class PanelDockAnchor {
    /** 卡片左上角（屏幕坐标，含位置动画与缩放预览）。 */
    var position: Offset by mutableStateOf(Offset.Zero)

    /** 卡片当前高度 px（可能被底栏预留夹过）。 */
    var cardHeightPx: Float by mutableFloatStateOf(0f)

    /** 卡片出现进度 0–1。 */
    var progress: Float by mutableFloatStateOf(0f)

    /** 卡片出现时的完整位移（进度 0，向触发点方向）；底栏跟随同一条位移。 */
    var appearSlide: Offset by mutableStateOf(Offset.Zero)
}

/**
 * 打开面板时冻结的底栏状态：位置 + 宽度 + 外观都取「打开那一刻」的面板。
 *
 * 冻结是「切换时底栏不动」的实现口径：不同面板的宽高 / 透明度 / 模糊各不相同，
 * 若跟着当前面板走，每切一次底栏都会跳一下、颜色也闪一下。切换只换卡片内容，
 * 卡片反过来以底栏为锚点重新定位（[hunoia.luno.gridpanel.geometry.resolveDockAnchoredCard]）。
 */
@Immutable
data class PanelDockSession(
    val placement: PanelDockPlacement,
    /**
     * 冻结时的卡片宽度比例：之后切换到的面板都用这个宽度渲染，
     * 只换内容不换宽度 —— 底栏不动的前提是卡也不左右跳。
     */
    val cardWidthFraction: Float,
    /** 冻结时的卡片透明度：底栏与卡同源，不随新面板变化。 */
    val cardAlpha: Float,
    /** 冻结时的模糊半径（0 = 关闭）。 */
    val blurRadiusPx: Int,
    /** 冻结时的面板 id：用于判断「这是切换后的卡片还是刚打开的那块」。 */
    val panelId: String,
)

/**
 * 底栏层：宽度与位置都来自冻结值，只跟随卡片的出现进度做 alpha / 位移（不缩放）。
 *
 * [onBounds] 回传底栏在窗口内的矩形：根层的「点空白关面板 / 退编辑」用它做排除，
 * 否则点到胶囊会被当成点空白，面板直接关掉。
 */
@Composable
fun PanelDockLayer(
    entries: List<PanelDockEntry>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    session: PanelDockSession,
    anchor: PanelDockAnchor,
    /** 当前面板卡的圆角（px）：底栏与卡同源，切换面板后跟着当前卡走，不做冻结。 */
    cornerPx: Float,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val density = LocalDensity.current
    val placement = session.placement
    val widthDp = with(density) { placement.widthPx.toDp() }
    val tabWidthDp = with(density) { placement.tabWidthPx.toDp() }
    // 底栏容器与面板卡同源：同一个 px 圆角，不另选档位（§2.1.1）。
    val shape = remember(cornerPx, density) {
        containerShape(with(density) { cornerPx.toDp() })
    }
    val listState = rememberLazyListState()
    val scrollable = placement.needsScroll

    // 入口放不下时横滑：选中项必须在可视区内，否则「当前在哪块面板」看不出来。
    LaunchedEffect(placement, selectedId, scrollable) {
        if (!scrollable) return@LaunchedEffect
        val index = entries.indexOfFirst { it.id == selectedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Box(
        modifier
            // 位置来自冻结值：切换面板时这里一个像素都不动。
            .offset {
                IntOffset(
                    x = (placement.centerX - placement.widthPx / 2f).roundToInt(),
                    y = placement.topY.roundToInt(),
                )
            }
            .width(widthDp)
            .height(PanelDockHeight)
            .graphicsLayer {
                val p = anchor.progress.coerceIn(0f, 1f)
                alpha = (p / PanelFadeTailFraction).coerceIn(0f, 1f)
                // 与卡片同一条出现位移，但不做缩放：底栏被缩放会变成一条线。
                val remaining = 1f - p
                translationX = anchor.appearSlide.x * remaining
                translationY = anchor.appearSlide.y * remaining
            }
            .zIndex(2f)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) },
    ) {
        // 面板同款：真实背景模糊垫底，上面才是「卡色」与描边。
        PanelBlurHost(
            enabled = session.blurRadiusPx > 0,
            radiusPx = session.blurRadiusPx,
            cornerRadiusPx = cornerPx,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(gridPanelCardColor.copy(alpha = session.cardAlpha)),
            // 入口少时整条居中聚拢；多到放不下时 LazyRow 自己滚动。
            contentAlignment = Alignment.Center,
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    // wrapContentWidth：入口少时按内容宽居中；多到放不下时按底栏宽裁切并滚动。
                    .wrapContentWidth()
                    .height(PanelDockHeight),
                contentPadding = PaddingValues(horizontal = PanelDockPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { _, entry ->
                    PanelDockTab(
                        entry = entry,
                        isSelected = entry.id == selectedId,
                        width = tabWidthDp,
                        onClick = { onSelect(entry.id) },
                    )
                }
            }
        }
    }
}

/**
 * 一个 tab：选中态用面板自己的语言表达（圆角块 + primary），不用主页底栏那颗指示 pill。
 * 点击切换；没有横滑切 tab —— 横向手势归底栏自己的滚动。
 */
@Composable
private fun PanelDockTab(
    entry: PanelDockEntry,
    isSelected: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactions = remember { MutableInteractionSource() }
    val tileShape = remember { containerShape(ItemCorner) }
    Box(
        modifier = Modifier
            .width(width)
            .height(PanelDockHeight - PanelDockPadding * 2)
            .pressScale(interactions)
            .clip(tileShape)
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
            ) {
                view.hapticTick()
                onClick()
            }
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        PanelDockGlyph(
            icon = entry.icon,
            size = PanelDockIconSize,
            contentDescription = entry.label,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * 面板图标的统一渲染：矢量图标走 [Icon]，名称首字 / 自定义文字走 [TextIconGlyph]。
 * 底栏 tab 与两处设置页行首共用这一份，避免「底栏画得出、设置页画不出」的双实现。
 */
@Composable
fun PanelDockGlyph(
    icon: PanelDockIcon,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) {
    when (icon) {
        is PanelDockIcon.Vector -> Icon(
            imageVector = icon.imageVector,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            tint = tint,
        )
        is PanelDockIcon.Glyph -> Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            TextIconGlyph(text = icon.text, size = size, color = tint)
        }
    }
}
