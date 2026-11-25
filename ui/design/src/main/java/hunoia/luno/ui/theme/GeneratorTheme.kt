package hunoia.luno.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.ui.component.NoOpIndication

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 动效档位随配置流更新：在这里读 state，主题内任意面板重新打开时都会拿到新 spec。
    // 设置页预览在子树里再 provide，覆盖的是预览副本，不写全局。
    val motionProfile = MotionTuning.profile
    // 外观（圆角 / 玻璃 / 间距）随配置流更新：读一次 settings，M3 形状与整棵子树跟着重组。
    val appearance = UiAppearance.settings
    // 形状重建只在设置真的变了时发生（避免每帧校验/分配）。
    val shapes = remember(appearance) { UiAppearance.materialShapes() }
    // 主题色：默认跟随系统；选色板时用 seed 生成完整 light/dark scheme。
    // 自定义色板变化也要重建，所以显式读 customPalettes。
    val customPalettes = UiAppearance.customPalettes
    val palette = remember(appearance.theme, customPalettes) {
        LunoPaletteScheme.resolve(appearance.theme, customPalettes)
    }
    val colorScheme = if (palette != null) {
        remember(palette.id, palette.colors, palette.rainbow, palette.rainbowHueOffset, darkTheme) {
            LunoPaletteScheme.schemeFor(palette, darkTheme)
        }
    } else if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    // 全站关闭 ripple：点击反馈统一改成按压缩放（ui-guidelines §5.3）。
    // `LocalRippleConfiguration = null` 让 M3 组件不再画 ripple；
    // `LocalIndication` 换成空指示，兜住仍然走默认 indication 的 clickable。
    // 反馈来源：Modifier.pressClickable / Modifier.pressScale 与 Luno* 按钮包装。
    CompositionLocalProvider(
        LocalIndication provides NoOpIndication,
        LocalRippleConfiguration provides null,
        LocalMotionProfile provides motionProfile,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            // M3 1.5 Expressive 动效语义：sheet / menu / chip / switch / FAB 的内部 morph
            // 统一走 expressive 曲线（ui-guidelines §5.1）。Luno 自己的 Motion* 档位不受影响。
            motionScheme = MotionScheme.expressive(),
            // M3 Shapes 要求 CornerBasedShape，不能用 Shape 类型的 token；同一份用户设置在此重建。
            shapes = shapes,
            content = content,
        )
    }
}
