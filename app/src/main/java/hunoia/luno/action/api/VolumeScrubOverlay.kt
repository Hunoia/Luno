package hunoia.luno.action.api

import hunoia.luno.bridge.window.windowManager

import android.content.Context
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import hunoia.luno.bridge.volumeDown
import hunoia.luno.bridge.volumeUp

class VolumeScrubOverlay(
    private val context: Context,
    private val horizontalEnabled: Boolean = false,
    private val stepThresholdDp: Int = 18,
) {

    private var overlayView: View? = null
    private var lastY = 0f
    private var lastX = 0f
    private var accumulator = 0f
    private var accumulatorX = 0f

    private val stepThresholdPx: Float

    init {
        stepThresholdPx = stepThresholdDp * context.resources.displayMetrics.density
    }

    fun show(onDismiss: () -> Unit): Boolean {
        if (overlayView != null) return false

        overlayView = View(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastY = event.rawY
                        lastX = event.rawX
                        accumulator = 0f
                        accumulatorX = 0f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.rawY - lastY
                        lastY = event.rawY
                        accumulator += deltaY
                        while (accumulator >= stepThresholdPx) {
                            context.volumeDown()
                            accumulator -= stepThresholdPx
                        }
                        while (accumulator <= -stepThresholdPx) {
                            context.volumeUp()
                            accumulator += stepThresholdPx
                        }
                        if (horizontalEnabled) {
                            val deltaX = event.rawX - lastX
                            lastX = event.rawX
                            accumulatorX += deltaX
                            while (accumulatorX >= stepThresholdPx) {
                                context.volumeUp()
                                accumulatorX -= stepThresholdPx
                            }
                            while (accumulatorX <= -stepThresholdPx) {
                                context.volumeDown()
                                accumulatorX += stepThresholdPx
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        dismiss()
                        onDismiss()
                        true
                    }
                    else -> false
                }
            }
        }

        val wm = context.windowManager()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(overlayView, params)
        return true
    }

    fun dismiss() {
        overlayView?.let { view ->
            overlayView = null
            context.windowManager().removeViewImmediate(view)
        }
        lastY = 0f
        lastX = 0f
        accumulator = 0f
        accumulatorX = 0f
    }
}
