package hunoia.luno.bridge.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.graphics.Point
import hunoia.luno.bridge.DensityProvider

object Accessibility {

    fun click(service: AccessibilityService?, x: Int, y: Int): Boolean {
        if (service == null) return false
        val point = Point(x, y)
        val builder = GestureDescription.Builder()
        val path = Path().apply {
            moveTo(point.x.toFloat(), point.y.toFloat())
        }
        builder.addStroke(StrokeDescription(path, 0L, 100L))
        return service.dispatchGesture(builder.build(), null, null)
    }

    fun longPress(service: AccessibilityService?, x: Int, y: Int): Boolean {
        if (service == null) return false
        val point = Point(x, y)
        val builder = GestureDescription.Builder()
        val path = Path().apply {
            moveTo(point.x.toFloat(), point.y.toFloat())
        }
        builder.addStroke(StrokeDescription(path, 0L, 1000L))
        return service.dispatchGesture(builder.build(), null, null)
    }

    fun doubleTap(service: AccessibilityService?, x: Int, y: Int): Boolean {
        if (service == null) return false
        val point = Point(x, y)
        val builder = GestureDescription.Builder()
        val path = Path().apply {
            moveTo(point.x.toFloat(), point.y.toFloat())
        }
        builder.addStroke(StrokeDescription(path, 0L, 100L))
        val path2 = Path().apply {
            moveTo(point.x.toFloat(), point.y.toFloat())
        }
        builder.addStroke(StrokeDescription(path2, 250L, 100L))
        return service.dispatchGesture(builder.build(), null, null)
    }

    fun fastVerticalScroll(
        service: AccessibilityService?,
        toTop: Boolean,
    ): Boolean {
        service ?: return false
        val screenWidth = DensityProvider.screenWidthPx
        val screenHeight = DensityProvider.screenHeightPx
        val point = Point(screenWidth / 2, screenHeight / 2)
        val builder = GestureDescription.Builder()
        val path = Path()
        if (toTop) {
            path.moveTo(point.x.toFloat(), point.y.toFloat())
            path.lineTo(point.x.toFloat(), point.y.toFloat() + Int.MAX_VALUE)
        } else {
            path.moveTo(point.x.toFloat(), point.y.toFloat())
            path.lineTo(point.x.toFloat(), point.y.toFloat() - Int.MAX_VALUE)
        }
        builder.addStroke(StrokeDescription(path, 0L, 100L))
        return service.dispatchGesture(builder.build(), null, null)
    }
}
