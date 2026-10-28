package hunoia.luno.quicklaunch.launch

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.WindowManager
import hunoia.luno.capability.launch.R
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.WindowBounds
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.system.buildViewIntent
import hunoia.luno.bridge.system.hasViewActivity
import hunoia.luno.bridge.system.normalizeOpenAppOrUrl
import java.util.Locale
import kotlin.math.roundToInt
import hunoia.luno.core.LOG_TAG

object Launcher {

    fun launchApp(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
    ): Boolean {
        return try {
            val pm = context.packageManager
            val intent = resolveLaunchIntent(pm, packageName, className, intentAction, data, extras)
                ?: pm.getLaunchIntentForPackage(packageName)?.also { fallback ->
                    // 兜底保持系统启动 Intent：自定义 action/data 命不中时不塞进去，
                    // 否则本来能启动的应用也会失败；extras 不改变解析结果，保留。
                    extras.forEach { (key, value) -> fallback.putExtra(key, value) }
                }
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (miniWindow) {
                MiniWindow.startActivity(context, intent, miniWindowSettings)
            } else {
                context.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "launchApp failed", e)
            false
        }
    }

    /**
     * 解析顺序：配了 action/data 时先按 package 限定的隐式 Intent 解析——action 可能指向
     * 专门的入口 Activity（如 `com.follow.clash.action.TOGGLE` → QuickActionActivity），
     * 强制显式组件会让 action 失效。命不中再退回显式指定的 Activity。
     * 都没配返回 null，交给系统 launch intent。
     */
    private fun resolveLaunchIntent(
        pm: android.content.pm.PackageManager,
        packageName: String,
        className: String,
        intentAction: String,
        data: String,
        extras: Map<String, String>,
    ): Intent? {
        val action = intentAction.trim()
        val uri = data.trim()
        fun base() = Intent().apply {
            if (action.isNotEmpty()) setAction(action)
            if (uri.isNotEmpty()) setData(Uri.parse(uri))
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        if (action.isNotEmpty() || uri.isNotEmpty()) {
            val implicit = base().apply { setPackage(packageName) }
            if (pm.resolveActivity(implicit, 0) != null) return implicit
        }
        if (className.isNotBlank()) {
            val explicit = base().apply { setClassName(packageName, className) }
            if (pm.resolveActivity(explicit, 0) != null) return explicit
        }
        return null
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
            Log.e(LOG_TAG, "launchAppActivity failed", e)
            false
        }
    }

    /**
     * 启动快捷方式：入参是已解析好的 Intent URI 列表。
     *
     * `LauncherInfo.ShortcutInfo` 是查询侧模型（带 Bitmap），不进口契约；调用方负责解码落库 JSON。
     */
    fun launchShortcutIntents(context: Context, intentUris: List<String>, label: String): Boolean {
        if (intentUris.isEmpty()) return false
        return try {
            val intents = intentUris.map {
                Intent.parseUri(it, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val resolved = intents.filter { context.packageManager.resolveActivity(it, 0) != null }
            if (resolved.isEmpty()) {
                showToast(context.getString(R.string.launch_shortcut_info_failed, label))
                return false
            }
            // 多 intent 快捷方式按顺序建回退栈；若后面的 intent 不可解析（部分应用
            // 声明了可选的目标页），退到最后一个可解析 intent，而不是整条失败。
            if (resolved.size == intents.size) {
                context.startActivities(intents.toTypedArray())
            } else {
                context.startActivity(resolved.last())
            }
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "launchShortcutInfo failed", e)
            showToast(context.getString(R.string.launch_shortcut_info_failed, label))
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
                showToast(context.getString(hunoia.luno.core.bridge.R.string.invalid_url))
                return false
            }
            val intent = buildViewIntent(normalizedUrl)
            if (!context.packageManager.hasViewActivity(intent)) {
                showToast(context.getString(hunoia.luno.core.bridge.R.string.launch_no_handler))
                return false
            }
            if (data.miniWindow) {
                return MiniWindow.startActivity(context, intent, miniWindowSettings)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "launchUrl failed", e)
            showToast(context.getString(hunoia.luno.core.bridge.R.string.launch_failed))
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
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
    ): Boolean {
        return MiniWindow.startActivity(
            context,
            ComponentName.createRelative(packageName, className),
            miniWindowSettings,
            intentAction = intentAction,
            data = data,
            extras = extras,
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
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
    ): Boolean = startActivity(
        context = context,
        intent = Intent().apply {
            setComponent(component)
            if (intentAction.isBlank()) {
                setAction(Intent.ACTION_MAIN)
                addCategory(Intent.CATEGORY_LAUNCHER)
            } else {
                setAction(intentAction)
            }
            if (data.isNotBlank()) setData(Uri.parse(data))
            extras.forEach { (key, value) -> putExtra(key, value) }
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
            Log.e(LOG_TAG, "startActivity in mini window failed", e)
            showToast(context.getString(R.string.launch_mini_window_failed))
            false
        }
    }

    /**
     * MIUI13+/澎湃对 freeform 小窗在合成层按 freeformScale(默认 0.7) 缩小显示，但 WindowManager
     * 帧仍是请求的 bounds。要让实际可见窗口等于设定尺寸，必须把 bounds 宽高乘以 1/0.7≈1.42 抵消。
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
