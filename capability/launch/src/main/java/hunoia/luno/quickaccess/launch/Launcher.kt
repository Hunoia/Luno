package hunoia.luno.quickaccess.launch

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import hunoia.luno.core.LunoLog as Log
import android.view.WindowManager
import hunoia.luno.capability.launch.R
import hunoia.luno.config.model.MiniFreeformPosition
import hunoia.luno.config.model.MiniFreeformSettings
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.WindowBounds
import hunoia.luno.config.model.resolveTopLeft
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.ui.systemBarsSafeArea
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.bridge.system.buildViewIntent
import hunoia.luno.bridge.system.hasViewActivity
import hunoia.luno.bridge.system.normalizeOpenAppOrUrl
import hunoia.luno.bridge.system.AppIcons
import hunoia.luno.bridge.system.shellQuote
import hunoia.luno.shizuku.ShizukuManager
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import hunoia.luno.core.LOG_TAG

object Launcher {

    /**
     * 小窗缩放系数的异步采样：不能挡在启动返回路径上，且要等 MIUI freeform 装饰/合成
     * 层真正生效（约 600–700ms）后 `dumpsys` 才能看到带放大的 transform。
     */
    private val learnScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        anchor: Pair<Int, Int>? = null,
    ): Boolean {
        val tLaunch = SystemClock.uptimeMillis()
        if (miniWindow) {
            Log.i(LOG_TAG, "miniWindow launch begin pkg=$packageName user=$userId t=$tLaunch")
        }
        return try {
            // 整个启动体放到 IO：hasClone / resolveActivity / startActivity 都是阻塞 binder，
            // 占住手势收尾与面板收口的主线程，会在系统忙时拖慢「触发→打开」的感知。
            var suppressSwap = false
            val launched = withContext(Dispatchers.IO) {
                // 优先复用已有 task：目标在最近任务里时，用 startActivityFromRecents 带小窗参数
                // 提到前台，而不是重新走一次 Activity 启动。实测能让 2.5s / 5.0s 的
                // 「快照启动窗卡在 OPEN 转场直到超时」变成 60–170ms。
                // 放在 hasClone / 分身分支之前，保证微信这类装了分身的应用也能命中。
                if (miniWindow && miniReuseEnabled()) {
                    when (tryReuseTaskInMiniWindow(
                        context = context,
                        packageName = packageName,
                        userId = userId,
                        miniWindowSettings = miniWindowSettings,
                        anchor = anchor,
                        tLaunch = tLaunch,
                    )) {
                        MiniReuseResult.Reused -> return@withContext true
                        MiniReuseResult.Toggled, MiniReuseResult.Switched -> {
                            suppressSwap = true
                            return@withContext true
                        }
                        MiniReuseResult.NotHandled -> Unit
                    }
                }
                if (userId != 0 && userId != Process.myUserHandle().hashCode()) {
                    return@withContext launchCloneApp(
                        context = context,
                        packageName = packageName,
                        className = className,
                        miniWindow = miniWindow,
                        miniWindowSettings = miniWindowSettings,
                        intentAction = intentAction,
                        data = data,
                        extras = extras,
                        userId = userId,
                        anchor = anchor,
                    )
                }
                // 目标应用有分身时，普通 startActivity 会被 MIUI XSpace 拦成「主应用/分身」二选一；
                // 用 shell 身份显式指定 userId=0 启动，和分身的显式 user 路径保持一致。
                if (AppIcons.hasClone(context, packageName) && ShizukuManager.currentStatus().isReady) {
                    buildLaunchIntent(context, packageName, className, intentAction, data, extras)?.let { intent ->
                        val options = if (miniWindow) {
                            MiniWindowGeometry.activityOptionsBundle(context, miniWindowSettings, anchor)
                        } else {
                            null
                        }
                        if (ShizukuManager.startActivityAsUser(intent, 0, options)) {
                            if (miniWindow) {
                                Log.i(
                                    LOG_TAG,
                                    "miniWindow shizuku launched elapsedMs=${SystemClock.uptimeMillis() - tLaunch} pkg=$packageName",
                                )
                            }
                            return@withContext true
                        }
                    }
                }
                val pm = context.packageManager
                val intent = buildLaunchIntent(context, packageName, className, intentAction, data, extras)
                    ?: run {
                        // 无桌面入口的系统包（或没有 launcher intent 的包）：打开应用详情，
                        // 而不是静默失败。包本身不存在时仍返回 false。
                        if (runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() == null) {
                            return@withContext false
                        }
                        return@withContext openAppDetails(context, packageName)
                    }
                if (miniWindow) {
                    MiniWindow.startActivity(context, intent, miniWindowSettings, anchor)
                } else {
                    context.startActivity(intent)
                    true
                }
            }
            if (launched && miniWindow) {
                scheduleScaleLearn(context, packageName)
                // 切换/toggle 路径已自行处理旧小窗（切换恢复当前、toggle 关掉小窗），
                // 不再 swap；复用/普通启动才把其他既有小窗恢复全屏（单前台小窗）。
                if (!suppressSwap && miniWindowSettings.swapExisting) {
                    scheduleSwapExistingWindows(context, packageName)
                }
            }
            launched
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "launchApp failed", e)
            false
        }
    }

    /** 小窗启动成功后异步学习合成缩放系数；转屏后第一次仍用旧缓存/常量。 */
    private fun scheduleScaleLearn(context: Context, packageName: String) {
        val landscape = context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        learnScope.launch {
            delay(700)
            FreeformScale.learn(landscape, packageName)
        }
    }

    /**
     * 普通小窗的「与已有小窗互换」：新小窗起来后，把旧的小窗任务恢复全屏。
     *
     * 放在后台异步做，不占启动返回路径；排除本次启动的包名和本应用自己的页面，
     * 避免把刚打开的小窗也一起恢复。旧小窗的 taskId 会被保留（见
     * [restoreTaskToFullscreen]），所以回到全屏后页面状态不丢。
     */
    private fun scheduleSwapExistingWindows(context: Context, packageName: String) {
        learnScope.launch {
            delay(450)
            if (!ShizukuManager.currentStatus().isReady) return@launch
            val tasks = parseStackTasks(
                ShizukuManager.executeShell("am stack list", maxChars = 200_000).stdout,
            )
            val existing = tasks.filter {
                it.isFreeform && it.visible &&
                    it.packageName != packageName &&
                    it.packageName != context.packageName &&
                    // 迷你小窗归「迷你小窗」那组设置管，普通小窗互换不去动它。
                    !ShizukuManager.isMiniFreeFormTask(it.taskId)
            }
            for (task in existing) {
                Log.i(
                    LOG_TAG,
                    "miniWindow swap existing pkg=${task.packageName} task=${task.taskId}",
                )
                restoreTaskToFullscreen(context, task)
            }
        }
    }

    /**
     * 迷你小窗：把当前应用的任务交给 MIUI 的迷你浮窗（`launchMiniFreeFormWindow`）。
     *
     * 与小窗（freeform）是两条独立链路：尺寸由系统决定，只按设置选左右位置；
     * 开启互换时，屏幕上已有的 freeform 小窗会同步恢复为全屏。目标选择见
     * [chooseMiniWindowTarget]。
     *
     * 找不到目标 task（应用已退出 / 系统未列出）返回 false，由调用方给出失败反馈。
     */
    suspend fun launchMiniWindow(
        context: Context,
        packageName: String,
        miniFreeform: MiniFreeformSettings = MiniFreeformSettings(),
        userId: Int = 0,
        anchor: Pair<Int, Int>? = null,
    ): Boolean = try {
        withContext(Dispatchers.IO) {
            if (!ShizukuManager.currentStatus().isReady) {
                Log.i(LOG_TAG, "miniFreeform skip shizuku-not-ready pkg=$packageName")
                return@withContext false
            }
            val list = ShizukuManager.executeShell("am stack list", maxChars = 200_000)
            val plan = chooseMiniWindowTarget(
                tasks = parseStackTasks(list.stdout),
                packageName = packageName,
                userId = userId,
                autoSwap = miniFreeform.autoSwap,
                selfPackage = context.packageName,
            )
            if (plan == null) {
                Log.i(LOG_TAG, "miniFreeform no-task pkg=$packageName user=$userId")
                return@withContext false
            }
            val target = plan.target
            // 目标是 freeform（普通小窗或迷你小窗）→ 变全屏（toggle），不挂迷你。
            // 「普通小窗→迷你」路径已删：restore 全屏后立即挂迷你会撞上未落定的
            // freeform↔fullscreen 转换，MIUI 内部 MiuiFreeFormActivityStack 为 null →
            // system_server 崩溃（实测）。迷你小窗同样走恢复全屏（已小窗→全屏）。
            if (target.isFreeform) {
                Log.i(
                    LOG_TAG,
                    "miniFreeform toggle-to-fullscreen " +
                        "mini=${ShizukuManager.isMiniFreeFormTask(target.taskId)} " +
                        "task=${target.taskId} pkg=${target.packageName}",
                )
                restoreTaskToFullscreen(context, target)
                return@withContext true
            }
            val positionCode = miniFreeformPositionCode(context, miniFreeform.position, anchor)
            val tCall = SystemClock.uptimeMillis()
            val ok = ShizukuManager.launchMiniFreeFormWindow(target.taskId, positionCode)
            Log.i(
                LOG_TAG,
                "miniFreeform task=${target.taskId} pkg=${target.packageName} " +
                    "user=${target.userId} current=$packageName swappedTarget=${plan.swapped} " +
                    "position=$positionCode ok=$ok swap=${plan.swap?.packageName} " +
                    "callMs=${SystemClock.uptimeMillis() - tCall}",
            )
            if (ok && plan.swap != null) {
                restoreTaskToFullscreen(context, plan.swap)
            }
            ok
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(LOG_TAG, "miniFreeform failed pkg=$packageName", e)
        false
    }

    /** 一次迷你小窗派发的目标选择结果。 */
    internal data class MiniWindowPlan(
        val target: StackTask,
        /** 需要恢复为全屏的旧小窗；关闭互换或屏幕上没有小窗时为 null。 */
        val swap: StackTask?,
        /** true 表示目标不是传入的「当前应用」，而是它下面的全屏任务。 */
        val swapped: Boolean,
    )

    /**
     * 选择迷你小窗的目标任务。
     *
     * 无障碍的「当前应用」在屏幕已有浮动小窗时指向那个浮窗，而不是它下面的全屏应用；
     * 开启互换时用户看到的是全屏应用，且「互换」的语义正是「全屏应用进迷你、旧小窗
     * 回全屏」，因此当前应用已在小窗里时改选可见的全屏任务。
     */
    internal fun chooseMiniWindowTarget(
        tasks: List<StackTask>,
        packageName: String,
        userId: Int,
        autoSwap: Boolean,
        selfPackage: String,
    ): MiniWindowPlan? {
        val current = tasks.firstOrNull {
            it.packageName == packageName && (userId == 0 || it.userId == userId) && it.visible
        } ?: tasks.firstOrNull {
            it.packageName == packageName && (userId == 0 || it.userId == userId)
        }
        val fullscreenBehind = if (autoSwap && current?.isFreeform == true) {
            tasks.firstOrNull {
                it.visible && !it.isFreeform &&
                    it.activityType != "home" &&
                    it.packageName != packageName &&
                    it.packageName != selfPackage
            }
        } else {
            null
        }
        val target = fullscreenBehind ?: current ?: return null
        val swap = if (autoSwap) {
            current?.takeIf { it.isFreeform && it.visible && it.taskId != target.taskId }
                ?: tasks.firstOrNull {
                    it.isFreeform && it.visible &&
                        it.taskId != target.taskId &&
                        it.packageName != target.packageName
                }
        } else {
            null
        }
        return MiniWindowPlan(target = target, swap = swap, swapped = fullscreenBehind != null)
    }

    private fun miniFreeformPositionCode(
        context: Context,
        position: MiniFreeformPosition,
        anchor: Pair<Int, Int>?,
    ): Int = when (position) {
        MiniFreeformPosition.LEFT -> 1
        MiniFreeformPosition.RIGHT -> 2
        MiniFreeformPosition.AUTO -> {
            val screenWidth = MiniWindowGeometry.realScreenSize(context).x
            if (anchor != null && screenWidth > 0 && anchor.first < screenWidth / 2) 1 else 2
        }
    }

    /**
     * 把小窗任务恢复全屏，并回读窗口模式确认。
     *
     * 用于迷你小窗的互换、以及普通小窗挂迷你前的预恢复。优先
     * `startActivityFromRecents` 复用原任务（保留页面状态，不会多开一个 task）；
     * 任务已被系统回收时退回按包名重新启动。只做尽力而为 —— MIUI 对已经在栈里的
     * task 可能忽略全屏档，此时记为失败日志，不影响后续迷你派发。
     */
    private suspend fun restoreTaskToFullscreen(context: Context, task: StackTask) {
        val options = MiniWindowGeometry.fullscreenOptionsBundle()
        val restored = ShizukuManager.startActivityFromRecents(task.taskId, options)
        val dispatched = if (restored) {
            true
        } else {
            val intent = runCatching {
                context.packageManager.getLaunchIntentForPackage(task.packageName)
            }.getOrNull()
            if (intent == null) {
                Log.w(LOG_TAG, "restore-fullscreen no-launch-intent pkg=${task.packageName}")
                return
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ShizukuManager.startActivityAsUser(intent, task.userId, options)
        }
        delay(450)
        val afterTasks = parseStackTasks(
            ShizukuManager.executeShell("am stack list", maxChars = 200_000).stdout,
        )
        val after = afterTasks.firstOrNull { it.taskId == task.taskId }
            ?: afterTasks.firstOrNull {
                it.packageName == task.packageName && it.userId == task.userId
            }
        Log.i(
            LOG_TAG,
            "restore-fullscreen pkg=${task.packageName} task=${task.taskId} " +
                "restoredFromRecents=$restored dispatched=$dispatched " +
                "windowingMode=${after?.windowingMode ?: "gone"}",
        )
    }

    /** `am stack list` 里的一条任务及其所属 RootTask 的窗口模式。 */
    internal data class StackTask(
        val taskId: Int,
        val packageName: String,
        val userId: Int,
        val windowingMode: String,
        val visible: Boolean,
        /** RootTask 的 `mActivityType`，如 `home` / `standard`；老系统可能读不到。 */
        val activityType: String? = null,
    ) {
        /** 普通小窗与迷你小窗的 RootTask 都是 `freeform`。 */
        val isFreeform: Boolean get() = windowingMode == "freeform"
    }

    /**
     * 解析 `am stack list`：RootTask 行之后紧跟 configuration（含 `mWindowingMode`），
     * 再列出该栈内的 task 行。freeform 小窗所在 RootTask 的 mode 即 `freeform`。
     */
    internal fun parseStackTasks(output: String): List<StackTask> {
        val tasks = mutableListOf<StackTask>()
        var mode = ""
        var activityType: String? = null
        for (line in output.lineSequence()) {
            if (line.startsWith("RootTask ")) {
                mode = ""
                activityType = null
                continue
            }
            if (mode.isEmpty()) {
                ROOT_WINDOWING_MODE_REGEX.find(line)?.let { mode = it.groupValues[1] }
            }
            if (activityType == null) {
                ROOT_ACTIVITY_TYPE_REGEX.find(line)?.let { activityType = it.groupValues[1] }
            }
            val match = STACK_TASK_REGEX.find(line) ?: continue
            val pkg = match.groupValues[2]
            if (pkg == "unknown") continue
            val taskId = match.groupValues[1].toIntOrNull() ?: continue
            tasks += StackTask(
                taskId = taskId,
                packageName = pkg,
                userId = match.groupValues[3].toIntOrNull() ?: 0,
                windowingMode = mode,
                visible = line.contains("visible=true"),
                activityType = activityType,
            )
        }
        return tasks
    }

    /**
     * 复用已有 task 的快路径开关。默认开启，出问题时可临时关闭对照：
     *
     * ```
     * adb shell setprop debug.luno.mini_reuse off   # 回到「每次重新 startActivity」
     * adb shell setprop debug.luno.mini_reuse on    # 恢复复用（默认）
     * ```
     */
    private fun miniReuseEnabled(): Boolean = reuseFlag("debug.luno.mini_reuse")

    /**
     * 前台（可见）task 是否跳过复用，默认开启。
     *
     * 可见 task 的 activity 已经 `RESUMED` + 绘制过，WMS 不会再给它建
     * `SnapshotStartingWindow`（`Disable startingWindow, because the main window has already
     * been drawn`）。此时 `startActivityFromRecents` 会让它在原地 fullscreen→freeform：
     * 窗口先按补偿后的 raw frame（可见尺寸 × 1/0.7）重排，而 MIUI freeform shell 的 0.7
     * 合成缩放 / 圆角 / caption 要晚 ~40–60ms 才生效，于是就露出一个没装饰的大方形窗口，
     * 再「弹」回正常小窗尺寸。后台 task 有快照窗挡着，所以看不到这一步。
     *
     * 改为走普通启动（OPEN 转场，原生入口实测不弹）；后台 task 仍走复用快路径。
     * 对照开关：
     *
     * ```
     * adb shell setprop debug.luno.mini_reuse_visible off   # 前台 task 也复用
     * adb shell setprop debug.luno.mini_reuse_visible on    # 恢复（默认）
     * ```
     */
    private fun miniReuseVisibleTaskEnabled(): Boolean = reuseFlag("debug.luno.mini_reuse_visible")

    private fun reuseFlag(key: String): Boolean {
        val value = runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            get.invoke(null, key) as? String
        }.getOrNull().orEmpty().trim().lowercase(Locale.ROOT)
        return value != "off"
    }

    private enum class MiniReuseResult {
        /** 未处理，回落到普通启动（需要 swap 旧小窗）。 */
        NotHandled,

        /** 复用了后台 task 打开小窗（需要 swap 旧小窗）。 */
        Reused,

        /** 当前小窗 → 全屏（toggle，不 swap）。 */
        Toggled,

        /** 小窗 + 全屏切换（镜像迷你小窗，不 swap）。 */
        Switched,
    }

    /**
     * 复用已有 task 打开小窗：`startActivityFromRecents(taskId, freeform options)`.
     * 失败（找不到 task / Shizuku 拒绝）返回 false，调用方回落到普通启动。
     *
     * 找到的是 task 而不是 Intent，所以不会重新走 ActivityStarter 的启动/快照路径，
     * 也就避开了「SnapshotStartingWindow 卡在 OPEN 转场直到 2.5s/5s 超时」。
     * [userId] 用于分身：主用户找 `userId=0` 的 task，分身找 `userId=999` 的 task。
     */
    private suspend fun tryReuseTaskInMiniWindow(
        context: Context,
        packageName: String,
        userId: Int,
        miniWindowSettings: MiniWindowSettings,
        anchor: Pair<Int, Int>?,
        tLaunch: Long,
    ): MiniReuseResult {
        if (!ShizukuManager.currentStatus().isReady) {
            Log.i(LOG_TAG, "miniReuse skip shizuku-not-ready pkg=$packageName user=$userId")
            return MiniReuseResult.NotHandled
        }
        val tLookup = SystemClock.uptimeMillis()
        val list = ShizukuManager.executeShell("am stack list", maxChars = 200_000)
        val tasks = parseStackTasks(list.stdout)
        val current = tasks.firstOrNull {
            it.packageName == packageName && (userId == 0 || it.userId == userId)
        }
        val lookupMs = SystemClock.uptimeMillis() - tLookup
        if (current == null) {
            Log.i(
                LOG_TAG,
                "miniReuse no-task pkg=$packageName user=$userId lookupMs=$lookupMs " +
                    "totalMs=${SystemClock.uptimeMillis() - tLaunch}",
            )
            return MiniReuseResult.NotHandled
        }
        val plan = chooseMiniWindowTarget(
            tasks = tasks,
            packageName = packageName,
            userId = userId,
            autoSwap = miniWindowSettings.swapExisting,
            selfPackage = context.packageName,
        ) ?: return MiniReuseResult.NotHandled
        val target = plan.target
        // 切换（镜像迷你小窗）：当前是小窗 + 开启互换 + 后面有可见全屏应用 →
        // 打开那个全屏应用的小窗（复用其 task），并把当前小窗恢复全屏。
        if (plan.swapped) {
            Log.i(
                LOG_TAG,
                "miniReuse switch target=${target.taskId} pkg=${target.packageName} " +
                    "swap=${plan.swap?.taskId} current=$packageName lookupMs=$lookupMs " +
                    "totalMs=${SystemClock.uptimeMillis() - tLaunch}",
            )
            val options = MiniWindowGeometry.activityOptionsBundle(context, miniWindowSettings, anchor)
            val tCall = SystemClock.uptimeMillis()
            val ok = ShizukuManager.startActivityFromRecents(target.taskId, options)
            Log.i(
                LOG_TAG,
                "miniReuse switch-ok ok=$ok callMs=${SystemClock.uptimeMillis() - tCall}",
            )
            if (ok) plan.swap?.let { restoreTaskToFullscreen(context, it) }
            return if (ok) MiniReuseResult.Switched else MiniReuseResult.NotHandled
        }
        val taskId = target.taskId
        val preWindowingMode = target.windowingMode
        val taskVisible = target.visible
        // 目标已是前台小窗（可见 freeform task）→ 切全屏（toggle），不再开小窗。
        // 复用原 task（保留页面状态），由 [restoreTaskToFullscreen] 回读确认。
        if (miniReuseVisibleTaskEnabled() && taskVisible && target.isFreeform) {
            Log.i(
                LOG_TAG,
                "miniReuse toggle-to-fullscreen task=$taskId pkg=$packageName user=$userId " +
                    "preMode=$preWindowingMode lookupMs=$lookupMs " +
                    "totalMs=${SystemClock.uptimeMillis() - tLaunch}",
            )
            restoreTaskToFullscreen(context, target)
            return MiniReuseResult.Toggled
        }
        // 前台（非小窗）task 不复用：见 [miniReuseVisibleTaskEnabled]。
        if (miniReuseVisibleTaskEnabled() && taskVisible) {
            Log.i(
                LOG_TAG,
                "miniReuse skip=visible task=$taskId preMode=$preWindowingMode user=$userId " +
                    "lookupMs=$lookupMs totalMs=${SystemClock.uptimeMillis() - tLaunch} pkg=$packageName",
            )
            return MiniReuseResult.NotHandled
        }
        val options = MiniWindowGeometry.activityOptionsBundle(context, miniWindowSettings, anchor)
        val tCall = SystemClock.uptimeMillis()
        val ok = ShizukuManager.startActivityFromRecents(taskId, options)
        Log.i(
            LOG_TAG,
            "miniReuse task=$taskId preMode=$preWindowingMode ok=$ok visible=$taskVisible user=$userId " +
                "lookupMs=$lookupMs callMs=${SystemClock.uptimeMillis() - tCall} " +
                "totalMs=${SystemClock.uptimeMillis() - tLaunch} pkg=$packageName",
        )
        return if (ok) MiniReuseResult.Reused else MiniReuseResult.NotHandled
    }

    /**
     * 以 freeform 小窗启动任意 Activity 用的启动参数（含 MIUI 合成层补偿）。
     *
     * 给本应用自己的页面用（如动作选择页横屏小窗）：调用方构造好自己的 Intent 后，
     * 把本参数作为 [android.content.Context.startActivity] 的 options 传入。
     * 非小窗场景传 null。
     */
    fun miniWindowActivityOptions(
        context: Context,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        anchor: Pair<Int, Int>? = null,
    ): Bundle = MiniWindowGeometry.activityOptionsBundle(context, miniWindowSettings, anchor)

    /** 解析 + 系统 launch intent 兜底，统一补 NEW_TASK；解析不到返回 null。 */
    private fun buildLaunchIntent(
        context: Context,
        packageName: String,
        className: String,
        intentAction: String,
        data: String,
        extras: Map<String, String>,
    ): Intent? {
        val pm = context.packageManager
        val intent = resolveLaunchIntent(pm, packageName, className, intentAction, data, extras)
            ?: pm.getLaunchIntentForPackage(packageName)?.also { fallback ->
                // 兜底保持系统启动 Intent：自定义 action/data 命不中时不塞进去，
                // 否则本来能启动的应用也会失败；extras 不改变解析结果，保留。
                extras.forEach { (key, value) -> fallback.putExtra(key, value) }
            }
            ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
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
        anchor: Pair<Int, Int>?,
    ): Boolean {
        if (ShizukuManager.currentStatus().isReady) {
            val intent = buildLaunchIntent(context, packageName, className, intentAction, data, extras)
            if (intent != null) {
                val options = if (miniWindow) {
                    MiniWindowGeometry.activityOptionsBundle(context, miniWindowSettings, anchor)
                } else {
                    null
                }
                // 首选 binder：options 和 userId 同一次调用生效，小窗不会先按系统尺寸显示。
                if (ShizukuManager.startActivityAsUser(intent, userId, options)) return true
            }
            // binder 被 ROM 拒绝时退回 shell；小窗再用 task resize 兜底（会有一次尺寸跳变）。
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
                    anchor = anchor,
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
            anchor = anchor,
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
        anchor: Pair<Int, Int>?,
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
            applyMiniWindowBounds(context, packageName, userId, miniWindowSettings, anchor)
        }
        return true
    }

    /** `am start` 异步返回：轮询 `am stack list` 找到本次 task，再按设置 resize。 */
    private suspend fun applyMiniWindowBounds(
        context: Context,
        packageName: String,
        userId: Int,
        settings: MiniWindowSettings,
        anchor: Pair<Int, Int>?,
    ) {
        val t0 = SystemClock.uptimeMillis()
        var taskId: Int? = null
        var polls = 0
        for (attempt in 0 until 6) {
            if (attempt > 0) delay(120)
            polls = attempt + 1
            val list = ShizukuManager.executeShell("am stack list", maxChars = 200_000)
            taskId = parseTaskId(list.stdout, packageName, userId)
            if (taskId != null) break
        }
        val id = taskId ?: run {
            Log.i(LOG_TAG, "miniWindow bounds NOT FOUND polls=$polls t=${SystemClock.uptimeMillis() - t0}ms")
            return
        }
        val bounds = MiniWindowGeometry.resolveBounds(context, settings, anchor)
        ShizukuManager.executeShell(
            "am task resize $id ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}",
        )
        Log.i(
            LOG_TAG,
            "miniWindow bounds applied task=$id polls=$polls elapsedMs=${SystemClock.uptimeMillis() - t0}",
        )
    }

    private fun parseTaskId(output: String, packageName: String, userId: Int): Int? {
        val regex = Regex("""taskId=(\d+): ${Regex.escape(packageName)}/\S+.*userId=$userId""")
        return regex.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun launchCloneViaLauncherApps(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings,
        userId: Int,
        anchor: Pair<Int, Int>?,
    ): Boolean = try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val user: UserHandle = AppIcons.userHandle(userId) ?: return false
        val activity = launcherApps.getActivityList(packageName, user)
            .firstOrNull { className.isBlank() || it.componentName.className == className }
            ?: return false
        val options = if (miniWindow) {
            MiniWindowGeometry.activityOptionsBundle(context, miniWindowSettings, anchor)
        } else {
            null
        }
        launcherApps.startMainActivity(activity.componentName, user, null, options)
        true
    } catch (e: Exception) {
        Log.e(LOG_TAG, "clone launcherApps launch failed user=$userId pkg=$packageName", e)
        false
    }

    /**
     * 打开系统「应用详情」页；无桌面入口的系统包只能这样进入。
     *
     * 分身（userId 非 0 且非当前用户）与主应用包名相同，设置页按 `package:` 只认当前用户的
     * 实例，直接启动只会显示主应用的详情。MIUI/XSpace 的分身用户里没有系统设置
     * （`pm list packages --user 999` 不含 com.android.settings），`startActivityAsUser`
     * 也解析不到；MIUI 的详情页（com.miui.securitycenter 的应用管理）改为读
     * `package_name` + `miui.intent.extra.USER_ID` 定位实例，所以这条链路只补 extra，
     * 仍按当前用户启动。非 MIUI（如工作资料）没有这套 extra，仍按目标用户启动。
     */
    suspend fun openAppDetails(context: Context, packageName: String, userId: Int = 0): Boolean = try {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (userId != 0 && userId != Process.myUserHandle().hashCode()) {
            if (!hasMiuiAppManager(context)) {
                if (ShizukuManager.currentStatus().isReady &&
                    ShizukuManager.startActivityAsUser(intent, userId, null)
                ) {
                    return true
                }
            } else {
                intent.putExtra(MIUI_EXTRA_PACKAGE_NAME, packageName)
                intent.putExtra(MIUI_EXTRA_USER_ID, userId)
            }
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.e(LOG_TAG, "openAppDetails failed pkg=$packageName user=$userId", e)
        false
    }

    private const val MIUI_APP_MANAGER_PACKAGE = "com.miui.securitycenter"

    /** MIUI 应用详情页认的实例定位参数；AOSP 设置页没有这两个 extra，会直接忽略。 */
    private const val MIUI_EXTRA_PACKAGE_NAME = "package_name"
    private const val MIUI_EXTRA_USER_ID = "miui.intent.extra.USER_ID"

    /** MIUI 的详情页由应用管理（com.miui.securitycenter）承接，只有它认上面的 extra。 */
    private fun hasMiuiAppManager(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(MIUI_APP_MANAGER_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * 启动快捷方式：优先直接启动落库的显式 intent，失败再走 LauncherApps / Shizuku
     * （后者用于启动应用未导出的 shortcut trampoline）。
     *
     * 普通快捷方式（如 Bitwarden 密码生成器）的 intent 通常指向可导出 Activity，
     * 直接启动最稳；LauncherApps 在非默认 launcher 上可能抛权限异常，Shizuku 又依赖
     * 用户授权，都不该挡在显式 intent 前面。
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
        if (intentUris.isNotEmpty() &&
            launchShortcutIntents(context, intentUris, label, showFailureToast = false)
        ) {
            Log.i(LOG_TAG, "shortcut launched via explicit intent pkg=$packageName id=$shortcutId")
            return true
        }
        if (packageName.isNotBlank() && !shortcutId.isNullOrBlank()) {
            if (startShortcutViaLauncherApps(context, packageName, shortcutId)) {
                Log.i(LOG_TAG, "shortcut launched via LauncherApps pkg=$packageName id=$shortcutId")
                return true
            }
            if (ShizukuManager.startShortcut(packageName, shortcutId)) {
                Log.i(LOG_TAG, "shortcut launched via Shizuku pkg=$packageName id=$shortcutId")
                return true
            }
        }
        Log.w(
            LOG_TAG,
            "shortcut launch failed pkg=$packageName id=$shortcutId intents=${intentUris.size}",
        )
        if (intentUris.isNotEmpty()) {
            showToast(context.getString(R.string.launch_shortcut_info_failed, label))
        }
        return false
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

    fun launchShortcutIntents(
        context: Context,
        intentUris: List<String>,
        label: String,
        /** false 时静默失败，供 fallback 链先试探；全部路径失败后由调用方统一提示。 */
        showFailureToast: Boolean = true,
    ): Boolean {
        if (intentUris.isEmpty()) return false
        return try {
            val intents = intentUris.map {
                Intent.parseUri(it, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val resolved = intents.filter { context.packageManager.resolveActivity(it, 0) != null }
            if (resolved.isEmpty()) {
                if (showFailureToast) {
                    showToast(context.getString(R.string.launch_shortcut_info_failed, label))
                }
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
            if (showFailureToast) {
                showToast(context.getString(R.string.launch_shortcut_info_failed, label))
            }
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
        anchor: Pair<Int, Int>? = null,
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
                return MiniWindow.startActivity(context, intent, miniWindowSettings, anchor)
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

    /**
     * 「小窗当前应用」：复用普通启动链路，主用户 / 分身 / 有分身的主应用都走同一套
     * ActivityOptions 与 XSpace 规避逻辑；userId 由 [currentUserIdForPackage] 判定。
     */
    suspend fun launchAppInPopup(
        context: Context,
        packageName: String,
        className: String,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
        userId: Int = 0,
        anchor: Pair<Int, Int>? = null,
    ): Boolean = launchApp(
        context = context,
        packageName = packageName,
        className = className,
        miniWindow = true,
        miniWindowSettings = miniWindowSettings,
        intentAction = intentAction,
        data = data,
        extras = extras,
        userId = userId,
        anchor = anchor,
    )

    /**
     * 当前前台实例的 userId。分身与主应用同包名，只能从系统任务列表确认实例；
     * 非分身包直接返回 0，避免每次 popup 都走 shell。
     */
    /**
     * 该包当前实例的 userId：优先可见任务，取不到再退回最近任务。
     * 有分身且 Shizuku 就绪时才会执行 shell 查询，否则直接 0（主用户）。
     */
    suspend fun currentUserIdForPackage(context: Context, packageName: String): Int =
        withContext(Dispatchers.IO) {
            if (!AppIcons.hasClone(context, packageName)) return@withContext 0
            if (!ShizukuManager.currentStatus().isReady) return@withContext 0
            val output = runCatching {
                ShizukuManager.executeShell("am stack list", maxChars = 200_000).stdout
            }.getOrNull() ?: return@withContext 0
            parseVisibleTaskUserId(output, packageName)
                ?: parseRecentTaskUserId(output, packageName)
                ?: 0
        }

    /**
     * 从 `am stack list` 里取该包可见任务的 userId。任务按顶层优先输出，第一个命中的
     * 就是焦点实例；主应用与分身同包名时靠这条区分。
     */
    internal fun parseVisibleTaskUserId(output: String, packageName: String): Int? {
        val regex = Regex(
            """taskId=\d+:\s+${Regex.escape(packageName)}/\S+.*?\buserId=(\d+)\s+visible=true\b""",
        )
        return regex.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * 从 `am stack list` 里取该包最近使用任务的 userId（不要求 visible）。
     * 输出按最近优先，第一个命中就是最近一次使用的实例；背景里的分身任务同样能命中。
     */
    internal fun parseRecentTaskUserId(output: String, packageName: String): Int? {
        val regex = Regex(
            """taskId=\d+:\s+${Regex.escape(packageName)}/\S+.*?\buserId=(\d+)\b""",
        )
        return regex.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /** `taskId=97417: com.tencent.mm/com.tencent.mm.ui.LauncherUI ... userId=0 ...` */
    private val STACK_TASK_REGEX = Regex(
        """taskId=(\d+): ([^/\s]+)/\S+.*\buserId=(\d+)""",
    )

    /** RootTask 的 configuration 行：`... mWindowingMode=freeform ...` */
    private val ROOT_WINDOWING_MODE_REGEX = Regex("""mWindowingMode=([\w-]+)""")

    /** RootTask 的 configuration 行：`... mActivityType=home ...` */
    private val ROOT_ACTIVITY_TYPE_REGEX = Regex("""mActivityType=(\w+)""")
}


internal object MiniWindowGeometry {

    fun realScreenSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.maximumWindowMetrics.bounds
        return Point(bounds.width(), bounds.height())
    }

    /**
     * MIUI13+/澎湃对 freeform 小窗在合成层再缩一次显示，但 WindowManager 帧仍是请求的 bounds，
     * 所以必须把宽高乘以 1/scale 抵消，否则实际窗口只有预览的 scale 倍大。
     *
     * scale 优先取系统实际值（[FreeformScale] 从 `dumpsys window windows` 的窗口
     * transform 采样，启动后异步刷新），读不到时退回实测常量：竖屏 0.7、横屏 0.56。
     * 横屏沿用 1/0.7 只有预览的 0.8 倍，这正是「横屏小窗比预览小」的成因。
     */
    private fun miuiFreeformCompensation(landscape: Boolean): Float {
        if (!isMiui13Plus()) return 1f
        return FreeformScale.current(landscape)
    }

    fun bounds(settings: WindowBounds, realSw: Int, realSh: Int, landscape: Boolean): Rect {
        val (winW, winH) = windowSize(settings, realSw, realSh, landscape)
        val left = (realSw * settings.x.coerceIn(0f, 1f)).roundToInt()
        val top = (realSh * settings.y.coerceIn(0f, 1f)).roundToInt()
        return Rect(left, top, left + winW, top + winH)
    }

    /** 请求的 freeform 帧尺寸（已乘 MIUI 合成层补偿）。 */
    private fun windowSize(
        settings: WindowBounds,
        realSw: Int,
        realSh: Int,
        landscape: Boolean,
    ): Pair<Int, Int> {
        val (visibleW, visibleH) = visibleWindowSize(settings, realSw, realSh)
        val comp = miuiFreeformCompensation(landscape)
        val winW = (visibleW * comp).roundToInt()
        val winH = (visibleH * comp).roundToInt()
        return winW to winH
    }

    /** 实际显示尺寸（未做合成层补偿）：定位必须用它，否则右侧/竖向会偏出触发点。 */
    private fun visibleWindowSize(
        settings: WindowBounds,
        realSw: Int,
        realSh: Int,
    ): Pair<Float, Float> {
        val w = realSw * settings.width.coerceIn(0.2f, 1.5f)
        val h = realSh * settings.height.coerceIn(0.2f, 1.5f)
        return w to h
    }

    /**
     * 选方向；跟随开关开着且有触点时按统一触发点定位（横向贴触发侧、竖向中心对准），
     * 否则保持配置里的固定位置。尺寸两种模式一致。
     */
    fun resolveBounds(
        context: Context,
        miniWindowSettings: MiniWindowSettings,
        anchor: Pair<Int, Int>?,
    ): Rect {
        val size = realScreenSize(context)
        val landscape = context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val settings = miniWindowSettings.forOrientation(landscape)
        if (miniWindowSettings.followTrigger && anchor != null && size.x > 0 && size.y > 0) {
            val (winW, winH) = windowSize(settings, size.x, size.y, landscape)
            val (visibleW, visibleH) = visibleWindowSize(settings, size.x, size.y)
            val safe = context.windowManager().systemBarsSafeArea()
            val offset = miniWindowSettings.placement(landscape).resolveTopLeft(
                anchorX = anchor.first,
                anchorY = anchor.second,
                widthPx = visibleW,
                heightPx = visibleH,
                screenWidthPx = size.x,
                screenHeightPx = size.y,
                safeTopPx = safe.top,
                safeBottomPx = safe.bottom,
                density = context.resources.displayMetrics.density,
                // 小窗以中心对准触发点（面板才是 1/4）。
                verticalAnchorFraction = 0.5f,
            )
            val left = offset.x.roundToInt()
            val top = offset.y.roundToInt()
            return Rect(left, top, left + winW, top + winH)
        }
        return bounds(settings, size.x, size.y, landscape)
    }

    /**
     * 小窗启动参数。Android 17 起 `ActivityOptions.setLaunchWindowingMode` 是
     * BLOCKED + TEST-API，普通 release 包反射会被 hiddenapi 拒绝；AOSP 的
     * `ActivityOptions(Bundle)` 会读取 `android.activity.windowingMode`，直接把
     * 这个 key 写进 bundle，不再依赖隐藏 API。
     */
    fun activityOptionsBundle(
        context: Context,
        miniWindowSettings: MiniWindowSettings,
        anchor: Pair<Int, Int>? = null,
    ): Bundle {
        val launchBounds = resolveBounds(context, miniWindowSettings, anchor)
        return ActivityOptions.makeBasic()
            .apply { setLaunchBounds(launchBounds) }
            .toBundle()
            .apply { putInt(KEY_WINDOWING_MODE, WINDOWING_MODE_FREEFORM) }
    }

    /** 把已有 freeform 任务转回全屏用的启动参数（互换时恢复旧小窗）。 */
    fun fullscreenOptionsBundle(): Bundle =
        ActivityOptions.makeBasic()
            .toBundle()
            .apply { putInt(KEY_WINDOWING_MODE, WINDOWING_MODE_FULLSCREEN) }

    private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
    private const val WINDOWING_MODE_FREEFORM = 5
    private const val WINDOWING_MODE_FULLSCREEN = 1

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
        anchor: Pair<Int, Int>? = null,
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
        anchor = anchor,
    )

    fun startActivity(
        context: Context,
        intent: Intent,
        miniWindowSettings: MiniWindowSettings,
        anchor: Pair<Int, Int>? = null,
    ): Boolean {
        return try {
            val t0 = SystemClock.uptimeMillis()
            val activityOptions = MiniWindowGeometry.activityOptionsBundle(context, miniWindowSettings, anchor)
            val tOptions = SystemClock.uptimeMillis()
            context.startActivity(intent, activityOptions)
            Log.i(
                LOG_TAG,
                "miniWindow startActivity done optionsMs=${tOptions - t0} t=${SystemClock.uptimeMillis()}",
            )
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "startActivity in mini window failed", e)
            showToast(context.getString(R.string.launch_mini_window_failed))
            false
        }
    }
}

