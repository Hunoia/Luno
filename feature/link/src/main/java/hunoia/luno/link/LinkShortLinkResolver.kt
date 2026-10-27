package hunoia.luno.link

import android.util.Log
import hunoia.luno.core.LOG_TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * 短链解析（方案 A，联网）：`xhslink.com` 这类应用没有声明的短链，只有跟随后才知道
 * 它最终指向哪里。任何存储/匹配都不在这里做——只负责把「可能的最终地址」按优先级
 * 吐出来，由 [LinkDispatcher] 拿去重新匹配规则。
 *
 * 顺序：真实跟随后的最终 URL → 页面里的 meta refresh / JS 跳转 → 页面里出现的链接。
 * 失败（超时、断网、404 无跳转）返回空列表，调用方用原链接继续走既有流程。
 */
object LinkShortLinkResolver {

    private const val ConnectTimeoutMs = 3_000
    private const val ReadTimeoutMs = 3_000

    /** 总预算：连接 + 读取之外再给一点余量，避免坏站点把点击拖成好几秒。 */
    private const val TotalTimeoutMs = 7_000L

    private const val MaxRedirects = 5
    private const val MaxBodyBytes = 64 * 1024

    private const val UserAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    /** 解析候选（可能为空）；只对 http(s) 发请求。 */
    suspend fun candidates(rawUrl: String): List<String> {
        if (!LinkUrls.isDispatchable(rawUrl)) return emptyList()
        val scheme = LinkUrls.schemeOf(rawUrl)?.lowercase()
        if (scheme != "http" && scheme != "https") return emptyList()
        if (LinkUrls.isPrivateHost(LinkUrls.hostOf(rawUrl))) {
            Log.i(LOG_TAG, "short link skipped (private host): ${rawUrl.take(80)}")
            return emptyList()
        }
        cached(rawUrl)?.let { return it }
        return withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(TotalTimeoutMs) {
                runCatching { fetch(rawUrl) }
                    .onFailure { Log.w(LOG_TAG, "short link resolve failed: ${it.message}") }
                    .getOrNull()
            }
            if (result.isNullOrEmpty()) {
                Log.i(LOG_TAG, "short link unresolved: ${rawUrl.take(80)}")
                emptyList()
            } else {
                Log.i(LOG_TAG, "short link resolved: ${rawUrl.take(60)} -> ${result.first().take(120)}")
                remember(rawUrl, result)
                result
            }
        }
    }

    /**
     * 进程内结果缓存：同一条短链常被胶囊、识屏、选择页连续解析，重复联网只会让点击变慢。
     * 只缓存成功结果，且只在进程存活期有效（短链目标可能变，但一次会话内足够稳定）。
     */
    private const val CacheCap = 32
    private val cache = object : LinkedHashMap<String, List<String>>(CacheCap, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) =
            size > CacheCap
    }

    private fun cached(url: String): List<String>? = synchronized(cache) {
        cache[url]?.toList()
    }

    private fun remember(url: String, result: List<String>) = synchronized(cache) {
        cache[url] = result.toList()
    }

    private fun fetch(rawUrl: String): List<String> {
        var current = rawUrl
        var hop = 0
        while (hop <= MaxRedirects) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = ConnectTimeoutMs
                readTimeout = ReadTimeoutMs
                // 必须关掉 JDK 自动跟随：它最多跟 20 跳且不校验内网地址，
                // 会绕过 MaxRedirects 与 [LinkUrls.isPrivateHost] 两道闸。
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", UserAgent)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return emptyList()
                    val next = URL(URL(current), location).toString()
                    if (LinkUrls.isPrivateHost(LinkUrls.hostOf(next))) {
                        Log.i(LOG_TAG, "short link redirect to private host, stop: ${next.take(80)}")
                        return emptyList()
                    }
                    current = next
                    hop++
                    continue
                }
                val finalUrl = conn.url.toString()
                val out = LinkedHashSet<String>()
                if (finalUrl != rawUrl) out += finalUrl
                if (code in 200..299) {
                    val body = readBody(conn)
                    if (body.isNotEmpty()) out += ShortLinkScan.urlsInHtml(body)
                }
                return out.take(MaxCandidates)
            } finally {
                conn.disconnect()
            }
        }
        return emptyList()
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = runCatching { conn.inputStream }.getOrNull() ?: return ""
        return runCatching {
            val buffer = ByteArray(MaxBodyBytes)
            var total = 0
            while (total < MaxBodyBytes) {
                val read = stream.read(buffer, total, MaxBodyBytes - total)
                if (read <= 0) break
                total += read
            }
            stream.close()
            val charset = charsetOf(conn.contentType)
            String(buffer, 0, total, charset)
        }.getOrDefault("")
    }

    private fun charsetOf(contentType: String?): java.nio.charset.Charset {
        if (contentType != null) {
            val marker = "charset="
            val index = contentType.lowercase().indexOf(marker)
            if (index >= 0) {
                val name = contentType.substring(index + marker.length)
                    .trim()
                    .substringBefore(';')
                    .trim('"', ' ')
                runCatching { return java.nio.charset.Charset.forName(name) }
            }
        }
        return Charsets.UTF_8
    }

    private const val MaxCandidates = 20
}

/**
 * 页面里「可能的跳转目标」扫描（纯函数，可单测）。
 *
 * 优先级：meta refresh → JS 的 `location(.href) =` / `location.replace|assign(...)` →
 * 正文里出现的任意链接（兜底）。JS 经常把斜杠转义成 `\/`，先还原再扫。
 */
object ShortLinkScan {

    private val metaRefresh = Regex(
        """(?i)<meta[^>]+http-equiv\s*=\s*["']?refresh["']?[^>]*content\s*=\s*["']?\s*\d*\s*;?\s*url=([^"'>\s]+)""",
    )

    private val jsLocation = Regex(
        """(?i)(?:window\.|document\.)?location(?:\.href)?\s*(?:=|\.replace\s*\(|\.assign\s*\()\s*["']([^"']+)["']""",
    )

    /** 兜底扫正文前先去掉标签：否则 `href="https://a/x"` 会把引号和 `</a` 一起吞进 URL。 */
    private val htmlTag = Regex("<[^>]*>")

    private val maxUrls = 20

    fun urlsInHtml(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val text = html.replace("\\/", "/")
        val out = LinkedHashSet<String>()
        metaRefresh.findAll(text).forEach { match ->
            normalize(match.groupValues[1])?.let(out::add)
        }
        jsLocation.findAll(text).forEach { match ->
            normalize(match.groupValues[1])?.let(out::add)
        }
        LinkUrls.extractUrls(htmlTag.replace(text, " ")).forEach { out += it }
        return out.take(maxUrls)
    }

    /** 去掉引号/HTML 实体并校验协议；不可分发的返回 null。 */
    private fun normalize(raw: String): String? {
        val value = raw.trim()
            .trim('"', '\'', '<', '>', '(', ')', ';', ',')
            .replace("&amp;", "&")
        if (value.isEmpty()) return null
        val url = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", ignoreCase = true) -> value
            value.startsWith("https://", ignoreCase = true) -> value
            else -> return null
        }
        return url.takeIf { LinkUrls.isDispatchable(it) }
    }
}
