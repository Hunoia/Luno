package hunoia.luno.runtime.overlay

import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.util.Log
import android.view.MotionEvent
import hunoia.luno.BuildConfig
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import hunoia.luno.R
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.bridge.window.applyOverlayViewTreeOwners
import hunoia.luno.bridge.window.windowManager
import hunoia.luno.quicklaunch.query.AppSearch.key
import hunoia.luno.config.ConfigProvider
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.quicklaunch.QuickAppLauncherContent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.max
import androidx.compose.animation.core.animateFloatAsState
import hunoia.luno.ui.theme.AnimOverlayFade
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

interface QuickAppLauncherOverlayHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    val context: Context
    val coroutineScope: CoroutineScope
    val advancedSettings: AdvancedSettings?

    fun requestEnableFrozenPackage(packageName: String, onResult: (Boolean) -> Unit)
}

class QuickAppLauncherOverlay(private val host: QuickAppLauncherOverlayHost) {
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isShowing = false
    private var isHiding = false
    private var triggerCloseAnimated: (() -> Unit)? = null
    private var lastCloseMs: Long = 0L
    var onAppLaunchRequested: ((AppInfo) -> Unit)? = null

    fun toggle() {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher","toggle: overlayView=${overlayView != null}")
        if (overlayView != null) {
            close()
        } else {
            show()
        }
    }

    fun close() {
        if (isHiding || overlayView == null) {
            if (BuildConfig.DEBUG) Log.d("LunoLauncher","close: skipped (isHiding=$isHiding overlayView=${overlayView != null})")
            return
        }
        val reason = "explicit close"
        isHiding = true
        lastCloseMs = System.currentTimeMillis()
        if (BuildConfig.DEBUG) Log.d("LunoLauncher","close: reason=$reason hasAnimation=${triggerCloseAnimated != null}")
        if (triggerCloseAnimated != null) {
            triggerCloseAnimated?.invoke()
        } else {
            overlayView?.let { animateOut(it) }
        }
    }

    fun closeImmediately() {
        if (overlayView == null) {
            return
        }
        isShowing = false
        isHiding = true
        lastCloseMs = System.currentTimeMillis()
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "closeImmediately: removing overlay")
        removeOverlayView()
    }

    private fun animateOut(view: View) {
        view.animate()
            .alpha(0f)
            .setDuration(AnimOverlayFade)
            .withEndAction {
                removeOverlayView()
            }
    }

    private fun removeOverlayView() {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher","removeOverlayView: removing overlay")
        overlayView?.let {
            it.animate().cancel()
            it.alpha = 1f
            val wm = host.context.windowManager()
            runCatching { wm.removeView(it) }
        }
        overlayView = null
        overlayParams = null
        isHiding = false
        triggerCloseAnimated = null
    }

    fun show() {
        val now = System.currentTimeMillis()
        val interval = if (lastCloseMs > 0) now - lastCloseMs else -1L
        if (BuildConfig.DEBUG) Log.d("LunoLauncher","show: isShowing=$isShowing isHiding=$isHiding overlayView=${overlayView != null} intervalSinceLastClose=${interval}ms")
        if (isShowing || isHiding || overlayView != null) return
        isShowing = true

        cleanupExistingOverlay()

        host.coroutineScope.launch {
            val initialSettings = loadSettingsAsync()
            withContext(Dispatchers.Main.immediate) {
                showOverlayView(initialSettings)
            }
        }
    }

    private suspend fun loadSettingsAsync() = withContext(Dispatchers.IO) {
        ConfigProvider.getQuickAppLauncherSettings()
    }

    private fun cleanupExistingOverlay() {
        if (overlayView != null && !isHiding) {
            val wm = host.context.windowManager()
            runCatching { overlayView?.let { wm.removeView(it) } }
            overlayView = null
            overlayParams = null
        }
    }

    private fun showOverlayView(initialSettings: QuickAppLauncherSettings) {
        val wm = host.context.windowManager()
        val lp = createLayoutParams(initialSettings)
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)

            setOnTouchListener(createDismissOnOutsideTouch(onOutsideTouch = {
                close()
            }, logTag = "touch"))

            setContent {
                val advancedSettings = host.advancedSettings ?: AdvancedSettings()
                SideGestureTheme {
                    QuickAppLauncherContent(
                        initialSettings = initialSettings,
                        requestEnableFrozenPackage = host::requestEnableFrozenPackage,
                        onCloseAnimated = {
                            isShowing = false
                            isHiding = true
                            lastCloseMs = System.currentTimeMillis()
                            if (BuildConfig.DEBUG) Log.d("LunoLauncher","closeAnimated: triggered")
                            removeOverlayView()
                        },
                        onUpdateLayout = { settings -> updateLayout(settings) },
                        onLaunch = { appInfo, miniWindow ->
                            val now = System.currentTimeMillis()
                            val interval = if (lastCloseMs > 0) now - lastCloseMs else -1L
                            if (BuildConfig.DEBUG) Log.d("LunoLauncher","appClick: ${appInfo.label} pkg=${appInfo.packageName} miniWindow=$miniWindow intervalSinceClose=${interval}ms")
                            val success = if (advancedSettings.miniWindowOverrideBounds) {
                                Launcher.launchAppInfo(
                                    host.context, appInfo, miniWindow,
                                    advancedSettings.miniWindowHorizontalBias,
                                    advancedSettings.miniWindowVerticalBias,
                                    advancedSettings.miniWindowVerticalOffsetFraction,
                                    advancedSettings.miniWindowWidthFraction,
                                    advancedSettings.miniWindowHeightFraction,
                                    overrideBounds = true,
                                )
                            } else {
                                Launcher.launchAppInfo(
                                    host.context, appInfo, miniWindow,
                                )
                            }
                            if (BuildConfig.DEBUG) Log.d("LunoLauncher","appClick: ${appInfo.label} launchResult=$success")
                            if (success) onAppLaunchRequested?.invoke(appInfo)
                            success
                        },
                        onRegisterCloseAnimated = { callback -> triggerCloseAnimated = callback }
                    )
                }
            }
        }
        wm.addView(composeView, lp)
        overlayView = composeView
        overlayParams = lp
        isShowing = false
    }


    private fun createLayoutParams(settings: QuickAppLauncherSettings) = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.RGBA_8888
        height = WindowManager.LayoutParams.WRAP_CONTENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        @Suppress("DEPRECATION")
        gravity = Gravity.START or Gravity.TOP
        applyPanelLayout(settings)
    }

    private fun WindowManager.LayoutParams.applyPanelLayout(settings: QuickAppLauncherSettings) {
        val screenWidth = DensityProvider.screenWidthPx
        val screenHeight = DensityProvider.screenHeightPx
        val panelWidth = (screenWidth * settings.panelWidthFraction.coerceIn(0.65f, 1f)).roundToInt().coerceIn(1, screenWidth)
        val estimatedHeight = estimatePanelHeightPx(settings).coerceAtMost(screenHeight)
        width = panelWidth
        x = ((screenWidth - panelWidth) * settings.panelHorizontalBias.coerceIn(0f, 1f)).roundToInt().coerceIn(0, screenWidth - panelWidth)
        val maxY = max(0, screenHeight - estimatedHeight)
        y = (screenHeight * (1f - settings.panelHeightFraction.coerceIn(0.05f, 0.95f))).roundToInt()
            .coerceIn(0, maxY)
        height = estimatedHeight
    }

    private fun estimatePanelHeightPx(settings: QuickAppLauncherSettings): Int {
        return (DensityProvider.screenHeightPx * settings.contentHeightFraction.coerceIn(0.35f, 0.85f)).roundToInt()
    }

    private fun updateLayout(settings: QuickAppLauncherSettings) {
        val view = overlayView ?: return
        val lp = overlayParams ?: return
        lp.applyPanelLayout(settings)
        runCatching {
            val wm = host.context.windowManager()
            wm.updateViewLayout(view, lp)
        }
    }

}

private fun createDismissOnOutsideTouch(
    onOutsideTouch: () -> Unit,
    logTag: String,
): View.OnTouchListener = View.OnTouchListener { v, event ->
    if (event.action == MotionEvent.ACTION_OUTSIDE) {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "$logTag: ACTION_OUTSIDE at (${event.rawX.toInt()}, ${event.rawY.toInt()}) → close")
        onOutsideTouch()
        v.performClick()
        true
    } else {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "$logTag: action=${event.action} at (${event.rawX.toInt()}, ${event.rawY.toInt()})")
        false
    }
}
