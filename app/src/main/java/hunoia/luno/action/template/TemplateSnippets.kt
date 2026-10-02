package hunoia.luno.action.template

/** 静态预设片段：插入后就是普通文本，不参与运行时替换。 */
data class TemplateSnippet(
    val text: String,
    val label: String,
)

object TemplateSnippets {
    val settingsKeys: List<TemplateSnippet> = listOf(
        TemplateSnippet("window_animation_scale", "窗口动画缩放"),
        TemplateSnippet("transition_animation_scale", "过渡动画缩放"),
        TemplateSnippet("animator_duration_scale", "动画时长缩放"),
        TemplateSnippet("screen_brightness", "屏幕亮度"),
        TemplateSnippet("screen_off_timeout", "屏幕超时"),
        TemplateSnippet("accelerometer_rotation", "自动旋转"),
        TemplateSnippet("time_12_24", "24 小时制"),
        TemplateSnippet("airplane_mode_on", "飞行模式"),
        TemplateSnippet("wifi_on", "WiFi 开关"),
        TemplateSnippet("bluetooth_on", "蓝牙开关"),
    )
}
