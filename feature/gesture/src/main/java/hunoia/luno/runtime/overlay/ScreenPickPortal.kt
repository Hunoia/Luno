package hunoia.luno.runtime.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.gesture.R
import hunoia.luno.ui.theme.MinInteractiveSize
import java.util.Locale
import kotlin.math.roundToInt

/** 幽灵卡底部动作槽的动作。动作集按内容类型给：文字不看 [Save]，图片不看 [Search]/[OpenLink]。 */
enum class PortalAction { Copy, Search, Share, Save, OpenLink }

/** 一次长按识别出的内容：文字节点或图片节点（两者都为空 = 识别中 / 无可取内容）。 */
internal data class PortalContent(
    val text: String? = null,
    val imageSize: Pair<Int, Int>? = null,
    /** 文本里抽出的第一条链接（有则显示「打开链接」）。 */
    val url: String? = null,
    /** 图片节点的缩略图（来自预截图裁剪）；还没裁好时为 null。 */
    val preview: ImageBitmap? = null,
) {
    val isImage: Boolean get() = text.isNullOrBlank() && imageSize != null
    val isEmpty: Boolean get() = text.isNullOrBlank() && imageSize == null
}

/** 动作集：文字 复制/搜索/分享（含链接时多一个打开）；图片 复制/分享/保存；两者共存时并集。 */
internal fun portalActionsFor(content: PortalContent): List<PortalAction> {
    val hasText = !content.text.isNullOrBlank()
    val hasImage = content.imageSize != null
    if (!hasText && !hasImage) return emptyList()
    return buildList {
        add(PortalAction.Copy)
        if (hasText) add(PortalAction.Search)
        add(PortalAction.Share)
        if (hasImage) add(PortalAction.Save)
        if (hasText && !content.url.isNullOrBlank()) add(PortalAction.OpenLink)
    }
}

/**
 * 幽灵卡与底部动作槽的几何：**屏幕像素坐标**。
 *
 * 摆放（Compose 渲染）与命中（松手落在哪个槽）共用这一份计算，否则会出现
 * “看着拖到复制上了、松手却没执行”。纯函数，可在 JVM 单测里钉死。
 */
object PortalLayout {

    const val SlotDp = 56f
    const val GapDp = 12f
    const val BottomMarginDp = 28f

    /** 幽灵卡尺寸（dp）：宽度按内容自适应，封顶 [CardMaxWidthDp]、保底 [CardMinWidthDp]；高度封顶 [CardMaxHeightDp]。 */
    const val CardMinWidthDp = 160f
    const val CardMaxWidthDp = 280f
    const val CardMaxHeightDp = 320f
    const val CardEstimatedHeightDp = 96f
    const val CardRadiusDp = 18f
    const val SlotRadiusDp = 18f

    /** 槽位矩形：底部居中一行，左右夹在屏内，底部让开导航栏 inset。 */
    fun slotRects(
        screenWidth: Float,
        screenHeight: Float,
        insetBottom: Float,
        count: Int,
        density: Float,
    ): List<Rect> {
        if (count <= 0 || screenWidth <= 0f || screenHeight <= 0f) return emptyList()
        val slot = SlotDp * density
        val gap = GapDp * density
        val rowWidth = count * slot + (count - 1) * gap
        val left = ((screenWidth - rowWidth) / 2f).coerceAtLeast(0f)
        val bottom = (screenHeight - insetBottom - BottomMarginDp * density).coerceAtLeast(slot)
        val top = bottom - slot
        return List(count) { index ->
            val x = left + index * (slot + gap)
            Rect(x, top, x + slot, bottom)
        }
    }

    /** 命中的槽位下标；未命中返回 null（松手即取消，不执行任何动作）。 */
    fun hit(rects: List<Rect>, point: Offset): Int? =
        rects.indexOfFirst { it.contains(point) }.takeIf { it >= 0 }

    /** 幽灵卡在视图坐标下的矩形；手势层用它判断“按在卡上=移动、按在空白=框选”。 */
    fun cardRect(
        finger: Offset,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        cardWidthPx: Float = CardMaxWidthDp * density,
        cardHeightPx: Float = CardEstimatedHeightDp * density,
    ): Rect {
        val width = cardWidthPx.takeIf { it > 0f } ?: CardMaxWidthDp * density
        val height = cardHeightPx.takeIf { it > 0f } ?: CardEstimatedHeightDp * density
        // 卡片中心对齐手指（跟手：指下即卡心）；贴边时整卡回推到屏内。
        val cardX = (finger.x - width / 2f).coerceIn(0f, (screenWidth - width).coerceAtLeast(0f))
        val cardY = (finger.y - height / 2f).coerceIn(0f, (screenHeight - height).coerceAtLeast(0f))
        return Rect(cardX, cardY, cardX + width, cardY + height)
    }
}

private fun PortalAction.icon(): ImageVector = when (this) {
    PortalAction.Copy -> Icons.Default.ContentCopy
    PortalAction.Search -> Icons.Default.Search
    PortalAction.Share -> Icons.Default.Share
    PortalAction.Save -> Icons.Default.SaveAlt
    PortalAction.OpenLink -> Icons.Default.OpenInNew
}

@Composable
private fun PortalAction.label(): String = stringResource(
    when (this) {
        PortalAction.Copy -> R.string.screen_pick_portal_copy
        PortalAction.Search -> R.string.screen_pick_portal_search
        PortalAction.Share -> R.string.screen_pick_portal_share
        PortalAction.Save -> R.string.screen_pick_portal_save
        PortalAction.OpenLink -> R.string.screen_pick_portal_open_link
    },
)

/**
 * 幽灵卡层：手指按下时浮起的卡片，底部动作槽点击执行。
 *
 * 卡片中心对齐手指坐标（跟手，贴边时整体夹在屏内）；宽度按内容自适应、
 * 封顶 [PortalLayout.CardMaxWidthDp] 并保底 [PortalLayout.CardMinWidthDp]；高度封顶
 * [PortalLayout.CardMaxHeightDp]；文字最多两行（抬手后的动作仍拿全文，不截断）。
 *
 * @param finger 手指位置（视图坐标）
 * @param cardWidthPx 卡片当前测量宽度（px）；0 时回退封顶宽，用于首帧与命中判定对齐
 * @param cardHeightPx 卡片当前测量高度（px）
 */
@Composable
internal fun PortalLayer(
    finger: Offset,
    content: PortalContent,
    resolving: Boolean,
    actions: List<PortalAction>,
    cardHeightPx: Float,
    cardWidthPx: Float = 0f,
    showActions: Boolean = true,
    onCardSize: (IntSize) -> Unit,
    onAction: (PortalAction) -> Unit = {},
) {
    val screenWidthPx = DensityProvider.screenWidthPx.toFloat()
    val screenHeightPx = DensityProvider.screenHeightPx.toFloat()
    val cardBackground = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f)
    val cardContent = MaterialTheme.colorScheme.inverseOnSurface

    Box(modifier = Modifier.fillMaxSize()) {
        val card = PortalLayout.cardRect(
            finger = finger,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            density = DensityProvider.density,
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
        )
        Column(
            modifier = Modifier
                .offsetPx(card.left, card.top)
                .widthIn(
                    min = PortalLayout.CardMinWidthDp.dp,
                    max = PortalLayout.CardMaxWidthDp.dp,
                )
                .wrapContentWidth(Alignment.Start)
                .heightIn(max = PortalLayout.CardMaxHeightDp.dp)
                .onSizeChanged(onCardSize)
                .background(cardBackground, RoundedCornerShape(PortalLayout.CardRadiusDp.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                resolving -> Text(
                    text = stringResource(R.string.screen_pick_portal_resolving),
                    color = cardContent.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                )
                content.isImage -> if (content.preview != null) {
                    Image(
                        bitmap = content.preview,
                        contentDescription = stringResource(R.string.screen_pick_portal_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .heightIn(max = 96.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = cardContent,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = content.imageSize?.let { size ->
                                String.format(
                                    Locale.US,
                                    "%s · %d×%d",
                                    stringResource(R.string.screen_pick_portal_image),
                                    size.first,
                                    size.second,
                                )
                            } ?: stringResource(R.string.screen_pick_portal_image),
                            color = cardContent,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                content.isEmpty -> Text(
                    text = stringResource(R.string.screen_pick_no_content),
                    color = cardContent.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
                // 文字最多两行，超出省略；全文仍保留在 content.text，复制/搜索不受影响。
                else -> Text(
                    text = content.text.orEmpty(),
                    color = cardContent,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actions.isNotEmpty()) {
                // 不用 fillMaxWidth：会把 wrapContentWidth 的卡片撑到最大宽，
                // 内容少时也全宽。动作槽按图标数量自适应。
                Row(
                    modifier = Modifier
                        .alpha(if (showActions) 1f else 0f)
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        PortalCardActionIcon(
                            action = action,
                            onClick = { onAction(action) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalCardActionIcon(
    action: PortalAction,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier.size(40.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = action.icon(),
                contentDescription = action.label(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PortalSlot(
    action: PortalAction,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PortalLayout.SlotRadiusDp.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier.fillMaxSize().heightIn(min = MinInteractiveSize),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(action.icon(), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text = action.label(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** 绝对定位（px）：幽灵卡与动作槽都按屏幕像素算几何，这里只做一次换算。 */
private fun Modifier.offsetPx(x: Float, y: Float): Modifier =
    this.then(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) })
