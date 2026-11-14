package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 设置列表视觉风格。
 *
 * - [Segmented]：每行独立圆角块，行间 `SegmentedGap` 缝合缝（Luno 既有风格，默认）。
 * - [CardDivided]：整组一张大圆角卡片，行间用缩进的细分割线（参照 FlClash）。
 *
 * 两种风格的行组件完全相同，差异集中在 `LunoGroupItem` / `groupItemSpacing` /
 * `groupItemClip` 三个入口（ui-guidelines §4.1）；页面不判风格。
 */
@Serializable
@Keep
enum class ListStyle {
    Segmented,
    CardDivided,
}
