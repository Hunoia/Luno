package hunoia.luno.ui.component.row

import androidx.compose.runtime.compositionLocalOf
import hunoia.luno.config.model.ListStyle

/**
 * 全局列表风格（由 `:app` 根部从 `TuneConfig.advanced.listStyle` 提供）。
 * [ListStyle.CardDivided] 时 [LunoGroup] 改为单卡 + 行间分割线。
 */
val LocalListStyle = compositionLocalOf { ListStyle.Segmented }

/**
 * 当前是否处于 [LunoGroup] 的"大卡分割线"组内。
 * 为 true 时行组件忽略调用方传入的 shape/底色，改画矩形 + 透明，
 * 由卡片容器统一托底裁切（组外独立行不受影响）。
 */
val LocalInDividedCard = compositionLocalOf { false }
