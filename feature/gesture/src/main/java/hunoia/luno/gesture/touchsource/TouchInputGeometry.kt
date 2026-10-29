package hunoia.luno.gesture.touchsource

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.model.GestureButton
import hunoia.luno.gesture.bounds
import hunoia.luno.gesture.effectiveWindowButton
import hunoia.luno.gesture.mirroredButton

/** 一次触摸的合法来源。同一次手势只归一个来源所有。 */
enum class TouchSourceKind {
    /** 无障碍按钮窗投递的 `MotionEvent`。 */
    WINDOW,

    /** Shizuku shell 读内核输入设备拿到的触摸帧。 */
    SHIZUKU,
}

/** 一个按钮窗（或它的镜像）在屏幕像素坐标系里的矩形。 */
data class TouchButtonRect(
    val buttonId: String,
    val mirrored: Boolean,
    val rect: Rect,
)

/** 拦截窗几何：Shizuku 只“吃掉”这一块，其余像素仍归下层/无障碍窗。 */
data class InterceptRegion(
    val buttonId: String,
    val mirrored: Boolean,
    val rect: Rect,
)

/**
 * 触钮区域与拦截区的唯一几何来源。
 *
 * 拦截窗按这里的位置摆放，来源判定也按这里裁决——两处各算一遍就会漂移，
 * 表现是“窗在这儿、判定在那儿”，边缘点击随机丢失。
 */
object TouchInputGeometry {

    /**
     * 所有按钮窗（含镜像）的矩形，取的是**实际生效的触摸区域**
     *（配了内核触摸流且拦截宽度更窄时就是那条窄带）。
     *
     * 窗口摆放与来源判定共用它，所以“按钮只响应条带内”两处一致、不会漂移。
     */
    fun buttonRects(
        buttons: List<GestureButton>,
        imePadding: Int = 0,
        screenWidth: Int = DensityProvider.screenWidthPx,
        screenHeight: Int = DensityProvider.screenHeightPx,
        density: Float = DensityProvider.density,
    ): List<TouchButtonRect> = buildList {
        buttons.forEach { button ->
            val effective = button.effectiveWindowButton(screenWidth, density)
            add(button.toTarget(effective, imePadding, screenWidth, screenHeight, mirrored = false))
            val mirrored = effective.mirroredButton() ?: return@forEach
            if (mirrored.bounds != effective.bounds) {
                add(button.toTarget(mirrored, imePadding, screenWidth, screenHeight, mirrored = true))
            }
        }
    }

    /**
     * 拦截区：只覆盖「把触摸来源配成 Shizuku」的按钮，矩形与它们的生效触摸区完全一致
     *（默认整块触发区，宽度可缩到贴边一条）。没配 Shizuku 的按钮一个像素都不吃。
     */
    fun interceptRegions(
        buttons: List<GestureButton>,
        imePadding: Int = 0,
        screenWidth: Int = DensityProvider.screenWidthPx,
        screenHeight: Int = DensityProvider.screenHeightPx,
        density: Float = DensityProvider.density,
    ): List<InterceptRegion> {
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()
        return buttonRects(
            buttons = buttons.filter { it.usesShizukuStream },
            imePadding = imePadding,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        ).map { InterceptRegion(it.buttonId, it.mirrored, it.rect) }
    }

    /** 这个按钮的触摸是否可能来自内核触摸流（AUTO / ONLY_SHIZUKU）。 */
    private val GestureButton.usesShizukuStream: Boolean
        get() = touchInput.mode != hunoia.luno.config.model.TouchInputMode.ONLY_ACCESSIBILITY

    /** 点是否落在某个来源的辖区里。 */
    fun contains(regions: List<InterceptRegion>, point: Offset): InterceptRegion? =
        regions.firstOrNull { it.rect.contains(point) }

    fun containsButton(rects: List<TouchButtonRect>, point: Offset): TouchButtonRect? =
        rects.firstOrNull { it.rect.contains(point) }
}

/**
 * 与 `GestureButton.bounds(imePadding)` 同一口径：按屏幕比例换算，键盘遮挡量把整块上移。
 * 这里写成显式参数是为了能在 JVM 单测里直接验几何，不依赖 AppContext。
 */
private fun GestureButton.toTarget(
    windowButton: GestureButton,
    imePadding: Int,
    screenWidth: Int,
    screenHeight: Int,
    mirrored: Boolean,
): TouchButtonRect {
    val left = screenWidth * windowButton.bounds.x
    val top = screenHeight * windowButton.bounds.y - imePadding
    return TouchButtonRect(
        buttonId = id,
        mirrored = mirrored,
        rect = Rect(
            left = left,
            top = top,
            right = left + screenWidth * windowButton.bounds.width,
            bottom = top + screenHeight * windowButton.bounds.height,
        ),
    )
}
