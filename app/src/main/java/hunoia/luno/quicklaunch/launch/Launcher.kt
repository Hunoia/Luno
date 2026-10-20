package hunoia.luno.quicklaunch.launch

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.WindowManager
import hunoia.luno.R
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.WindowBounds
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.system.buildViewIntent
import hunoia.luno.bridge.system.hasViewActivity
import hunoia.luno.bridge.system.normalizeOpenAppOrUrl
import java.util.Locale
import kotlin.math.roundToInt

object Launcher {

    fun launchApp(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
    ): Boolean {
        if (miniWindow) {
            return launchAppInPopup(
                context = context,
                packageName = packageName,
                className = className,
                miniWindowSettings = miniWindowSettings,
            )
        }
        return try {
            val intent = Intent().apply {
                setClassName(packageName, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) == null) {
                val fallback = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
                context.startActivity(fallback)
                return true
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("LunoLauncher", "launchApp failed", e)
            false
        }
    }

    fun launchAppActivity(context: Context, packageName: String, className: String): Boolean {
        return try {
            val component = ComponentName.createRelative(packageName, className)
            val intent = Intent().apply {
                setComponent(component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) == null) return false
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("LunoLauncher", "launchAppActivity failed", e)
            false
        }
    }

    fun launchAppInfo(
        context: Context,
        appInfo: AppInfo,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
    ): Boolean {
        return launchApp(
            context = context,
            packageName = appInfo.packageName,
            className = appInfo.className,
            miniWindow = miniWindow,
            miniWindowSettings = miniWindowSettings,
        )
    }

    fun launchShortcutInfo(context: Context, shortcutInfo: LauncherInfo.ShortcutInfo): Boolean {
        return try {
            val intents = shortcutInfo.intents.map {
                Intent.parseUri(it, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }.toTypedArray()
            context.startActivities(intents)
            true
        } catch (e: Exception) {
            Log.e("LunoLauncher", "launchShortcutInfo failed", e)
            showToast(context.getString(R.string.launch_shortcut_info_failed, shortcutInfo.label))
            false
        }
    }

    fun launchUrl(context: Context, url: String): Boolean {
        return launchUrl(context, OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL, url = url))
    }

    fun launchUrl(
        context: Context,
        data: OpenAppOrUrlData,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
    ): Boolean {
        return try {
            val normalizedUrl = buildOpenUrl(data) ?: run {
                showToast(context.getString(R.string.invalid_url))
                return false
            }
            val intent = buildViewIntent(normalizedUrl)
            if (!context.packageManager.hasViewActivity(intent)) {
                showToast(context.getString(R.string.launch_no_handler))
                return false
            }
            if (data.miniWindow) {
                return MiniWindow.startActivity(context, intent, miniWindowSettings)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("LunoLauncher", "launchUrl failed", e)
            showToast(context.getString(R.string.launch_failed))
            false
        }
    }

    internal fun buildOpenUrl(data: OpenAppOrUrlData): String? {
        val normalizedUrl = normalizeOpenAppOrUrl(data.url) ?: return null
        return OpenUrlBuilder.build(normalizedUrl, data)
    }

    fun launchAppInPopup(
        context: Context,
        packageName: String,
        className: String,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
    ): Boolean {
        return MiniWindow.startActivity(
            context,
            ComponentName.createRelative(packageName, className),
            miniWindowSettings,
        )
    }

}


private object MiniWindow {

    private fun getRealScreenSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.maximumWindowMetrics.bounds
        return Point(bounds.width(), bounds.height())
    }

    fun startActivity(
        context: Context,
        component: ComponentName,
        miniWindowSettings: MiniWindowSettings,
    ): Boolean = startActivity(
        context = context,
        intent = Intent().apply {
            setComponent(component)
            setAction(Intent.ACTION_MAIN)
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        miniWindowSettings = miniWindowSettings,
    )

    fun startActivity(
        context: Context,
        intent: Intent,
        miniWindowSettings: MiniWindowSettings,
    ): Boolean {
        return try {
            val realSize = getRealScreenSize(context)
            val activityOptions = makeActivityOptions(
                settings = miniWindowSettings.forOrientation(
                    context.resources.configuration.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ),
                realSw = realSize.x,
                realSh = realSize.y,
            )
            context.startActivity(intent, activityOptions.toBundle())
            true
        } catch (e: Exception) {
            Log.e("LunoLauncher", "startActivity in mini window failed", e)
            showToast(context.getString(R.string.launch_mini_window_failed))
            false
        }
    }

    /**
     * MIUI13+/澎湃 对 freeform 小窗在合成层按 freeformScale(默认 0.7)缩小显示，
     * 但 WindowManager 帧仍是请求的 bounds。因此要让实际可见窗口等于设定尺寸，
     * 必须把 bounds 宽度/高度乘以 1/0.7≈1.42 抵消（与 GestureEvo 的 MIUI 补偿一致）。
     */
    private val miuiFreeformCompensation: Float by lazy {
        if (isMiui13Plus()) 1.42f else 1f
    }

    private fun makeActivityOptions(
        settings: WindowBounds,
        realSw: Int,
        realSh: Int,
    ) = ActivityOptions.makeBasic().also { opts ->
        runCatching {
            opts.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(opts, 5)
        }
        val comp = miuiFreeformCompensation
        val left = (realSw * settings.x.coerceIn(0f, 1f)).roundToInt()
        val top = (realSh * settings.y.coerceIn(0f, 1f)).roundToInt()
        val winW = (realSw * settings.width.coerceIn(0.2f, 1.5f) * comp).roundToInt()
        val winH = (realSh * settings.height.coerceIn(0.2f, 1.5f) * comp).roundToInt()
        opts.setLaunchBounds(Rect(left, top, left + winW, top + winH))
    }

    private fun isMiui13Plus(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val isXiaomi = manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco")
        if (!isXiaomi) return false
        val code = systemProperty("ro.miui.ui.version.code")?.toIntOrNull()
            ?: systemProperty("ro.miui.ui.version.name")
                ?.filter { it.isDigit() }
                ?.toIntOrNull()
            ?: return false
        return code >= 13
    }

    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        get.invoke(null, key) as? String
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
