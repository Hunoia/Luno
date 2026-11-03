package hunoia.luno.bridge.ui

import android.graphics.drawable.Drawable
import android.view.View
import hunoia.luno.core.LunoLog as Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

/**
 * 区域背景模糊（Android 12+ 的 `BackgroundBlurDrawable`）。
 *
 * 走隐藏 API，但把全部反射与失败收口在这里：任何一步不可用都返回 null，调用方
 * 静默退回无模糊。平台 API 只允许出现在 `:core:bridge`（architecture 不变量 4）。
 *
 * 用法：把一个贴住目标的 View 传进来，attach 后由 handle 在尺寸/参数变化时 update，
 * 销毁时 detach。drawable 只模糊该 View 的 bounds（带圆角），不影响窗口其它区域。
 */
object BackgroundBlur {

    private const val TAG = "BackgroundBlur"

    @Volatile
    private var exemptionState = 0 // 0 = 未尝试，1 = 可用，-1 = 不可用

    private val getViewRootImpl: Method? by lazy {
        runCatching {
            View::class.java.getDeclaredMethod("getViewRootImpl").apply { isAccessible = true }
        }.getOrNull()
    }

    /**
     * 首次反射前安装隐藏 API 豁免。与一墨使用的是同两个类；
     * 失败后不再重复尝试，直接按“本机不支持”处理。
     */
    private fun ensureExemptions(): Boolean {
        if (exemptionState == 1) return true
        if (exemptionState == -1) return false
        synchronized(this) {
            if (exemptionState != 0) return exemptionState == 1
            val ok = runCatching {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/view/ViewRootImpl",
                    "Lcom/android/internal/graphics/drawable/BackgroundBlurDrawable",
                )
            }.onFailure {
                Log.w(TAG, "hidden api exemption failed: ${it.message}")
            }.getOrDefault(false)
            exemptionState = if (ok) 1 else -1
            if (!ok) Log.w(TAG, "hidden api exemption rejected, region blur disabled")
            return ok
        }
    }

    /**
     * 给 [view] 挂一个区域模糊背景。失败返回 null，调用方不要重试到卡顿：
     * 真正的可用性由 View 是否 attached、系统是否支持共同决定。
     */
    fun attach(
        view: View,
        radiusPx: Int,
        cornerRadiusPx: Float,
        tint: Int,
    ): BackgroundBlurHandle? {
        if (radiusPx <= 0 || view.width <= 0 || view.height <= 0) return null
        if (!ensureExemptions()) return null
        val root = runCatching { getViewRootImpl?.invoke(view) }.getOrNull() ?: return null

        return runCatching {
            val create = root.javaClass
                .getDeclaredMethod("createBackgroundBlurDrawable")
                .apply { isAccessible = true }
            val drawable = create.invoke(root) as? Drawable ?: return@runCatching null
            val cls = drawable.javaClass
            val radiusSetter = cls
                .getDeclaredMethod("setBlurRadius", Integer.TYPE)
                .apply { isAccessible = true }
            val corner4 = runCatching {
                cls.getDeclaredMethod(
                    "setCornerRadius",
                    java.lang.Float.TYPE,
                    java.lang.Float.TYPE,
                    java.lang.Float.TYPE,
                    java.lang.Float.TYPE,
                ).apply { isAccessible = true }
            }.getOrNull()
            val corner1 = if (corner4 == null) {
                runCatching {
                    cls.getDeclaredMethod(
                        "setCornerRadius",
                        java.lang.Float.TYPE,
                    ).apply { isAccessible = true }
                }.getOrNull()
            } else {
                null
            }
            val colorSetter = runCatching {
                cls.getDeclaredMethod("setColor", Integer.TYPE).apply { isAccessible = true }
            }.getOrNull()
            val updatePath = runCatching {
                cls.getDeclaredMethod("updatePath").apply { isAccessible = true }
            }.getOrNull()

            val handle = BackgroundBlurHandle(
                view = view,
                drawable = drawable,
                radiusSetter = radiusSetter,
                cornerSetter = corner4 ?: corner1,
                cornerArity = if (corner4 != null) 4 else 1,
                colorSetter = colorSetter,
                updatePath = updatePath,
            )
            view.background = drawable
            drawable.setVisible(true, false)
            handle.update(radiusPx, cornerRadiusPx, tint)
            Log.i(TAG, "region blur attached radius=${radiusPx}px corner=${cornerRadiusPx}px")
            handle
        }.onFailure {
            Log.w(TAG, "attach failed: ${it::class.simpleName} ${it.message}")
        }.getOrNull()
    }
}

/** [BackgroundBlur.attach] 的句柄；参数变化只 update，不再重新反射。 */
class BackgroundBlurHandle internal constructor(
    private val view: View,
    private val drawable: Drawable,
    private val radiusSetter: Method,
    private val cornerSetter: Method?,
    private val cornerArity: Int,
    private val colorSetter: Method?,
    private val updatePath: Method?,
) {
    private var detached = false

    fun update(radiusPx: Int, cornerRadiusPx: Float, tint: Int) {
        if (detached) return
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return
        runCatching {
            drawable.setBounds(0, 0, w, h)
            radiusSetter.invoke(drawable, radiusPx.coerceAtLeast(0))
            when (cornerArity) {
                4 -> cornerSetter?.invoke(
                    drawable,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    cornerRadiusPx,
                )
                1 -> cornerSetter?.invoke(drawable, cornerRadiusPx)
            }
            colorSetter?.invoke(drawable, tint)
            updatePath?.invoke(drawable)
        }.onFailure {
            Log.w("BackgroundBlur", "update failed: ${it::class.simpleName} ${it.message}")
        }
    }

    fun detach() {
        if (detached) return
        detached = true
        runCatching { drawable.setVisible(false, false) }
        if (view.background === drawable) view.background = null
    }
}
