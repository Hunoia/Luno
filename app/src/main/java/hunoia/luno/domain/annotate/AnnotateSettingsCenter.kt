package hunoia.luno.domain.annotate

import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.ToolStyle
import hunoia.luno.annotate.model.ToolbarAnchor
import hunoia.luno.annotate.store.AnnotatePreferences
import hunoia.luno.annotate.store.AnnotateSettingsStore
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AnnotateSettings
import hunoia.luno.config.model.AnnotateToolStyle

/**
 * 标注偏好门面：feature 模块只认 [AnnotateSettingsStore] 端口，配置层读写集中在这里。
 *
 * 与 `ActionLibrary` / `GridPanelCenter` 同构：配置访问不散落在 UI 里。
 * 读失败按默认值处理（标注本身不依赖偏好）；写失败静默（偏好不是用户数据）。
 */
object AnnotateSettingsCenter : AnnotateSettingsStore {

    override suspend fun load(): AnnotatePreferences {
        val settings = runCatching { ConfigProvider.getAnnotateSettings() }.getOrNull()
            ?: return AnnotatePreferences()
        return AnnotateSettingsMapper.toPreferences(settings)
    }

    override suspend fun save(preferences: AnnotatePreferences) {
        runCatching {
            ConfigProvider.updateAnnotateSettings { AnnotateSettingsMapper.toSettings(preferences) }
        }
    }
}

/**
 * 配置层 DTO ↔ 标注模型映射。
 *
 * 落盘键用 `ToolKind.name`：core:data 不认识标注工具枚举，读取侧再做映射；
 * 未知键（旧版本残留 / 新版本新增工具）直接忽略，跨版本不会解码失败。
 */
internal object AnnotateSettingsMapper {

    fun toPreferences(settings: AnnotateSettings): AnnotatePreferences = AnnotatePreferences(
        styles = settings.styles.mapNotNull { (key, dto) ->
            val tool = ToolKind.entries.firstOrNull { it.name == key } ?: return@mapNotNull null
            tool to dto.toStyle()
        }.toMap(),
        anchor = toAnchor(settings),
    )

    fun toSettings(preferences: AnnotatePreferences): AnnotateSettings = AnnotateSettings(
        styles = preferences.styles.mapKeys { (tool, _) -> tool.name }
            .mapValues { (_, style) -> style.toDto() },
        // source: 两个比例都存过才算有效位置。
        toolbarXFraction = preferences.anchor?.xFraction,
        toolbarYFraction = preferences.anchor?.yFraction,
    )

    /** 两个比例都存过才算有效位置。 */
    private fun toAnchor(settings: AnnotateSettings): ToolbarAnchor? {
        val x = settings.toolbarXFraction ?: return null
        val y = settings.toolbarYFraction ?: return null
        return ToolbarAnchor(x, y)
    }
}

private fun AnnotateToolStyle.toStyle(): ToolStyle = ToolStyle(
    colorArgb = colorArgb,
    strokeWidth = strokeWidth,
    textSize = textSize,
    mosaicCell = mosaicCell,
)

private fun ToolStyle.toDto(): AnnotateToolStyle = AnnotateToolStyle(
    colorArgb = colorArgb,
    strokeWidth = strokeWidth,
    textSize = textSize,
    mosaicCell = mosaicCell,
)
