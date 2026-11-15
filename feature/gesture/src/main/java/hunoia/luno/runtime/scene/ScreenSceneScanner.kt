package hunoia.luno.runtime.scene

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.ScreenSceneEvent
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.gesture.BuildConfig
import hunoia.luno.link.LinkUrls
import hunoia.luno.runtime.overlay.text.A11yTextSnapshotReader
import hunoia.luno.runtime.overlay.text.TextSnapshot
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 情景识别：当前前台应用窗口的无障碍文本读取器。
 *
 * 架构 §7.4 边界：只由无障碍事件驱动、只读当前前台应用窗口、带时间/节点预算（复用
 * [A11yTextSnapshotReader] 的 250ms / 1500 节点）、不做定时轮询、不扫后台窗口、不自动 OCR。
 *
 * 文本只在进程内经 [ScreenSceneEvent] 交给建议胶囊，本类不落任何持久化。
 *
 * 两条产线：
 * - **首扫**：抓到文本就立刻推送，不等待任何特权通道；被新事件取消时什么都不记，
 *   下一轮照常重来。
 * - **链接补充**（可选）：首扫之后在后台经 Shizuku 读 XWeb 日志补真实链接，拿到且这一屏
 *   还没切走时，用同一个 [ScreenSceneEvent.screenKey] 再推一次。补充失败就静默降级为
 *   「没有链接」，绝不影响首扫的弹出时机。
 */
class ScreenSceneScanner(
    private val service: AccessibilityService,
    private val ownPackage: String,
    private val scope: CoroutineScope,
    /** [eventPackage] = 本条事件自带的前台包名，用于便宜的桌面判定；null 时现查。 */
    private val contextOf: (eventPackage: String?) -> SceneScanContext,
    /** 自家全屏浮层在屏时不扫：面板/识屏/自家界面挡住时读到的不是用户在看的界面。 */
    private val surfaceBusy: () -> Boolean,
    private val publish: (ScreenSceneEvent) -> Unit,
    /** 特权链接补充通道是否可用；不可用时首扫连地址栏都不查，少一次 IPC。 */
    private val urlProbeAvailable: () -> Boolean = { XWebUrlProbe.isAvailable() },
) {

    /** 树遍历是 Binder 同步调用，只在单线程上串行，避免多轮扫描叠一起。 */
    private val scanDispatcher = Dispatchers.Default.limitedParallelism(1)

    private val scheduler = SceneScanScheduler()
    private val ledger = SceneScanLedger()
    private val reader by lazy { A11yTextSnapshotReader(service, ownPackage) }

    /** 一次事件驱动的「等待稳定 → 首扫」。被后续事件取消是常态。 */
    private var settleJob: Job? = null

    /** 链接补充的跟随任务：与首扫解耦，屏幕变了才丢结果。 */
    private var refineJob: Job? = null

    /**
     * 最近一条通过门禁的事件所属应用。
     *
     * 扫描落地前用它确认「扫的还是这一屏」：树遍历 + 补链接期间用户可能已经切走，
     * 迟到的结果不能在别的界面上弹出来。
     */
    private var lastEventPackage: String = ""

    /** 调试用：只在门禁结论变化时打一条，避免每个事件刷一行。 */
    private var lastDecision: SceneScanDecision? = null

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // 先做纯整型判定：判定上下文要查前台包（binder），不能为焦点/选区/窗口列表变化付这份钱。
        if (!SceneScanGate.isSceneEventType(event.eventType)) {
            if (BuildConfig.DEBUG && lastDecision != SceneScanDecision.NotSceneEvent) {
                Log.i(LOG_TAG, "scene scan idle: eventType=${event.eventType}")
                lastDecision = SceneScanDecision.NotSceneEvent
            }
            return
        }
        val eventPackage = event.packageName?.toString()
        val context = contextOf(eventPackage)
        val decision = SceneScanGate.decide(event.eventType, context, eventPackage, ownPackage)
        if (decision != SceneScanDecision.Accepted) {
            if (BuildConfig.DEBUG && decision != lastDecision) {
                Log.i(LOG_TAG, "scene scan ignored: $decision pkg=$eventPackage")
            }
            lastDecision = decision
            if (leavesScreen(decision, event.eventType)) forgetScreen()
            return
        }
        if (BuildConfig.DEBUG && lastDecision != null) {
            Log.i(LOG_TAG, "scene scan armed: pkg=$eventPackage")
        }
        lastDecision = null
        val packageName = eventPackage ?: return
        lastEventPackage = packageName
        val delayMs = scheduler.onSignal(
            nowMs = SystemClock.uptimeMillis(),
            packageName = packageName,
            screenChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )
        schedule(packageName, delayMs)
    }

    /**
     * 用户显式要求扫一次（建议球长按）：跳过同屏去重当 [force]，
     * 但开关关闭 / 锁屏 / 息屏 / 桌面一律不扫。返回是否已受理。
     */
    fun requestNow(force: Boolean): Boolean {
        val context = contextOf(null)
        if (!context.enabled || context.locked || !context.interactive || context.launcher) {
            return false
        }
        // 没有任何可用规则时，扫了也只会得到空结果。
        if (!context.hasRules) return false
        settleJob?.cancel()
        settleJob = scope.launch { scan(expectedPackage = null, force = force) }
        return true
    }

    fun release() {
        settleJob?.cancel()
        settleJob = null
        refineJob?.cancel()
        refineJob = null
    }

    /**
     * 被门禁拒绝的事件里，哪些代表「用户已经离开这一屏」。
     *
     * 自家界面只看窗口状态变化：浮层/胶囊自己的内容变化（重组、徽标刷新）也会带本包名，
     * 那些不是「用户离开」，按内容事件一律当真会把同屏账反复清零。
     */
    private fun leavesScreen(decision: SceneScanDecision, eventType: Int): Boolean = when (decision) {
        SceneScanDecision.Disabled,
        SceneScanDecision.Locked,
        SceneScanDecision.ScreenOff,
        SceneScanDecision.Launcher,
        -> true

        SceneScanDecision.OwnApp -> eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        else -> false
    }

    private fun forgetScreen() {
        settleJob?.cancel()
        settleJob = null
        refineJob?.cancel()
        refineJob = null
        lastEventPackage = ""
        ledger.forgetScreen()
    }

    private fun schedule(packageName: String, delayMs: Long) {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(delayMs)
            scan(expectedPackage = packageName, force = false)
        }
    }

    private suspend fun scan(expectedPackage: String?, force: Boolean) {
        // 排队期间用户已经切走 / 锁屏 / 回桌面：这条扫描从一开始就不该执行。
        if (expectedPackage != null && expectedPackage != lastEventPackage) return
        val context = contextOf(expectedPackage)
        if (!context.enabled || !context.hasRules || context.locked || !context.interactive) return
        if (context.launcher) return
        if (surfaceBusy()) return
        scheduler.onScanStarted(SystemClock.uptimeMillis())
        val snapshot = withContext(scanDispatcher) {
            // textOnly：情景识别只要文本叶子，图片 / 布局 / 逐字坐标都是白分配。
            runCatching { reader.capture(downX = -1, downY = -1, textOnly = true) }.getOrNull()
        } ?: return
        val packageName = snapshot.targetPackage
        if (packageName.isBlank()) return
        // 事件说的是 A，抓到的是 B：用户在这几百毫秒里切走了，A 的卡片不能压在 B 上。
        if (expectedPackage != null && packageName != expectedPackage) {
            if (BuildConfig.DEBUG) {
                Log.i(LOG_TAG, "scene scan dropped: expected=$expectedPackage got=$packageName")
            }
            return
        }
        val text = toPlainText(snapshot)
        if (snapshot.leaves.isEmpty() || text.isBlank()) {
            if (BuildConfig.DEBUG) {
                Log.i(LOG_TAG, "scene scan empty: pkg=$packageName leaves=${snapshot.leaves.size}")
            }
            scheduler.onScanResult(SystemClock.uptimeMillis(), published = false)
            retryEmptySnapshot(expectedPackage, force)
            return
        }
        val screenKey = fingerprint(packageName, text)
        if (!force && ledger.isSameAsPublished(packageName, screenKey)) {
            scheduler.onScanResult(SystemClock.uptimeMillis(), published = false)
            return
        }
        // 地址栏定向读取只有几次 IPC（<150ms），且是补链接的前提；
        // 补充通道不可用时不查，首扫直接落地。
        val addressHost = if (urlProbeAvailable()) {
            withContext(scanDispatcher) { addressHostOf(snapshot) }
        } else {
            null
        }
        // 首扫立刻推送：链接补充从此只在后台跟随，不再把胶囊压在几秒的 shell 往返之后。
        deliver(packageName, screenKey, text, snapshot.complete, refinedUrl = null)
        if (addressHost != null) {
            refineUrlAsync(packageName, screenKey, text, snapshot.complete, addressHost)
        }
    }

    /**
     * 页面刚打开时 WebView 的无障碍文本树常常还是空的（XWeb 尤其明显：标题栏先到、网页内容
     * 后到），而加载之后又可能不再有内容事件。这里只做**一次跟随补扫**，不是定时轮询：
     * 同一应用最多 [SceneScanLedger.MAX_EMPTY_RETRIES] 次，换屏 / 推送成功即复位。
     */
    private fun retryEmptySnapshot(expectedPackage: String?, force: Boolean) {
        if (force || expectedPackage == null) return
        if (expectedPackage != lastEventPackage) return
        if (!ledger.shouldRetryEmpty(expectedPackage)) return
        // 注意：这里是当前协程自己续一拍，不取消自己（settleJob 即将结束）。
        settleJob = scope.launch {
            delay(EMPTY_RETRY_DELAY_MS)
            scan(expectedPackage = expectedPackage, force = false)
        }
    }

    /**
     * 特权补充：给当前屏补一条真实链接。
     *
     * 只有「界面上确实写着域名」时才去读日志（省掉绝大多数无谓的 shell 往返），且读回来的
     * host 必须和界面上那一行对得上——日志里最后一条 URL 可能属于已经切走的上一屏。
     * 失败静默降级为「没有链接」。
     */
    private fun refineUrlAsync(
        expectedPackage: String,
        screenKey: String,
        text: String,
        complete: Boolean,
        addressHost: String,
    ) {
        refineJob?.cancel()
        refineJob = scope.launch {
            val url = refineUrl(expectedPackage, screenKey, addressHost) ?: return@launch
            // 补链接这几秒里这一屏可能已经换了：宁可不带链接，也不能挂错屏。
            if (!isStillCurrent(expectedPackage, screenKey)) return@launch
            deliver(expectedPackage, screenKey, text, complete, refinedUrl = url)
        }
    }

    /**
     * 页面刚打开时标题栏和日志还没就位（实测首扫经常落在两者之前），所以拿不到时等一拍再读，
     * 最多 [MAX_URL_RETRIES] 次；期间这一屏被切走就提前放弃。
     */
    private suspend fun refineUrl(
        expectedPackage: String,
        screenKey: String,
        addressHost: String,
    ): String? {
        var attempt = 0
        while (true) {
            if (!isStillCurrent(expectedPackage, screenKey)) return null
            val url = refineUrlOnce(addressHost)
            if (url != null) return url
            if (attempt >= MAX_URL_RETRIES) return null
            attempt++
            delay(RETRY_DELAY_MS)
        }
    }

    private suspend fun refineUrlOnce(addressHost: String): String? {
        val candidates = XWebUrlProbe.recent(
            nowEpochMs = System.currentTimeMillis(),
            maxAgeMs = URL_MAX_AGE_MS,
        )
        // 新→旧挑第一条同站的：日志里最新那条常是内置浏览器预加载的别的页面。
        for (hit in candidates) {
            val urlHost = LinkUrls.hostOf(hit.url) ?: continue
            if (!SceneUrlMatch.sameSite(addressHost, urlHost)) continue
            return hit.url
        }
        return null
    }

    /** 这一屏还是不是「用户在看的屏」：同屏指纹没变，也没有别的应用的事件插进来。 */
    private fun isStillCurrent(expectedPackage: String, screenKey: String): Boolean =
        ledger.isSameAsPublished(expectedPackage, screenKey) &&
            (lastEventPackage.isEmpty() || lastEventPackage == expectedPackage)

    private fun deliver(
        packageName: String,
        screenKey: String,
        text: String,
        complete: Boolean,
        refinedUrl: String?,
    ) {
        publish(
            ScreenSceneEvent(
                text = text,
                packageName = packageName,
                screenKey = screenKey,
                truncated = !complete || text.length >= MAX_TEXT_CHARS,
                refinedUrl = refinedUrl,
            ),
        )
        // 推送成功之后才记同屏账：被取消 / 被切走的扫描不能留下「已推送」的假账。
        ledger.onPublished(packageName, screenKey)
        lastEventPackage = packageName
        scheduler.onScanResult(SystemClock.uptimeMillis(), published = true)
        if (BuildConfig.DEBUG) {
            Log.i(
                LOG_TAG,
                "scene published: pkg=$packageName key=$screenKey chars=${text.length} " +
                    "refined=${refinedUrl != null}",
            )
        }
    }

    /**
     * 这一屏顶部那条地址栏的 host。
     *
     * 先定向查地址栏控件（快）：整棵树是深度优先的，内置浏览器把标题栏排在网页之后，
     * 带预算的遍历基本到不了那里。快照里恰好包含了也认（浏览器类应用排在前面）。
     */
    private suspend fun addressHostOf(snapshot: TextSnapshot): String? {
        val targeted = withContext(scanDispatcher) { reader.readAddressBarTexts() }
        val fromBar = targeted.firstNotNullOfOrNull { SceneUrlMatch.hostOfLeaf(it) }
        if (fromBar != null) return fromBar
        return snapshot.leaves
            .sortedBy { it.bounds.top }
            .firstNotNullOfOrNull { SceneUrlMatch.hostOfLeaf(it.text) }
    }

    /** 丢掉密码节点：情景识别会把界面文本送进建议卡片，密码框内容不能跟着走。 */
    private fun toPlainText(snapshot: TextSnapshot): String {
        val builder = StringBuilder()
        for (leaf in snapshot.leaves) {
            if (leaf.isPassword) continue
            val leafText = leaf.text
            if (leafText.isBlank()) continue
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(leafText)
            if (builder.length >= MAX_TEXT_CHARS) break
        }
        return builder.toString()
    }

    private fun fingerprint(packageName: String, text: String): String {
        val digest = runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest("$packageName|$text".toByteArray(Charsets.UTF_8))
        }.getOrNull() ?: return text.hashCode().toString(16)
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** 参与规则匹配的文本上限：再长也只是多付一次正则成本，规则抓不到更多。 */
        private const val MAX_TEXT_CHARS = 8_000

        /**
         * 链接的年龄上限。
         *
         * 日志里的 URL 总是「最新在前」，所以同 host 的旧 URL 只会在页面没有重新提交时出现
         * ——那基本就是当前页（内置浏览器回退到已缓存页面不会重新加载）。闸门只负责挡住
         * 明显过期的残留，不负责判页，所以给得宽松；真正判页的是地址栏 host 对账。
         */
        private const val URL_MAX_AGE_MS = 30 * 60_000L

        /** 拿不到链接时的重试：页面刚打开时标题栏 / 日志都还没就位。 */
        private const val MAX_URL_RETRIES = 2
        private const val RETRY_DELAY_MS = 800L

        /** 空快照的跟随补扫间隔：比空闲防抖略长，给 WebView 一点加载时间。 */
        private const val EMPTY_RETRY_DELAY_MS = 900L
    }
}
