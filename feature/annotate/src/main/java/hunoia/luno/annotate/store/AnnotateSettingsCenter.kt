package hunoia.luno.annotate.store

import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AnnotateSettings
import hunoia.luno.config.model.AnnotateToolStyle

/**
 * 标注偏好门面：配置层读写集中在这里，与 `ActionLibrary` / `GridPanelCenter` 同构。
 * 读失败按默认值处理（标注本身不依赖偏好）；写失败静默（偏好不是用户数据）。
 */
object AnnotateSettingsCenter {

    suspend fun load(): AnnotatePreferences {
        val settings = runCatching { ConfigProvider.getAnnotateSettings() }.getOrNull()
            ?: return AnnotatePreferences()
        return AnnotateSettingsMapper.toPreferences(settings)
    }

    suspend fun save(preferences: AnnotatePreferences) {
        runCatching {
            ConfigProvider.updateAnnotateSettings { AnnotateSettingsMapper.toSettings(preferences) }
        }
    }
}

/** 一份标注偏好：每工具风格。笔迹不落盘。 */
data class AnnotatePreferences(
    val styles: Map<ToolKind, AnnotateToolStyle> = emptyMap(),
)

/**
 * 配置层键 ↔ 标注工具映射（风格即 [AnnotateToolStyle]，无字段拷贝）。
 *
 * 落盘键用 `ToolKind.name`：core:data 不认识标注工具枚举，读取侧再做映射；
 * 未知键（旧版本残留 / 新版本新增工具）直接忽略，跨版本不会解码失败。
 */
internal object AnnotateSettingsMapper {

    fun toPreferences(settings: AnnotateSettings): AnnotatePreferences = AnnotatePreferences(
        styles = settings.styles.mapNotNull { (key, style) ->
            val tool = ToolKind.entries.firstOrNull { it.name == key } ?: return@mapNotNull null
            tool to style
        }.toMap(),
    )

    fun toSettings(preferences: AnnotatePreferences): AnnotateSettings = AnnotateSettings(
        styles = preferences.styles.mapKeys { (tool, _) -> tool.name },
    )
}
