package hunoia.luno.core.domain

/**
 * 文字/表情图标：动作库条目的 `iconKey` 不是调色板 key 时，把原始字符串当文字/表情渲染。
 *
 * 只是数据，住在契约层，好让能力模块（动作/搜索）能产出它、UI 层（`:ui:design`）渲染它，
 * 而双方都不需要互相依赖 `ImageVector` 之类平台类型。
 */
data class TextIcon(val text: String)
