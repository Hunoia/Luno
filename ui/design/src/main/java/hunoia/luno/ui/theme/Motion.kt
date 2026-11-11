package hunoia.luno.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import hunoia.luno.config.model.MotionSpeed
import hunoia.luno.config.model.MotionTuningSettings
import kotlin.math.roundToInt

/**
 * 动效家族（ui-guidelines §5.1）：调用点只能选家族，不能自己写时长。
 *
 * [baseMillis] 是标准档（1.0×）下的基础时长；进 / 退同长同曲线（对称）。
 * 只覆盖面板 / 浮层 / 浮窗三类；按压缩放、落位、页面切换等交互反馈不参与档位。
 */
enum class MotionFamily(val baseMillis: Int) {
    /** 卡片面板：网格面板、快捷启动、动作面板、AnchoredDialog 卡片。 */
    PanelCard(200),

    /** 全屏面板：快捷搜索。 */
    PanelFull(240),

    /** 浮层：运行时状态浮层、指针光标、音量条、TransientOverlay。 */
    Overlay(150),

    /** 浮窗：悬浮球显隐 / 让位 / 回场、气泡显隐。 */
    Floating(200),

    /** 浮窗气泡 morph（尺寸 + alpha）。 */
    FloatingMorph(180),

    /** 浮窗气泡 snap（位置 + alpha）。 */
    FloatingSnap(170),
}

/** 一次动效的完整 spec：时长 + 缓动。对称由调用点用同一 spec 正反播放保证。 */
data class MotionSpec(
    val durationMillis: Int,
    val easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing,
)

/** 三类档位换算后的家族时长。 */
data class MotionProfile(
    val panel: MotionSpeed = MotionSpeed.Standard,
    val overlay: MotionSpeed = MotionSpeed.Standard,
    val floating: MotionSpeed = MotionSpeed.Standard,
) {
    fun speedOf(family: MotionFamily): MotionSpeed = when (family) {
        MotionFamily.PanelCard, MotionFamily.PanelFull -> panel
        MotionFamily.Overlay -> overlay
        MotionFamily.Floating, MotionFamily.FloatingMorph, MotionFamily.FloatingSnap -> floating
    }

    fun millisOf(family: MotionFamily): Int =
        (family.baseMillis * speedOf(family).factor).roundToInt().coerceAtLeast(1)

    fun specOf(family: MotionFamily): MotionSpec = MotionSpec(millisOf(family))

    companion object {
        val Standard = MotionProfile()
    }
}

fun MotionTuningSettings.toProfile(): MotionProfile = MotionProfile(panel, overlay, floating)

/**
 * 进程级动效档位。App 启动时订阅配置流写入；原生服务（`ViewPropertyAnimator` /
 * `ValueAnimator`）没有 CompositionLocal，直接读这里。
 *
 * 「下次打开生效」：已开着的窗口保持原时长，不做运行中重播。
 */
object MotionTuning {
    var profile: MotionProfile by mutableStateOf(MotionProfile.Standard)
}

/**
 * Compose 侧读取点。默认回落到 [MotionTuning.profile]；需要跟随档位重组的子树
 * （如设置页预览）显式 provide，避免全局状态被写成动态依赖。
 */
val LocalMotionProfile = staticCompositionLocalOf { MotionTuning.profile }

/** 当前进程的原生动效时长（ms），已乘应用档位、未乘系统缩放（原生 API 自带）。 */
fun motionMillis(family: MotionFamily): Int = MotionTuning.profile.millisOf(family)

/**
 * 面板退场播完后拆窗 / 缩窗的编排延迟（ms）：必须由家族时长推导。
 *
 * 写死常量会在「慢」档（1.3× / 1.7×）比退场动画短，窗口在动画中途被拆掉——
 * 表现就是退场被截断（快捷启动、动作面板都踩过）。+40ms 留给最后一帧提交。
 */
fun panelTeardownDelayMillis(): Long = motionMillis(MotionFamily.PanelCard) + 40L

/** 面板家族 spec：卡片面板默认，[fullScreen] 走全屏基础值。 */
@Composable
fun panelSpec(fullScreen: Boolean = false): MotionSpec =
    LocalMotionProfile.current.specOf(
        if (fullScreen) MotionFamily.PanelFull else MotionFamily.PanelCard,
    )

/** 浮层家族 spec（纯 alpha）。 */
@Composable
fun overlaySpec(): MotionSpec = LocalMotionProfile.current.specOf(MotionFamily.Overlay)

/** 浮窗家族 spec。 */
@Composable
fun floatingSpec(family: MotionFamily = MotionFamily.Floating): MotionSpec =
    LocalMotionProfile.current.specOf(family)

/** 把 spec 转成 Compose 的 tween（时长已含应用档位，系统缩放由 Recomposer 自动乘）。 */
fun <T> MotionSpec.tween(): TweenSpec<T> = tween(durationMillis, easing = easing)
