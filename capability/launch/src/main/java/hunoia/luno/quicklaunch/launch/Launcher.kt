package hunoia.luno.quicklaunch.launch

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
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
import hunoia.luno.bridge.system.AppIcons
import hunoia.luno.shizuku.ShizukuManager
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import hunoia.luno.core.LOG_TAG

object Launcher {

    suspend fun launchApp(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
        userId: Int = 0,
    ): Boolean {
        if (userId != 0 && userId != Process.myUserHandle().hashCode()) {
            return launchCloneApp(
                context = context,
                packageName = packageName,
                className = className,
                miniWindow = miniWindow,
                miniWindowSettings = miniWindowSettings,
                intentAction = intentAction,
                data = data,
                extras = extras,
                userId = userId,
            )
        }
        return try {
            val pm = context.packageManager
            val intent = resolveLaunchIntent(pm, packageName, className, intentAction, data, extras)
                ?: pm.getLaunchIntentForPackage(packageName)?.also { fallback ->
                    // 兜底保持系统启动 Intent：自定义 action/data 命不中时不塞进去，
                    // 否则本来能启动的应用也会失败；extras 不改变解析结果，保留。
                    extras.forEach { (key, value) -> fallback.putExtra(key, value) }
                }
                ?: run {
                    // 无桌面入口的系统包（或没有 launcher intent 的包）：打开应用详情，
                    // 而不是静默失败。包本身不存在时仍返回 false。
                    if (runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() == null) {
                        return false
                    }
                    return openAppDetails(context, packageName)
                }
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

    /**
     * 分身启动：优先 Shizuku shell `am start --user`（直达，绕开 MIUI XSpace 二选一），
     * 无 Shizuku 时退回 LauncherApps（系统会弹分身选择器）。
     *
     * 小窗：shell 没有 `--bounds`，先按 freeform 启动，再用 `am task resize` 落到
     * Luno 小窗设置的位置/尺寸（MIUI 合成层缩放由 [MiniWindowGeometry] 补偿）。
     */
    private suspend fun launchCloneApp(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings,
        intentAction: String,
        data: String,
        extras: Map<String, String>,
        userId: Int,
    ): Boolean {
        if (ShizukuManager.currentStatus().isReady) {
            if (launchCloneViaShell(
                    context = context,
                    packageName = packageName,
                    className = className,
                    miniWindow = miniWindow,
                    miniWindowSettings = miniWindowSettings,
                    intentAction = intentAction,
                    data = data,
                    extras = extras,
                    userId = userId,
                )
            ) {
                return true
            }
        }
        return launchCloneViaLauncherApps(
            context = context,
            packageName = packageName,
            className = className,
            miniWindow = miniWindow,
            miniWindowSettings = miniWindowSettings,
            userId = userId,
        )
    }

    private suspend fun launchCloneViaShell(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings,
        intentAction: String,
        data: String,
        extras: Map<String, String>,
        userId: Int,
    ): Boolean {
        val cmd = buildString {
            append("am start --user ").append(userId)
            if (intentAction.isNotBlank()) append(" -a ").append(shellQuote(intentAction))
            if (data.isNotBlank()) append(" -d ").append(shellQuote(data))
            if (className.isNotBlank()) {
                append(" -n ").append(shellQuote("$packageName/$className"))
            } else {
                append(" -a ").append(shellQuote(Intent.ACTION_MAIN))
                append(" -c ").append(shellQuote(Intent.CATEGORY_LAUNCHER))
                append(" -p ").append(shellQuote(packageName))
            }
            if (miniWindow) append(" --windowingMode 5")
            for ((key, value) in extras) {
                append(" --es ").append(shellQuote(key)).append(' ').append(shellQuote(value))
            }
        }
        val result = ShizukuManager.executeShell(cmd, maxChars = 16_384)
        val output = result.stdout + result.stderr
        if (!result.isSuccess || output.contains("Error", ignoreCase = true)) {
            Log.w(LOG_TAG, "clone shell launch failed user=$userId pkg=$packageName out=$output")
            return false
        }
        if (miniWindow) {
            applyMiniWindowBounds(context, packageName, userId, miniWindowSettings)
        }
        return true
    }

    /** `am start` 异步返回：轮询 `am stack list` 找到本次 task，再按设置 resize。 */
    private suspend fun applyMiniWindowBounds(
        context: Context,
        packageName: String,
        userId: Int,
        settings: MiniWindowSettings,
    ) {
        var taskId: Int? = null
        for (attempt in 0 until 6) {
            if (attempt > 0) delay(120)
            val list = ShizukuManager.executeShell("am stack list", maxChars = 200_000)
            taskId = parseTaskId(list.stdout, packageName, userId)
            if (taskId != null) break
        }
        val id = taskId ?: return
        val metrics = MiniWindowGeometry.realScreenSize(context)
        val bounds = MiniWindowGeometry.bounds(
            settings = settings.forOrientation(
                context.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            ),
            realSw = metrics.x,
            realSh = metrics.y,
        )
        ShizukuManager.executeShell(
            "am task resize $id ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}",
        )
    }

    private fun parseTaskId(output: String, packageName: String, userId: Int): Int? {
        val regex = Regex("""taskId=(\d+): ${Regex.escape(packageName)}/\S+.*userId=$userId""")
        return regex.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun launchCloneViaLauncherApps(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings,
        userId: Int,
    ): Boolean = try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val user: UserHandle = AppIcons.userHandle(userId) ?: return false
        val activity = launcherApps.getActivityList(packageName, user)
            .firstOrNull { className.isBlank() || it.componentName.className == className }
            ?: return false
        val options = if (miniWindow) {
            MiniWindowGeometry.activityOptions(context, miniWindowSettings).toBundle()
        } else {
            null
        }
        launcherApps.startMainActivity(activity.componentName, user, null, options)
        true
    } catch (e: Exception) {
        Log.e(LOG_TAG, "clone launcherApps launch failed user=$userId pkg=$packageName", e)
        false
    }

    /** 打开系统「应用详情」页；无桌面入口的系统包只能这样进入。 */
    fun openAppDetails(context: Context, packageName: String): Boolean = try {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    } catch (e: Exception) {
        Log.e(LOG_TAG, "openAppDetails failed pkg=$packageName", e)
        false
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
     * 启动快捷方式：优先走 LauncherApps / Shizuku（可启动应用未导出的 shortcut
     * trampoline），失败再回退到直接 startActivity。
     *
     * `LauncherInfo.ShortcutInfo` 是查询侧模型（带 Bitmap），不进口契约；调用方负责解码落库 JSON。
     */
    suspend fun launchShortcutWithFallback(
        context: Context,
        packageName: String,
        shortcutId: String?,
        intentUris: List<String>,
        label: String,
    ): Boolean {
        if (packageName.isNotBlank() && !shortcutId.isNullOrBlank()) {
            if (startShortcutViaLauncherApps(context, packageName, shortcutId)) return true
            if (hunoia.luno.shizuku.ShizukuManager.startShortcut(packageName, shortcutId)) return true
        }
        return launchShortcutIntents(context, intentUris, label)
    }

    /**
     * 非默认 launcher 直接 startActivity 会被非导出 trampoline 拦住；
     * LauncherApps 是系统给快捷方式开的正式入口。
     */
    private fun startShortcutViaLauncherApps(
        context: Context,
        packageName: String,
        shortcutId: String,
    ): Boolean = try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.startShortcut(
            packageName,
            shortcutId,
            null,
            null,
            Process.myUserHandle(),
        )
        true
    } catch (e: Exception) {
        Log.w(LOG_TAG, "launcherApps.startShortcut failed pkg=$packageName id=$shortcutId", e)
        false
    }

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


internal object MiniWindowGeometry {

    fun realScreenSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.maximumWindowMetrics.bounds
        return Point(bounds.width(), bounds.height())
    }

    /**
     * MIUI13+/澎湃对 freeform 小窗在合成层按 freeformScale(默认 0.7) 缩小显示，但 WindowManager
     * 帧仍是请求的 bounds。要让实际可见窗口等于设定尺寸，必须把 bounds 宽高乘以 1/0.7≈1.42 抵消。
     */
    private val miuiFreeformCompensation: Float by lazy {
        if (isMiui13Plus()) 1.42f else 1f
    }

    fun bounds(settings: WindowBounds, realSw: Int, realSh: Int): Rect {
        val comp = miuiFreeformCompensation
        val left = (realSw * settings.x.coerceIn(0f, 1f)).roundToInt()
        val top = (realSh * settings.y.coerceIn(0f, 1f)).roundToInt()
        val winW = (realSw * settings.width.coerceIn(0.2f, 1.5f) * comp).roundToInt()
        val winH = (realSh * settings.height.coerceIn(0.2f, 1.5f) * comp).roundToInt()
        return Rect(left, top, left + winW, top + winH)
    }

    fun activityOptions(context: Context, miniWindowSettings: MiniWindowSettings): ActivityOptions {
        val size = realScreenSize(context)
        val settings = miniWindowSettings.forOrientation(
            context.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE,
        )
        return ActivityOptions.makeBasic().also { opts ->
            runCatching {
                opts.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    .invoke(opts, 5)
            }
            opts.setLaunchBounds(bounds(settings, size.x, size.y))
        }
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

private object MiniWindow {

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
            val activityOptions = MiniWindowGeometry.activityOptions(context, miniWindowSettings)
            context.startActivity(intent, activityOptions.toBundle())
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "startActivity in mini window failed", e)
            showToast(context.getString(R.string.launch_mini_window_failed))
            false
        }
    }
}
