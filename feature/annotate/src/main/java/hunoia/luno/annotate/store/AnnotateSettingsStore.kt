package hunoia.luno.annotate.store

import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.ToolStyle

/**
 * 标注偏好的持久化端口。
 *
 * 配置层（DataStore + 备份域）在 `:core:data`，而 feature 模块不直连配置存储，
 * 由宿主注入实现（见 `app` 的 `AnnotateSettingsCenter`）。
 */
interface AnnotateSettingsStore {
    suspend fun load(): AnnotatePreferences
    suspend fun save(preferences: AnnotatePreferences)
}

/** 一份标注偏好：每工具风格。笔迹不落盘。 */
data class AnnotatePreferences(
    val styles: Map<ToolKind, ToolStyle> = emptyMap(),
)
