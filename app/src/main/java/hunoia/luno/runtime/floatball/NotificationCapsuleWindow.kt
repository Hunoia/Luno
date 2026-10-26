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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.quicklaunch.icon.SearchIconCache
import hunoia.luno.runtime.condition.NotificationPulse
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 通知胶囊：TYPE_APPLICATION_OVERLAY 小窗，通知到达时屏幕底部居中弹出
 * 「应用图标 + 标题 + 正文」。窗口 WRAP_CONTENT + BOTTOM|CENTER_HORIZONTAL 重力：
 * 尺寸随内容自适应（正文为空只剩标题行则变矮），换内容重排后重力自动保持居中，
 * 全程无手工坐标。点击（[onTapped]）由 Service 发 contentIntent 或启动应用，
 * 自动收起计时归 Service。低频出现，走整拆整建（show 挂窗、hide 滑出后摘窗）；
 * 可见期间来新通知只换 Compose 态内容，不重播进场。
 * 动效一律 View 层（服务进程 ComposeView 重组不上屏，见 FloatBallWindow.applyVisual 注释）。
 */
internal class NotificationCapsuleWindow(private val context: android.content.Context) {

    private val wm: WindowManager get() = context.windowManager()
    private var view: View? = null

    /** 正在播退场动画的窗（view 已置空）：服务销毁时 detachNow 也要能把它摘掉。 */
    private var exitingView: View? = null

    // Compose 态：内容更新走 mutableStateOf 重组（镜像窗同款前提），普通字段不重绘。
    private var current by mutableStateOf<CapsuleItem?>(null)

    /** 整份设置只为了拿主球 accent 色（胶囊配色与剪贴胶囊同源）。 */
    private var settings: FloatBallSettings = FloatBallSettings()

    /** 底部安全边距的导航栏部分：show 时从系统资源读，读不到按兜底。 */
    private var navBarInsetPx = 0

    /** 进场动画在飞：期间按压不播缩放（同一 ViewPropertyAnimator，会取消进场卡在半透明）。 */
    private var entering = false

    /** 等待布局回调补播进场：WRAP_CONTENT 的高度布局后才有。 */
    private var enterPending = false

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

    fun updateSettings(next: FloatBallSettings) {
        settings = next
    }

    /** 弹出/刷新：不可见则挂窗播进场；已可见只换内容（新通知覆盖 + Service 重新计时）。返回是否成功挂窗。 */
    fun show(pulse: NotificationPulse): Boolean {
        current = CapsuleItem(pulse.packageName, pulse.title, pulse.text)
        val existing = view
        if (existing != null) return true
        navBarInsetPx = readNavBarInsetPx()
        val created = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 瞬态窗推到 RESUMED：应用图标（produceState）与内容更新都要 effects/重组真正运行。
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
            setContent { CapsuleContent() }
        }
        created.setOnTouchListener { _, event -> arbiter.onTouch(event) }
        created.alpha = 0f
        created.scaleX = 1f
        created.scaleY = 1f
        // 进场：从自身高度处滑入 + 淡入。WRAP_CONTENT 的高度布局后才知道，
        // View.post 早于下一帧 measure/layout（height 还是 0，滑入会变成纯淡入）。
        created.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height <= 0 || !enterPending) return@addOnLayoutChangeListener
            enterPending = false
            v.translationY = v.height.toFloat()
            v.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(ENTER_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { entering = false }
                .start()
        }
        if (!wm.safeAddView(created, capsuleLayoutParams(bottomOffsetPx()))) {
            Log.e(TAG, "notification capsule addView failed (overlay permission or window rejected)")
            return false
        }
        view = created
        entering = true
        enterPending = true
        return true
    }

    /** 收起：淡出 + 滑回底部后整窗摘除（内容保留到淡完，别在半路清空闪白）。 */
    fun hide(onHidden: (() -> Unit)? = null) {
        val v = view
        view = null
        entering = false
        enterPending = false
        if (v == null) {
            onHidden?.invoke()
            return
        }
        v.animate().cancel()
        applyBallTouchFlags(v, wm, hidden = true)
        // 退出动画期间也留着句柄：服务在此期间销毁时 detachNow 才能把窗摘掉。
        exitingView = v
        // 首帧前 hide 时 height 还为 0，退回内容大致高度，至少保留滑出方向。
        val exitY = if (v.height > 0) {
            v.height.toFloat()
        } else {
            DensityProvider.dp2px(CapsuleFallbackHeightDp).toFloat()
        }
        v.animate()
            .translationY(exitY)
            .alpha(0f)
            .setDuration(HIDE_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (exitingView === v) exitingView = null
                context.removeWindow(v)
                onHidden?.invoke()
            }
            .start()
    }

    /** 不播动画直接摘窗：服务销毁等即时路径（含正在播退场动画的窗）。 */
    fun detachNow() {
        val v = view
        val exiting = exitingView
        view = null
        exitingView = null
        entering = false
        enterPending = false
        current = null
        v?.let(context::removeWindow)
        exiting?.let {
            it.animate().cancel()
            context.removeWindow(it)
        }
    }

    private fun readNavBarInsetPx(): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val nav = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        return max(nav, DensityProvider.dp2px(CapsuleMinNavBarDp))
    }

    /** BOTTOM 重力下的窗底偏移：导航栏 + 间距。 */
    private fun bottomOffsetPx(): Int =
        navBarInsetPx + DensityProvider.dp2px(CapsuleBottomMarginDp)

    // ---- Compose ----

    @Composable
    private fun CapsuleContent() {
        SideGestureTheme {
            current?.let { CapsuleFace(item = it) }
        }
    }

    @Composable
    private fun CapsuleFace(item: CapsuleItem) {
        val maxPillWidth = with(LocalDensity.current) {
            (DensityProvider.screenWidthPx - DensityProvider.dp2px(CapsuleSideMarginDp * 2)).toDp()
        }
        val accentColor = gestureButtonMarkColor(settings.color)
        // accent 任意选色：深底白字、浅底黑字，不写死 onSurface。
        val contentColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        val pill = RoundedCornerShape(percent = 50)
        Row(
            modifier = Modifier
                .widthIn(max = maxPillWidth)
                .clip(pill)
                .background(accentColor.copy(alpha = CapsuleBackgroundAlpha))
                .border(1.dp, contentColor.copy(alpha = 0.22f), pill)
                .padding(horizontal = CapsuleInnerPaddingDp.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(packageName = item.packageName)
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.82f),
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

        const val CapsuleIconDp = 24f
        const val CapsuleInnerPaddingDp = 16f
        const val CapsuleSideMarginDp = 16f

        /** 底色透明度（与剪贴胶囊同口径，保留 accent 色但不要过透）。 */
        const val CapsuleBackgroundAlpha = 0.85f

        /** 未量到高度前的兜底（dp）：只在首帧前收起时用于滑出距离。 */
        const val CapsuleFallbackHeightDp = 52f
        const val CapsuleBottomMarginDp = 10f
        const val CapsuleMinNavBarDp = 12f
        const val CapsulePressScale = 0.95f
    }
}
