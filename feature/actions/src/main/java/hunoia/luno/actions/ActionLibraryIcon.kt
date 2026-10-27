package hunoia.luno.actions

import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.actions.icon.IconPalette
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.core.domain.TextIcon

/**
 * 动作库条目的图标解析：自定义文字/表情 > 调色板图标 > 类型默认图标。
 *
 * 归在 `:feature:actions`：图标资源与调色板是动作模块的展示知识。
 * 返回值是「交给 UI 层解释的不透明模型」（ImageVector / [TextIcon] / 应用图标 String），
 * 因此契约层不必引入 Compose 类型，搜索侧只需要原样透传。
 */
fun ActionLibraryEntry.resolvedIcon(): Any? {
    iconKey?.let { key ->
        val vector = IconPalette.byKey(key)
        if (vector != null) return vector
        if (key.isNotBlank()) return TextIcon(key)
    }
    return ActionDefinitions.byTypeId(typeId)?.icon
}
