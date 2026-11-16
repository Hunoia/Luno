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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import hunoia.luno.config.model.SuggestionRuleAction
import hunoia.luno.gesture.R
import hunoia.luno.runtime.suggestion.SuggestionSlot
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.containerShape
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 幽灵卡内容动作。
 *
 * 动作集按内容类型给：文字不看 [Save]，图片不看 [Search]/[OpenLink]；
 * [Ocr] 只在拿得到 a11y 节点 bounds 时出现（a11y 取不到文本时的兜底取字）。
 */
enum class PortalAction { Copy, Search, Share, Save, OpenLink, Ocr }

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

    /** 建议规则槽：动作取规则声明的 [SuggestionRuleAction]，值取规则捕获的文本。 */
    data class Suggestion(val suggested: SuggestionSlot) : PortalSlot {
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
internal fun portalSlotsFor(
    content: PortalContent,
    suggestions: List<SuggestionSlot>,
): List<PortalSlot> {
    val slots = ArrayList<PortalSlot>(suggestions.size + 6)
    val openUrls = HashSet<String>()
    val seen = HashSet<String>()
    suggestions.forEach { suggested ->
        if (!seen.add("${suggested.action}:${suggested.value}")) return@forEach
        if (suggested.action == SuggestionRuleAction.OPEN) openUrls += suggested.value
        slots += PortalSlot.Suggestion(suggested)
    }
    portalActionsFor(content).forEach { action ->
        if (action == PortalAction.OpenLink && content.url in openUrls) return@forEach
        slots += PortalSlot.Action(action)
    }
    return slots
}

/** 手指拖向的那条边。 */
enum class PortalEdge { Top, Bottom, Left, Right }

/**
 * 幽灵卡与工具条的几何：**屏幕像素坐标**。
 *
 * 摆放（Compose 渲染）与松手判定共用这一份计算，否则会出现
 * “看着拖到「复制」上了、松手却没执行”。纯函数，可在 JVM 单测里钉死。
 *
 * 模型：卡片跟手（内容预览），手指拖到某条边附近时该边浮出工具条；工具条沿边跟着手指走，
 * 松手落在哪一格就是哪个动作，落空按 [PortalReleaseMode] 处置（[resolvePortalRelease]）。
 */
object PortalLayout {

    /** 单格尺寸：工具条只装图标，48dp 满足浮层命中区基线。 */
    const val SlotDp = 48f
    const val IconDp = 20f
    const val GapDp = 6f
    const val PaddingDp = 6f

    /** 工具条贴边距离：留在系统边缘手势 / 圆角之外。 */
    const val EdgeMarginDp = 12f

    /** 判定「拖到这一边」的带宽：手指离边的垂直距离小于它才浮出工具条。 */
    const val EdgeBandDp = 96f

    /** 幽灵卡尺寸（dp）：宽度按内容自适应，封顶 [CardMaxWidthDp]、保底 [CardMinWidthDp]；高度封顶 [CardMaxHeightDp]。 */
    const val CardMinWidthDp = 160f
    const val CardMaxWidthDp = 280f
    const val CardMaxHeightDp = 320f
    const val CardEstimatedHeightDp = 96f
    const val CardRadiusDp = 18f

    /** 系统栏 / 导航条 inset（px）：工具条与卡片都要让开。 */
    data class Insets(
        val top: Float = 0f,
        val bottom: Float = 0f,
        val left: Float = 0f,
        val right: Float = 0f,
    )

    /** 工具条几何：哪条边、整条的范围、每一格的矩形（屏幕像素坐标）。 */
    data class Bar(
        val edge: PortalEdge,
        val bounds: Rect,
        val slots: List<Rect>,
    ) {
        val isVertical: Boolean get() = edge == PortalEdge.Left || edge == PortalEdge.Right

        /** 整条平移：几何按屏幕坐标算，渲染前减窗口原点用。 */
        fun translated(dx: Float, dy: Float): Bar = Bar(
            edge = edge,
            bounds = Rect(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy),
            slots = slots.map { Rect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) },
        )
    }

    /**
     * 手指落在哪条边的工具条带上；返回 null = 离所有边都远，工具条不出现（松手即取消）。
     *
     * 取距离最近的一条边；等距时按 底 → 顶 → 左 → 右 的顺序选，保证四角处行为确定。
     */
    fun edgeFor(
        finger: Offset,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        bandDp: Float = EdgeBandDp,
        insets: Insets = Insets(),
    ): PortalEdge? {
        if (screenWidth <= 0f || screenHeight <= 0f) return null
        val band = bandDp * density
        val candidates = listOf(
            PortalEdge.Bottom to (screenHeight - insets.bottom - finger.y),
            PortalEdge.Top to (finger.y - insets.top),
            PortalEdge.Left to (finger.x - insets.left),
            PortalEdge.Right to (screenWidth - insets.right - finger.x),
        )
        val nearest = candidates.minBy { it.second }
        return if (nearest.second <= band) nearest.first else null
    }

    /**
     * 工具条几何。未到边缘（[edgeFor] 为 null）、没有格子、屏幕尺寸无效时返回 null。
     *
     * **沿边位置锚定在 [anchorAxis]，不跟手**：跟手会让整条随手指滑动，格子在指下不停变，
     * 用户永远选不中同一个动作。锚点由调用方在「工具条首次浮出」时锁定一次（见
     * [ScreenPickOverlay.movePortal]）；为 null 时退回按手指居中，只用于无状态的一次性计算。
     *
     * [slotMainPx] 是单格主轴尺寸；0 用 [SlotDp]。一屏放不下时整条等比缩小，绝不溢出屏外。
     */
    fun bar(
        finger: Offset,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        count: Int,
        insets: Insets = Insets(),
        slotMainPx: Float = 0f,
        bandDp: Float = EdgeBandDp,
        anchorAxis: Float? = null,
    ): Bar? {
        if (count <= 0 || screenWidth <= 0f || screenHeight <= 0f) return null
        val edge = edgeFor(finger, screenWidth, screenHeight, density, bandDp, insets) ?: return null
        val cross = SlotDp * density
        val pad = PaddingDp * density
        val depth = cross + pad * 2
        var main = if (slotMainPx > 0f) slotMainPx else cross
        var gap = GapDp * density
        val margin = EdgeMarginDp * density
        val vertical = edge == PortalEdge.Left || edge == PortalEdge.Right
        val axisStart = if (vertical) insets.top else insets.left
        val axisEnd = if (vertical) screenHeight - insets.bottom else screenWidth - insets.right
        // 可用主轴长度要两头都留出贴边距，否则缩小后仍会顶出屏外。
        val axisRoom = (axisEnd - axisStart - margin * 2).coerceAtLeast(0f)

        // 放不下：主轴尺寸与间距一体等比缩小，保证整条留在屏内。
        val natural = count * main + (count - 1) * gap
        if (axisRoom > 0f && natural + pad * 2 > axisRoom) {
            val scale = ((axisRoom - pad * 2) / natural).coerceIn(0.05f, 1f)
            main *= scale
            gap *= scale
        }
        val total = count * main + (count - 1) * gap + pad * 2

        // 沿边定位：锚定在浮出那一刻的手指位置（不随手指滑动），贴到安全区就夹住。
        val anchor = anchorAxis ?: if (vertical) finger.y else finger.x
        val axisMin = axisStart + margin
        val axisMax = (axisEnd - margin - total).coerceAtLeast(axisMin)
        val axisPos = (anchor - total / 2f).coerceIn(axisMin, axisMax)

        val bounds = when (edge) {
            PortalEdge.Left -> Rect(
                insets.left + margin,
                axisPos,
                insets.left + margin + depth,
                axisPos + total,
            )
            PortalEdge.Right -> Rect(
                screenWidth - insets.right - margin - depth,
                axisPos,
                screenWidth - insets.right - margin,
                axisPos + total,
            )
            PortalEdge.Top -> Rect(
                axisPos,
                insets.top + margin,
                axisPos + total,
                insets.top + margin + depth,
            )
            PortalEdge.Bottom -> Rect(
                axisPos,
                screenHeight - insets.bottom - margin - depth,
                axisPos + total,
                screenHeight - insets.bottom - margin,
            )
        }

        val slots = List(count) { index ->
            val shift = pad + index * (main + gap)
            when (edge) {
                PortalEdge.Left, PortalEdge.Right -> Rect(
                    bounds.left + pad,
                    bounds.top + shift,
                    bounds.right - pad,
                    bounds.top + shift + main,
                )
                PortalEdge.Top, PortalEdge.Bottom -> Rect(
                    bounds.left + shift,
                    bounds.top + pad,
                    bounds.left + shift + main,
                    bounds.bottom - pad,
                )
            }
        }
        return Bar(edge = edge, bounds = bounds, slots = slots)
    }

    /**
     * 松手落在哪一格：整条范围内取主轴中点最近的一格（格间缝隙也算，不逼用户压线）。
     * 未进入工具条范围返回 null。
     */
    fun hit(bar: Bar?, point: Offset): Int? {
        if (bar == null || bar.slots.isEmpty()) return null
        if (!bar.bounds.contains(point)) return null
        val vertical = bar.isVertical
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        bar.slots.forEachIndexed { index, rect ->
            val distance = if (vertical) {
                abs(point.y - rect.center.y)
            } else {
                abs(point.x - rect.center.x)
            }
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

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
        bar: Bar? = null,
        insets: Insets = Insets(),
    ): Rect {
        val width = cardWidthPx.takeIf { it > 0f } ?: CardMaxWidthDp * density
        val height = cardHeightPx.takeIf { it > 0f } ?: CardEstimatedHeightDp * density
        val gap = GapDp * density
        // 卡片中心对齐手指（跟手：指下即卡心）；贴边时整卡回推到屏内。
        var x = finger.x - width / 2f
        var y = finger.y - height / 2f
        if (bar != null) {
            when (bar.edge) {
                PortalEdge.Left -> x = maxOf(x, bar.bounds.right + gap)
                PortalEdge.Right -> x = minOf(x, bar.bounds.left - gap - width)
                PortalEdge.Top -> y = maxOf(y, bar.bounds.bottom + gap)
                PortalEdge.Bottom -> y = minOf(y, bar.bounds.top - gap - height)
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
 * 与 [PortalLayout.hit] 同源（同一份 [PortalLayout.Bar]），渲染与结算不会分叉。
 */
fun resolvePortalRelease(
    bar: PortalLayout.Bar?,
    point: Offset,
    mode: PortalReleaseMode,
): PortalRelease {
    val index = PortalLayout.hit(bar, point)
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
        PortalAction.Save -> R.string.screen_pick_portal_save
        PortalAction.OpenLink -> R.string.screen_pick_portal_open_link
        PortalAction.Ocr -> R.string.screen_pick_portal_ocr
    },
)

/** 建议槽按规则动作选图标：复制 / 打开链接 / 分享。 */
private fun SuggestionRuleAction.icon(): ImageVector = when (this) {
    SuggestionRuleAction.COPY -> Icons.Default.ContentCopy
    SuggestionRuleAction.OPEN -> Icons.AutoMirrored.Filled.OpenInNew
    SuggestionRuleAction.SHARE -> Icons.Default.Share
}

/** 格子的可读标签：内容动作取本地化动作名，建议槽取规则名（空则兜底「建议」）。 */
@Composable
private fun PortalSlot.label(): String = when (this) {
    is PortalSlot.Action -> action.label()
    is PortalSlot.Suggestion -> suggested.label.ifBlank {
        stringResource(R.string.screen_pick_portal_suggestion)
    }
}

private fun PortalSlot.icon(): ImageVector = when (this) {
    is PortalSlot.Action -> action.icon()
    is PortalSlot.Suggestion -> suggested.action.icon()
}

/**
 * 幽灵卡层（卡片本体 + 边缘工具条）。
 *
 * 卡片中心对齐手指（跟手，贴边时整卡夹在屏内并给工具条让位）；宽度按内容自适应、
 * 封顶 [PortalLayout.CardMaxWidthDp] 并保底 [PortalLayout.CardMinWidthDp]；高度封顶
 * [PortalLayout.CardMaxHeightDp]；文字最多两行（动作仍拿全文，不截断）。
 *
 * 卡片上**没有按钮**：动作只在边缘工具条上松手结算（见 [PortalLayout.bar] /
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
    bar: PortalLayout.Bar?,
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
            PortalBarLayer(bar = bar, slots = slots, activeSlot = activeSlot)
        }
    }
}

/**
 * 边缘工具条：贴边一条胶囊，每格一个圆形图标；指到哪一格哪一格放大，
 * 并在条内侧浮出该格的名字（内容动作名 / 规则名），松手即执行。
 */
@Composable
private fun PortalBarLayer(
    bar: PortalLayout.Bar,
    slots: List<PortalSlot>,
    activeSlot: Int,
) {
    val depth = (PortalLayout.SlotDp + PortalLayout.PaddingDp * 2).dp
    Box(modifier = Modifier.fillMaxSize()) {
        val icons: @Composable () -> Unit = {
            slots.forEachIndexed { index, slot ->
                PortalBarIcon(slot = slot, active = index == activeSlot)
            }
        }
        Box(
            modifier = Modifier
                .offsetPx(bar.bounds.left, bar.bounds.top)
                .let { base -> if (bar.isVertical) base.width(depth) else base.height(depth) }
                .background(
                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                    CapsuleShape,
                )
                .padding(PortalLayout.PaddingDp.dp),
        ) {
            if (bar.isVertical) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(PortalLayout.GapDp.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { icons() }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PortalLayout.GapDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { icons() }
            }
        }
        slots.getOrNull(activeSlot)?.let { slot ->
            PortalSlotLabel(bar = bar, text = slot.label())
        }
    }
}

@Composable
private fun PortalBarIcon(
    slot: PortalSlot,
    active: Boolean,
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.inverseOnSurface
    }
    Box(
        modifier = Modifier
            .size(PortalLayout.SlotDp.dp)
            .scale(if (active) 1.1f else 1f)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = slot.icon(),
            contentDescription = slot.label(),
            tint = tint,
            modifier = Modifier.size(PortalLayout.IconDp.dp),
        )
    }
}

/** 当前格的名字：浮在工具条内侧（屏内那侧），不遮挡手指。 */
@Composable
private fun PortalSlotLabel(
    bar: PortalLayout.Bar,
    text: String,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = DensityProvider.density
    val gap = 8f * density
    val screenWidth = DensityProvider.screenWidthPx.toFloat()
    val screenHeight = DensityProvider.screenHeightPx.toFloat()
    val chipWidth = size.width.toFloat()
    val chipHeight = size.height.toFloat()
    val centeredX = bar.bounds.left + (bar.bounds.width - chipWidth) / 2f
    val centeredY = bar.bounds.top + (bar.bounds.height - chipHeight) / 2f
    val (x, y) = when (bar.edge) {
        PortalEdge.Left -> (bar.bounds.right + gap) to centeredY
        PortalEdge.Right -> (bar.bounds.left - gap - chipWidth) to centeredY
        PortalEdge.Top -> centeredX to (bar.bounds.bottom + gap)
        PortalEdge.Bottom -> centeredX to (bar.bounds.top - gap - chipHeight)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offsetPx(
                    x.coerceIn(0f, (screenWidth - chipWidth).coerceAtLeast(0f)),
                    y.coerceIn(0f, (screenHeight - chipHeight).coerceAtLeast(0f)),
                )
                .onSizeChanged { size = it }
                .background(
                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                    CapsuleShape,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
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
