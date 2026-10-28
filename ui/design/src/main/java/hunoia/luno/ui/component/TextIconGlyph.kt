package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlin.math.min

/**
 * 兼容桥：文字/表情图标数据已下沉 `hunoia.luno.core.domain`，渲染仍在 UI 层。
 */
typealias TextIcon = hunoia.luno.core.domain.TextIcon

/** 渲染 [TextIcon]：与 App 图标（Coil 模型）区分开的文字/表情图标。 */
@Composable
fun TextIconGlyph(
    icon: TextIcon,
    size: Dp,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    TextIconGlyph(text = icon.text, size = size, color = color, modifier = modifier)
}

/**
 * 文字/表情图标的统一渲染：在 [size] 见方容器内按需缩小字号，避免裁剪且横竖居中。
 * 直接用 titleLarge 的 Text 会因行高超出容器被切掉，emoji 还会偏上。
 */
@Composable
fun TextIconGlyph(
    text: String,
    size: Dp,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = MaterialTheme.typography.titleLarge.copy(
        color = color,
        lineHeight = TextUnit.Unspecified,
    )
    val sidePx = with(density) { size.toPx() }
    val style = remember(text, base, sidePx, density) {
        val measured = measurer.measure(text = AnnotatedString(text), style = base)
        val w = measured.size.width.toFloat()
        val h = measured.size.height.toFloat()
        if (w <= sidePx && h <= sidePx) {
            base
        } else {
            base.copy(fontSize = base.fontSize * min(sidePx / w, sidePx / h))
        }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.wrapContentSize(Alignment.Center, unbounded = true),
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
