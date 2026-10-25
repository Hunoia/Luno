package hunoia.luno.runtime.floatball

import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.config.model.NotificationCapsuleSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.quicklaunch.icon.SearchIconCache
import hunoia.luno.runtime.condition.NotificationPulse
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 通知胶囊：TYPE_APPLICATION_OVERLAY 小窗，通知到达时按设置侧贴屏幕底部弹出
 * 「应用图标 + 标题 + 正文」，点击（[onTapped]）由 Service 发 contentIntent 或启动应用，
 * 自动收起计时归 Service。低频出现，走整拆整建（show 挂窗、hide 淡出后摘窗）；
 * 可见期间来新通知只换 Compose 态内容，不重播进场。
 * 动效一律 View 层（服务进程 ComposeView 重组不上屏，见 FloatBallWindow.applyVisual 注释）。
 */
internal class NotificationCapsuleWindow(private val context: android.content.Context) {

    private val wm: WindowManager get() = context.windowManager()
    private var view: View? = null

    // Compose 态：内容更新走 mutableStateOf 重组（镜像窗同款前提），普通字段不重绘。
    private var current by mutableStateOf<CapsuleItem?>(null)

    private var settings: NotificationCapsuleSettings = NotificationCapsuleSettings()

    private val widthPx: Int get() = DensityProvider.dp2px(CapsuleWidthDp)
    private val heightPx: Int get() = DensityProvider.dp2px(CapsuleHeightDp)

    /** LP 坐标系原点在屏幕上的 y（状态栏下沿），attach 后实测（与球窗同套路）。 */
    private var lpOriginY = 0

    /** 底部安全边距的导航栏部分：show 时从系统资源读，读不到按兜底。 */
    private var navBarInsetPx = 0

    /** 进场动画在飞：期间按压不播缩放（同一 ViewPropertyAnimator，会取消进场卡在半透明）。 */
    private var entering = false

    /** 点击胶囊（未超 touchSlop 抬手）。 */
    var onTapped: (() -> Unit)? = null

    private val arbiter = FloatBallTouchArbiter(
        onSlideDragged = { _, _ -> },
        onTapped = { onTapped?.invoke() },
        onPressChanged = { pressed ->
            if (entering) return@FloatBallTouchArbiter
            view?.let { v ->
                val scale = if (pressed) CapsulePressScale else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(PRESS_MS).start()
            }
        },
        onDragEnded = {},
    )

    private val screenWidthPx: Int get() = DensityProvider.screenWidthPx
    private val screenHeightPx: Int get() = DensityProvider.screenHeightPx

    fun updateSettings(next: FloatBallSettings) {
        settings = next.notificationCapsule
    }

    /** 弹出/刷新：不可见则挂窗播进场；已可见只换内容（新通知覆盖 + Service 重新计时）。 */
    fun show(pulse: NotificationPulse) {
        current = CapsuleItem(pulse.packageName, pulse.title, pulse.text)
        val existing = view
        if (existing != null) {
            applyWindowBounds()
            return
        }
        navBarInsetPx = readNavBarInsetPx()
        val created = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            FloatBallLifecycleOwner.createAndAttachTo(this)
            setContent { CapsuleContent() }
        }
        created.setOnTouchListener { _, event -> arbiter.onTouch(event) }
        if (!wm.safeAddView(created, capsuleLayoutParams(widthPx, heightPx))) {
            Log.e(TAG, "notification capsule addView failed (overlay permission or window rejected)")
            return
        }
        view = created
        applyWindowBounds()
        postMeasureLpOriginY({ view }, { lpOriginY }) {
            lpOriginY = it
            applyWindowBounds()
        }
        // 进场：从自身高度处滑入 + 淡入；scale 复位兜底按压动画被取消的残留。
        entering = true
        created.scaleX = 1f
        created.scaleY = 1f
        created.translationY = heightPx.toFloat()
        created.alpha = 0f
        created.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { entering = false }
            .start()
    }

    /** 收起：淡出 + 滑回底部后整窗摘除（内容保留到淡完，别在半路清空闪白）。 */
    fun hide(onHidden: (() -> Unit)? = null) {
        val v = view
        view = null
        entering = false
        if (v == null) {
            onHidden?.invoke()
            return
        }
        v.animate().cancel()
        applyBallTouchFlags(v, wm, hidden = true)
        v.animate()
            .translationY(heightPx.toFloat())
            .alpha(0f)
            .setDuration(HIDE_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                context.removeWindow(v)
                onHidden?.invoke()
            }
            .start()
    }

    /** 不播动画直接摘窗：服务销毁等即时路径。 */
    fun detachNow() {
        val v = view
        view = null
        entering = false
        current = null
        v?.let(context::removeWindow)
    }

    private fun readNavBarInsetPx(): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val nav = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        return max(nav, DensityProvider.dp2px(CapsuleMinNavBarDp))
    }

    private fun applyWindowBounds() {
        val v = view ?: return
        val sideMargin = DensityProvider.dp2px(CapsuleSideMarginDp).toFloat()
        val x = if (settings.leftSide) sideMargin else (screenWidthPx - widthPx - sideMargin).toFloat()
        val y = screenHeightPx - heightPx - navBarInsetPx -
            DensityProvider.dp2px(CapsuleBottomMarginDp).toFloat()
        writeBallPosition(v, wm, x, y, lpOriginY)
    }

    // ---- Compose ----

    @Composable
    private fun CapsuleContent() {
        SideGestureTheme {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                current?.let { CapsuleFace(item = it) }
            }
        }
    }

    @Composable
    private fun CapsuleFace(item: CapsuleItem) {
        val pill = RoundedCornerShape(percent = 50)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), pill)
                .padding(horizontal = CapsuleInnerPaddingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(packageName = item.packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    private fun AppIcon(packageName: String) {
        val sizePx = DensityProvider.dp2px(CapsuleIconDp)
        val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
            val cached = SearchIconCache.get(packageName, sizePx)
            if (cached != null) {
                value = cached
            } else {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        SearchIconCache.ensureAll(context, listOf(packageName), sizePx)
                        SearchIconCache.get(packageName, sizePx)
                    }.getOrNull()
                }
            }
        }
        val loaded = icon
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(CapsuleIconDp.dp),
            )
        } else {
            Box(modifier = Modifier.size(CapsuleIconDp.dp))
        }
    }

    /** 胶囊当前内容；Compose 态字段，show 更新触发重组。 */
    private data class CapsuleItem(val packageName: String, val title: String, val text: String)

    private companion object {
        const val TAG = LOG_TAG

        const val ENTER_MS = 250L
        const val HIDE_MS = 200L
        const val PRESS_MS = 100L

        const val CapsuleWidthDp = 280f
        const val CapsuleHeightDp = 52f
        const val CapsuleIconDp = 24f
        const val CapsuleInnerPaddingDp = 16f
        const val CapsuleSideMarginDp = 16f
        const val CapsuleBottomMarginDp = 10f
        const val CapsuleMinNavBarDp = 12f
        const val CapsulePressScale = 0.95f
    }
}
