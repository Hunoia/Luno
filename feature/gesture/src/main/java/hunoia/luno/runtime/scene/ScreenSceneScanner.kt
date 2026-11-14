package hunoia.luno.runtime.scene

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import hunoia.luno.core.ScreenSceneEvent
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
 */
class ScreenSceneScanner(
    private val service: AccessibilityService,
    private val ownPackage: String,
    private val scope: CoroutineScope,
    private val contextOf: () -> SceneScanContext,
    /** 自家全屏浮层在屏时不扫：面板/识屏/自家界面挡住时读到的不是用户在看的界面。 */
    private val surfaceBusy: () -> Boolean,
    private val publish: (ScreenSceneEvent) -> Unit,
) {

    /** 树遍历是 Binder 同步调用，只在单线程上串行，避免多轮扫描叠一起。 */
    private val scanDispatcher = Dispatchers.Default.limitedParallelism(1)

    private val scheduler = SceneScanScheduler()
    private val reader by lazy { A11yTextSnapshotReader(service, ownPackage) }

    private var settleJob: Job? = null
    private var lastPackage: String = ""
    private var lastScreenKey: String = ""

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val context = contextOf()
        val eventPackage = event.packageName?.toString()
        val decision = SceneScanGate.decide(event.eventType, context, eventPackage, ownPackage)
        if (decision != SceneScanDecision.Accepted) return
        val delayMs = scheduler.onSignal(SystemClock.uptimeMillis())
        settle(delayMs)
    }

    /**
     * 用户显式要求扫一次（建议球长按）：跳过同屏去重当 [force]，
     * 但开关关闭 / 锁屏 / 息屏一律不扫。返回是否已受理。
     */
    fun requestNow(force: Boolean): Boolean {
        val context = contextOf()
        if (!context.enabled || context.locked || !context.interactive) return false
        settleJob?.cancel()
        settleJob = scope.launch { scan(force) }
        return true
    }

    fun release() {
        settleJob?.cancel()
        settleJob = null
    }

    private fun settle(delayMs: Long) {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(delayMs)
            scan(force = false)
        }
    }

    private suspend fun scan(force: Boolean) {
        scheduler.onScanStarted(SystemClock.uptimeMillis())
        if (surfaceBusy()) return
        val snapshot = withContext(scanDispatcher) {
            runCatching { reader.capture(downX = -1, downY = -1) }.getOrNull()
        } ?: return
        if (snapshot.leaves.isEmpty()) return
        val (text, packageName) = toPlainText(snapshot)
        if (text.isBlank()) return
        val screenKey = fingerprint(packageName, text)
        if (!force && packageName == lastPackage && screenKey == lastScreenKey) return
        lastPackage = packageName
        lastScreenKey = screenKey
        publish(
            ScreenSceneEvent(
                text = text,
                packageName = packageName,
                screenKey = screenKey,
                truncated = !snapshot.complete || text.length >= MAX_TEXT_CHARS,
            ),
        )
    }

    /** 丢掉密码节点：情景识别会把界面文本送进建议卡片，密码框内容不能跟着走。 */
    private fun toPlainText(snapshot: TextSnapshot): Pair<String, String> {
        val builder = StringBuilder()
        for (leaf in snapshot.leaves) {
            if (leaf.isPassword) continue
            val leafText = leaf.text
            if (leafText.isBlank()) continue
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(leafText)
            if (builder.length >= MAX_TEXT_CHARS) break
        }
        return builder.toString() to snapshot.targetPackage
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
    }
}
