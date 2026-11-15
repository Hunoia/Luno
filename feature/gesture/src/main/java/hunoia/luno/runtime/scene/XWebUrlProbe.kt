package hunoia.luno.runtime.scene

import android.os.SystemClock
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 从 logcat 取「最近提交过的 WebView 页面链接」。
 *
 * 背景：XWeb（微信 / QQ / 企微的 Chromium 分支）在页面主文档提交时把完整 URL 打进 tag
 * `XWeb.Core`，且 release 构建没裁。`READ_LOGS` 只有 shell 身份能读，所以只能经 Shizuku
 * 派生一个 `sh` 去读——这仍然只是一次读操作，不落任何持久化。
 *
 * 为什么不是「只取最后一条」：内置浏览器会**预加载/后台打开**别的 H5（实测可见页是
 * 拼多多时，日志最新一条是后台的应用宝下载页），所以最后一条经常不是用户在看的页面。
 * 这里回一批（新→旧），由调用方挑「host 与界面地址栏同站」的那条。
 *
 * 边界（与 architecture §7.4 的情景识别同源）：
 * - 只在扫描已经发生之后调用，不新增定时轮询、不常驻监听；
 * - 读一次 `logcat -d` 在设备侧约 0.1s（缓冲区大时更久），所以结果做几秒的短缓存：
 *   同一屏的首扫 + 链接补充、以及一次跟随补扫之间不会重复 shell 往返；
 * - 单次调用带超时预算，Shizuku 不可用 / 超时就当没有链接，静默降级；
 * - 只认主文档提交事件里的 `http(s)` URL，`about:blank`（XWeb 预热页）一律丢弃。
 */
internal object XWebUrlProbe {

    /**
     * 输出「新→旧」最多 [MAX_CANDIDATES] 条，格式 `epoch秒 url=...`。
     *
     * 取一批而不是最后一条，是为了跳过预加载页；数量上限既挡输出膨胀，也够覆盖一次导航。
     *
     * `-t [TAIL_LINES]` 不能省：`logcat -d` 会把该 tag 的整个环形缓冲（可达几 MB）读一遍，
     * 而我们只关心最近几次导航。只读尾部几百行后，设备侧往返稳定在几十毫秒。
     */
    private val Command: String get() =
        "logcat -d -s XWeb.Core:V -v epoch -t $TAIL_LINES | " +
            "awk '/DID_COMMIT_PROVISIONAL_LOAD/ { " +
            "if (match(\$0, /url=https?:\\/\\/[^ ]+/)) { n++; " +
            "ts[n % $MAX_CANDIDATES] = \$1; " +
            "u[n % $MAX_CANDIDATES] = substr(\$0, RSTART, RLENGTH) } } " +
            "END { for (k = 0; k < $MAX_CANDIDATES && k < n; k++) { " +
            "i = (n - k) % $MAX_CANDIDATES; print ts[i] \" \" u[i] } }'"

    /** 单次预算：实测设备侧 0.11s，留足余量但绝不允许拖住一次扫描。 */
    private const val TIMEOUT_MS = 800L

    /** 一次最多回多少条候选。 */
    private const val MAX_CANDIDATES = 16

    /** 只读日志尾部这么多行：足够覆盖最近十几次导航，又不会整缓冲扫一遍。 */
    private const val TAIL_LINES = 300

    /** 结果短缓存：日志以秒级变化，同一屏的几次查询没有必要重复起 shell。 */
    private const val CACHE_TTL_MS = 3_000L

    /** 时钟回拨容忍：日志时间戳来自 logd，与本进程墙钟可能差几毫秒。 */
    private const val CLOCK_SKEW_MS = 5_000L

    data class Hit(val url: String, val ageMs: Long)

    private data class RawHit(val loggedAtMs: Long, val url: String)

    private val lock = Mutex()
    private var cachedAtElapsedMs = Long.MIN_VALUE
    private var cachedHits: List<RawHit> = emptyList()

    /** 特权通道是否就绪。调用方据此决定要不要去读地址栏（不读就省一次 IPC）。 */
    fun isAvailable(): Boolean = ShizukuManager.currentStatus().isReady

    /** 新→旧；超过 [maxAgeMs] 的丢弃。 */
    suspend fun recent(nowEpochMs: Long, maxAgeMs: Long): List<Hit> {
        if (!isAvailable()) return emptyList()
        val entries = load()
        val hits = ArrayList<Hit>(entries.size)
        for (entry in entries) {
            val ageMs = nowEpochMs - entry.loggedAtMs
            if (ageMs < -CLOCK_SKEW_MS || ageMs > maxAgeMs) continue
            hits += Hit(url = entry.url, ageMs = ageMs)
        }
        return hits
    }

    private suspend fun load(): List<RawHit> = lock.withLock {
        val now = SystemClock.elapsedRealtime()
        if (cachedAtElapsedMs != Long.MIN_VALUE && now - cachedAtElapsedMs in 0..CACHE_TTL_MS) {
            return@withLock cachedHits
        }
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            ShizukuManager.executeShell(Command, maxChars = 16_384)
        }
        if (result == null || !result.isSuccess) {
            // 失败不刷新时间戳：Shizuku 刚就绪时下一次扫描要能立刻重试。
            return@withLock cachedHits
        }
        cachedHits = parse(result.stdout)
        cachedAtElapsedMs = now
        cachedHits
    }

    private fun parse(stdout: String): List<RawHit> {
        val hits = ArrayList<RawHit>(MAX_CANDIDATES)
        for (line in stdout.lineSequence()) {
            if (hits.size >= MAX_CANDIDATES) break
            if (line.isBlank()) continue
            val loggedAtMs = (line.substringBefore(' ').toDoubleOrNull() ?: continue)
                .times(1000)
                .toLong()
            val url = line.substringAfter(' ', "")
                .trim()
                .removePrefix("url=")
                .ifBlank { continue }
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            hits += RawHit(loggedAtMs = loggedAtMs, url = url)
        }
        return hits
    }
}
