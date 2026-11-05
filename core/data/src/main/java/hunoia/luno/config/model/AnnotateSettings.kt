package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 屏幕标注的用户偏好：每工具风格 + 工具条位置。笔迹不落盘（见计划 §3.3）。
 *
 * 键用 `ToolKind.name`：配置层不依赖 feature 模块，读取侧做映射；
 * 未知键（旧版本残留 / 新版本新增工具）读取时直接忽略。
 */
@Serializable
@Keep
data class AnnotateSettings(
    val styles: Map<String, AnnotateToolStyle> = emptyMap(),
    /** 工具条窗口左边 / 屏宽。null = 默认居中贴底。 */
    val toolbarXFraction: Float? = null,
    /** 工具条窗口底边 / 屏高（底边锚定：面板开合不影响工具条）。 */
    val toolbarYFraction: Float? = null,
) {
    /** 全默认 = 没存过偏好；用于备份域的空域判定。 */
    fun isEmpty(): Boolean =
        styles.isEmpty() && toolbarXFraction == null && toolbarYFraction == null
}

/** 单个工具的风格；字段默认值与 annotate 模块的 PEN 默认一致。 */
@Serializable
@Keep
data class AnnotateToolStyle(
    val colorArgb: Long = 0xFFFF3B30,
    val strokeWidth: Float = 6f,
    val textSize: Float = 48f,
    val mosaicCell: Float = 24f,
)
