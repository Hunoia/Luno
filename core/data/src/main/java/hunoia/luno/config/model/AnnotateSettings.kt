package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 屏幕标注的用户偏好：每工具风格。笔迹不落盘（见计划 §3.3）。
 *
 * 键用 `ToolKind.name`：配置层不依赖 feature 模块，读取侧做映射；
 * 未知键（旧版本残留 / 新版本新增工具）读取时直接忽略。
 */
@Serializable
@Keep
data class AnnotateSettings(
    val styles: Map<String, AnnotateToolStyle> = emptyMap(),
) {
    /** 全默认 = 没存过偏好；用于备份域的空域判定。 */
    fun isEmpty(): Boolean = styles.isEmpty()
}

/**
 * 单个工具的颜色 / 线宽。颜色用 ARGB Long（Compose Color.value 也是同口径），
 * 避免模型层依赖 Compose，导出侧可直接转 `android.graphics.Color`。
 *
 * 字段默认值与 annotate 模块的默认工具风格一致（PEN：3f 屏幕 dp，见 `AnnotateState.DEFAULT_STYLES`）。
 */
@Serializable
@Keep
data class AnnotateToolStyle(
    val colorArgb: Long = 0xFFFF3B30,
    /**
     * 笔迹 / 线框宽度，**屏幕 dp**（B9）：落笔瞬间按 `dp × density ÷ fit.scale`
     * 换算成图片像素存进元素，所以在不同分辨率底图上手感一致。
     */
    val strokeWidth: Float = 3f,
    /** 文字大小，图片像素。 */
    val textSize: Float = 48f,
    /** 马赛克单元边长，图片像素。 */
    val mosaicCell: Float = 24f,
    /** 形状是否半透明填充（SHAPE 工具）：新画的形状带填充 + 描边；旧配置缺字段时默认 false。 */
    val shapeFilled: Boolean = false,
    /** 模糊半径（BLUR 工具），图片像素；旧配置缺字段时默认 16。 */
    val blurRadius: Float = 16f,
)
