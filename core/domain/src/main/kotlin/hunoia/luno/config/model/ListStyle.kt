package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 设置列表视觉风格。
 *
 * - [Segmented]：每行独立圆角 chip，行间 1dp 缝合缝（Luno 既有风格，默认）。
 * - [CardDivided]：整组一张大圆角卡片，行间用缩进的细分割线（参照 FlClash）。
 */
@Serializable
@Keep
enum class ListStyle {
    Segmented,
    CardDivided,
}
