package hunoia.luno.runtime.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import hunoia.luno.config.model.PortalReleaseMode
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.runtime.recognition.RecognitionAction
import hunoia.luno.runtime.recognition.RecognizedItem
import hunoia.luno.gesture.R
import hunoia.luno.ui.component.overlay.EdgeActionBar
import hunoia.luno.ui.component.overlay.EdgeBarSlot
import hunoia.luno.ui.overlay.EdgeBar
import hunoia.luno.ui.overlay.EdgeBarEdge
import hunoia.luno.ui.overlay.EdgeBarLayout
import hunoia.luno.ui.theme.containerShape
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 幽灵卡内容动作。
 *
 * 动作集按内容类型给：文字不看 [Save]，图片不看 [Search]/[OpenLink]；
 * [Ocr] 只在拿得到 a11y 节点 bounds 时出现（a11y 取不到文本时的兜底取字）。
 */
enum class PortalAction { Copy, Search, Share, Float, Save, OpenLink, Ocr }

/** 一次长按识别出的内容：文字节点、图片节点或二维码（三者都为空 = 识别中 / 无可取内容）。 */
internal data class PortalContent(
    val text: String? = null,
    val imageSize: Pair<Int, Int>? = null,
    /** 文本里抽出的第一条链接（有则显示「打开链接」）。 */
    val url: String? = null,
    /** 图片节点的缩略图（来自预截图裁剪）；还没裁好时为 null。 */
    val preview: ImageBitmap? = null,
    /** 命中的 a11y 节点 bounds（屏幕坐标）：OCR 兜底/按需识别的区域。 */
    val nodeBounds: android.graphics.Rect? = null,
    /** 条码识别结果：二维码/条形码的原始内容；非空时卡片按条码样式展示。 */
    val qrText: String? = null,
    /** 命中一维条形码（否则二维码）。 */
    val qrIsBarcode: Boolean = false,
) {
    val isQr: Boolean get() = qrText != null
    val isImage: Boolean get() = text.isNullOrBlank() && imageSize != null && qrText == null
    val isEmpty: Boolean get() = text.isNullOrBlank() && imageSize == null && qrText == null
}

/**
 * 工具条上的一格：内容动作（按内容类型派生）或建议规则（与建议胶囊同一套规则）。
 *
 * 建议槽不复用 [PortalAction]：它的动作来自用户配置的规则，值也是规则捕获出来的那一小段
 * 文本（验证码、链接……），与「复制整段识别文本」不是同一件事。
 */
internal sealed interface PortalSlot {
    val id: String

    /** 内容动作槽。 */
    data class Action(val action: PortalAction) : PortalSlot {
        override val id: String get() = "action:${action.name}"
    }

    /** 建议槽：输出与值都来自识别器结果，本层不做任何规则判定。 */
    data class Suggestion(val suggested: RecognizedItem) : PortalSlot {
        override val id: String get() = "rule:${suggested.ruleId}"
    }
}

/** 动作集：文字 复制/搜索/分享（含链接时多一个打开）；图片 复制/分享/保存；两者共存时并集。 */
internal fun portalActionsFor(content: PortalContent): List<PortalAction> {
    // 条码卡：复制/搜索/分享 + 打开链接（内容为 URL 时）。不含保存/OCR。
    if (content.isQr) {
        return buildList {
            add(PortalAction.Copy)
            add(PortalAction.Search)
            add(PortalAction.Share)
            add(PortalAction.Float)
            if (!content.url.isNullOrBlank()) add(PortalAction.OpenLink)
        }
    }
    val hasText = !content.text.isNullOrBlank()
    val hasImage = content.imageSize != null
    if (!hasText && !hasImage) return emptyList()
    return buildList {
        // OCR 放第一：与屏幕识别框选动作条（Ocr → Copy → Search → Share → Save）对齐；
        // 只有拿得到节点 bounds 才给 OCR。
        if (content.nodeBounds != null) add(PortalAction.Ocr)
        add(PortalAction.Copy)
        if (hasText) add(PortalAction.Search)
        add(PortalAction.Share)
        // 悬浮显示：文本与图片都给（图片落一份缓存副本再挂窗，不进历史 / 图库）。
        if (hasText || hasImage) add(PortalAction.Float)
        if (hasImage) add(PortalAction.Save)
        if (hasText && !content.url.isNullOrBlank()) add(PortalAction.OpenLink)
    }
}

/**
 * 工具条格子：规则建议在前，内容动作在后（规则是这次识别「更具体」的结果）。
 *
 * 两处去重：
 * - `OPEN` 规则已经给出可分母的链接时，内容动作的 [PortalAction.OpenLink] 不再出现
 *   （「打开链接」只留一个槽）；
 * - 建议之间出现完全相同的「动作 + 值」（同一条链接配了两条规则）只保留第一条。
 */
/**
 * 工具条格子 = 识别器给出的建议槽（在前）+ 内容类型动作（在后）。
 *
 * 去重口径只有一处：识别器已经保证「同一条链接不出两条建议」，这里再挡掉
 * 「建议槽已经能打开这条链接时，内容动作再给一个打开链接」的重复。
 */
internal fun portalSlotsFor(
    content: PortalContent,
    recognized: List<RecognizedItem>,
): List<PortalSlot> {
    val slots = ArrayList<PortalSlot>(recognized.size + 6)
    val openUrls = HashSet<String>()
    val seen = HashSet<String>()
    recognized.forEach { item ->
        if (!seen.add("${item.output}:${item.value}")) return@forEach
        if (item.builtin == RecognitionBuiltin.OpenLink) openUrls += item.urls
        slots += PortalSlot.Suggestion(item)
    }
    portalActionsFor(content).forEach { action ->
        if (action == PortalAction.OpenLink && content.url != null && content.url in openUrls) {
            return@forEach
        }
        slots += PortalSlot.Action(action)
    }
    return slots
}

/**
 * 幽灵卡几何：**屏幕像素坐标**。
 *
 * 卡片跟手（内容预览）；拖到某条边时浮出工具条。工具条的几何 / 命中 / 沿边锚定
 * 收口在 `:ui:design` 的 [EdgeBarLayout]（与剪贴板部件的拖拽动作条同一份实现），
 * 这里只保留卡片尺寸与「给工具条让位」的摆放。
 */
object PortalLayout {

    /** 幽灵卡尺寸（dp）：宽度按内容自适应，封顶 [CardMaxWidthDp]、保底 [CardMinWidthDp]；高度封顶 [CardMaxHeightDp]。 */
    const val CardMinWidthDp = 160f
    const val CardMaxWidthDp = 280f
    const val CardMaxHeightDp = 320f
    const val CardEstimatedHeightDp = 96f
    const val CardRadiusDp = 18f

    /**
     * 幽灵卡矩形；手势层用它判断“按在卡上=移动、按在空白=框选”。
     *
     * [bar] 非空时卡片沿工具条法线方向让位，避免卡片压住要松手的那一格。
     */
    fun cardRect(
        finger: Offset,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        cardWidthPx: Float = CardMaxWidthDp * density,
        cardHeightPx: Float = CardEstimatedHeightDp * density,
        bar: EdgeBar? = null,
        insets: EdgeBarLayout.Insets = EdgeBarLayout.Insets(),
    ): Rect {
        val width = cardWidthPx.takeIf { it > 0f } ?: CardMaxWidthDp * density
        val height = cardHeightPx.takeIf { it > 0f } ?: CardEstimatedHeightDp * density
        val gap = EdgeBarLayout.GapDp * density
        // 卡片中心对齐手指（跟手：指下即卡心）；贴边时整卡回推到屏内。
        var x = finger.x - width / 2f
        var y = finger.y - height / 2f
        if (bar != null) {
            when (bar.edge) {
                EdgeBarEdge.LEFT -> x = maxOf(x, bar.bounds.right + gap)
                EdgeBarEdge.RIGHT -> x = minOf(x, bar.bounds.left - gap - width)
                EdgeBarEdge.TOP -> y = maxOf(y, bar.bounds.bottom + gap)
                EdgeBarEdge.BOTTOM -> y = minOf(y, bar.bounds.top - gap - height)
            }
        }
        x = x.coerceIn(insets.left, (screenWidth - insets.right - width).coerceAtLeast(insets.left))
        y = y.coerceIn(insets.top, (screenHeight - insets.bottom - height).coerceAtLeast(insets.top))
        return Rect(x, y, x + width, y + height)
    }
}

/** 松手时的结局：执行某一格 / 取消（按设置决定关层或留在识屏页）。 */
sealed interface PortalRelease {
    data class Act(val index: Int) : PortalRelease
    data object Cancel : PortalRelease
    data object Stay : PortalRelease
}

/**
 * 松手结算：命中工具条格就执行；否则按 [mode] 决定撤卡关层还是留在识屏页。
 *
 * 与 [EdgeBarLayout.hit] 同源（同一份 [EdgeBar]），渲染与结算不会分叉。
 */
fun resolvePortalRelease(
    bar: EdgeBar?,
    point: Offset,
    mode: PortalReleaseMode,
): PortalRelease {
    val index = EdgeBarLayout.hit(bar, point)
    return if (index != null) {
        PortalRelease.Act(index)
    } else {
        when (mode) {
            PortalReleaseMode.Cancel -> PortalRelease.Cancel
            PortalReleaseMode.StayInPicker -> PortalRelease.Stay
        }
    }
}

private fun PortalAction.icon(): ImageVector = when (this) {
    PortalAction.Copy -> Icons.Default.ContentCopy
    PortalAction.Search -> Icons.Default.Search
    PortalAction.Share -> Icons.Default.Share
    PortalAction.Float -> Icons.Default.PushPin
    PortalAction.Save -> Icons.Default.SaveAlt
    PortalAction.OpenLink -> Icons.AutoMirrored.Filled.OpenInNew
    PortalAction.Ocr -> Icons.Default.TextFields
}

@Composable
private fun PortalAction.label(): String = stringResource(
    when (this) {
        PortalAction.Copy -> R.string.screen_pick_portal_copy
        PortalAction.Search -> R.string.screen_pick_portal_search
        PortalAction.Share -> R.string.screen_pick_portal_share
        PortalAction.Float -> R.string.screen_pick_portal_float
        PortalAction.Save -> R.string.screen_pick_portal_save
        PortalAction.OpenLink -> R.string.screen_pick_portal_open_link
        PortalAction.Ocr -> R.string.screen_pick_portal_ocr
    },
)

/**
 * 建议槽的图标：取识别器给出的**动作集首位**（不再自己判输出类型）。
 *
 * 动作集首位就是这条结果的主语义：复制类给复制、链接给打开、动作输出给动作、
 * 岛给通知。`null`（老数据没有 output 派生）退回「打开」，与内置链接规则一致。
 */
private fun PortalSlot.Suggestion.iconOf(): ImageVector =
    when (suggested.actions.firstOrNull()) {
        RecognitionAction.Copy -> Icons.Default.ContentCopy
        RecognitionAction.Open -> Icons.AutoMirrored.Filled.OpenInNew
        RecognitionAction.Share -> Icons.Default.Share
        RecognitionAction.Run -> Icons.Default.Android
        RecognitionAction.Notify -> Icons.Default.Notifications
        null -> Icons.AutoMirrored.Filled.OpenInNew
    }

/** 格子的可读标签：内容动作取本地化动作名，建议槽取规则名（空则兜底「建议」）。 */
@Composable
private fun PortalSlot.label(): String = when (this) {
    is PortalSlot.Action -> action.label()
    is PortalSlot.Suggestion -> suggested.ruleName.ifBlank {
        stringResource(R.string.screen_pick_portal_suggestion)
    }
}

private fun PortalSlot.icon(): ImageVector = when (this) {
    is PortalSlot.Action -> action.icon()
    is PortalSlot.Suggestion -> iconOf()
}

/**
 * 幽灵卡层（卡片本体 + 边缘工具条）。
 *
 * 卡片中心对齐手指（跟手，贴边时整卡夹在屏内并给工具条让位）；宽度按内容自适应、
 * 封顶 [PortalLayout.CardMaxWidthDp] 并保底 [PortalLayout.CardMinWidthDp]；高度封顶
 * [PortalLayout.CardMaxHeightDp]；文字最多两行（动作仍拿全文，不截断）。
 *
 * 卡片上**没有按钮**：动作只在边缘工具条上松手结算（见 [EdgeBarLayout.bar] /
 * [resolvePortalRelease]）。
 *
 * @param finger 手指位置（视图坐标）
 * @param bar 工具条几何（视图坐标）；null = 还没拖到任何一条边
 * @param activeSlot 当前指到的格；-1 = 没有
 * @param cardWidthPx 卡片当前测量宽度（px）；0 时回退封顶宽，用于首帧与命中判定对齐
 * @param cardHeightPx 卡片当前测量高度（px）
 */
@Composable
internal fun PortalLayer(
    finger: Offset,
    content: PortalContent,
    resolving: Boolean,
    slots: List<PortalSlot>,
    bar: EdgeBar?,
    activeSlot: Int,
    cardHeightPx: Float,
    cardWidthPx: Float = 0f,
    onCardSize: (IntSize) -> Unit,
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
            bar = bar,
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
                .background(cardBackground, containerShape(PortalLayout.CardRadiusDp.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = if (content.isImage) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            when {
                resolving -> Text(
                    text = stringResource(R.string.screen_pick_portal_resolving),
                    color = cardContent.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                )
                content.isQr -> QrCardContent(content, cardContent)
                content.isImage -> if (content.preview != null) {
                    Image(
                        bitmap = content.preview,
                        contentDescription = stringResource(R.string.screen_pick_portal_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .heightIn(max = 96.dp)
                            .clip(containerShape(12.dp)),
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
        }
        if (bar != null && slots.isNotEmpty()) {
            EdgeActionBar(
                bar = bar,
                slots = slots.map { EdgeBarSlot(icon = it.icon(), label = it.label()) },
                activeSlot = activeSlot,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
        }
    }
}

/** 条码卡内容：顶部「二维码/条形码」标识 + 解码文本（最多 4 行省略）。 */
@Composable
private fun QrCardContent(content: PortalContent, cardContent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.QrCode,
                contentDescription = null,
                tint = cardContent.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(
                    if (content.qrIsBarcode) R.string.screen_pick_barcode_label
                    else R.string.screen_pick_qr_label,
                ),
                color = cardContent.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = content.qrText.orEmpty(),
            color = cardContent,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 绝对定位（px）：幽灵卡与工具条都按屏幕像素算几何，这里只做一次换算。 */
private fun Modifier.offsetPx(x: Float, y: Float): Modifier =
    this.then(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) })
