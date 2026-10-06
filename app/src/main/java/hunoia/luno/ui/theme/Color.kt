package hunoia.luno.ui.theme

/**
 * 语义化颜色令牌。
 *
 * 颜色本身来自 M3 `ColorScheme`（动态取色强制开启，见 `Theme.kt`），
 * 这里只放"角色化"的常量：那些跨组件复用一个固定色值的场合。
 * 见 docs/ui-spec.md §2。
 */

/** 禁用态整体透明度。所有组件的 `enabled = false` 走这一个值。 */
const val DisabledAlpha: Float = 0.36f
