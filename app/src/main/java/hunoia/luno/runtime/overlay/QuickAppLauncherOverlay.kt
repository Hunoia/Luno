package hunoia.luno.runtime.overlay

import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.util.Log
import android.view.MotionEvent
import hunoia.luno.BuildConfig
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.bridge.window.applyOverlayViewTreeOwners
import hunoia.luno.bridge.window.windowManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.max
import hunoia.luno.ui.theme.AnimOverlayFade

interface QuickAppLauncherOverlayHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    val context: Context
    val coroutineScope: CoroutineScope
    val advancedSettings: AdvancedSettings?

    fun requestEnableDisabledPackage(packageName: String, onResult: (Boolean) -> Unit)
}

class QuickAppLauncherOverlay(private val host: QuickAppLauncherOverlayHost) {
    @Volatile private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    @Volatile private var isShowing = false
    private var isHiding = false
    private var triggerCloseAnimated: (() -> Unit)? = null
    private var lastCloseMs: Long = 0L
    @Volatile private var pendingJob: Job? = null
    var onAppLaunchRequested: ((AppInfo) -> Unit)? = null

    fun toggle() {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher","toggle: overlayView=${overlayView != null}")
        if (overlayView != null) {
            close()
        } else {
            show()
        }
    }

    private fun close() {
        if (overlayView == null) {
            val cancelled = cancelPendingShow()
            if (BuildConfig.DEBUG) Log.d("LunoLauncher", "close: no overlay (cancelledPendingShow=$cancelled)")
            return
        }
        if (isHiding) {
            if (BuildConfig.DEBUG) Log.d("LunoLauncher", "close: skipped (already hiding)")
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
        cancelPendingShow()
        if (overlayView == null) {
            return
        }
        isShowing = false
        isHiding = true
        lastCloseMs = System.currentTimeMillis()
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "closeImmediately: removing overlay")
        removeOverlayView()
    }

    private fun cancelPendingShow(): Boolean {
        val job = pendingJob ?: return false
        pendingJob = null
        job.cancel()
        if (overlayView == null) isShowing = false
        return true
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

        pendingJob = host.coroutineScope.launch {
            try {
                val initialSettings = loadSettingsAsync()
                withContext(Dispatchers.Main.immediate) {
                    if (!isActive) return@withContext
                    showOverlayView(initialSettings)
                }
            } finally {
                if (pendingJob === coroutineContext[Job]) pendingJob = null
                if (overlayView == null) isShowing = false
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
                        requestEnableDisabledPackage = host::requestEnableDisabledPackage,
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
        if (!wm.safeAddView(composeView, lp)) {
            isShowing = false
            return
        }
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
