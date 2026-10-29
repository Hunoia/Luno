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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.gesture.R
import hunoia.luno.ui.theme.MinInteractiveSize
import java.util.Locale
import kotlin.math.roundToInt

/** 幽灵卡上可拖放的动作。动作集按内容类型给：文字不看 [Save]，图片不看 [Search]/[OpenLink]。 */
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

/** 动作集：文字 复制/搜索/分享（含链接时多一个打开）；图片 复制/分享/保存。 */
internal fun portalActionsFor(content: PortalContent): List<PortalAction> = when {
    content.isImage -> listOf(PortalAction.Copy, PortalAction.Share, PortalAction.Save)
    !content.text.isNullOrBlank() -> buildList {
        add(PortalAction.Copy)
        add(PortalAction.Search)
        add(PortalAction.Share)
        if (!content.url.isNullOrBlank()) add(PortalAction.OpenLink)
    }
    else -> emptyList()
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

    /** 幽灵卡自身尺寸/圆角：与底部分布共用，避免卡片和槽位两个尺寸体系。 */
    const val CardWidthDp = 216f
    const val CardEstimatedHeightDp = 96f
    const val CardGapDp = 20f
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
 * 幽灵卡层：手指按住时浮起的卡片 + 底部动作槽，`activeSlot` 高亮当前指下的槽。
 *
 * 卡片跟随手指（始终位于指上方，上方放不下才翻到下方），拖到底部动作槽时仍能看到内容。
 *
 * @param finger 手指位置（视图坐标）
 * @param slotRects 动作槽矩形（屏幕坐标，长度与 [actions] 对齐）
 */
@Composable
internal fun PortalLayer(
    finger: Offset,
    content: PortalContent,
    resolving: Boolean,
    actions: List<PortalAction>,
    activeSlot: Int?,
    slotRects: List<Rect>,
    originX: Int,
    originY: Int,
) {
    val screenWidthPx = DensityProvider.screenWidthPx.toFloat()
    val screenHeightPx = DensityProvider.screenHeightPx.toFloat()
    val cardWidthPx = PortalLayout.CardWidthDp * DensityProvider.density
    val cardBackground = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f)
    val cardContent = MaterialTheme.colorScheme.inverseOnSurface

    Box(modifier = Modifier.fillMaxSize()) {
        actions.forEachIndexed { index, action ->
            val rect = slotRects.getOrNull(index) ?: return@forEachIndexed
            Box(
                modifier = Modifier
                    .offsetPx(rect.left - originX, rect.top - originY)
                    .size(PortalLayout.SlotDp.dp),
            ) {
                PortalSlot(action = action, active = activeSlot == index)
            }
        }

        val gapPx = PortalLayout.CardGapDp * DensityProvider.density
        // 卡片中心跟随手指；靠近边缘时允许露一半（保证右半屏也能滑过去），
        // 不再用“整张卡必须留在屏内”的 clamp，否则卡片到屏幕 2/3 就停住。
        val cardX = (finger.x - cardWidthPx / 2f).coerceIn(
            -cardWidthPx / 2f,
            screenWidthPx - cardWidthPx / 2f,
        )
        val estimatedCard = PortalLayout.CardEstimatedHeightDp * DensityProvider.density
        val above = finger.y - gapPx - estimatedCard
        val cardY = (if (above > 0f) above else finger.y + gapPx)
            .coerceIn(0f, (screenHeightPx - estimatedCard).coerceAtLeast(0f))
        Column(
            modifier = Modifier
                .offsetPx(cardX, cardY)
                .widthIn(max = PortalLayout.CardWidthDp.dp)
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
                else -> Text(
                    text = content.text.orEmpty(),
                    color = cardContent,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(max = 72.dp),
                )
            }
        }
    }
}

@Composable
private fun PortalSlot(action: PortalAction, active: Boolean) {
    Surface(
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
