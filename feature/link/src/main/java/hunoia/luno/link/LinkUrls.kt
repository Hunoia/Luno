package hunoia.luno.link

import hunoia.luno.config.model.LinkDispatchRule
import hunoia.luno.config.model.LinkMatchType
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 链接识别与规则匹配的纯逻辑（无 Context / 无 android.net.Uri，可直接 JVM 单测）。
 *
 * 口径与 LinkGo 的「提取规则 + 分发规则」子集对齐：
 * - 提取：只认带 `scheme://` 的串，遇空白或汉字即止，尾部标点回退；
 * - 匹配：包含 / 正则 / 精确，规则表顺序取第一条命中；
 * - 改写：模板变量替换，空模板不改写。
 *
 * 刻意不含 LinkGo 的短链 302 解析、链式二次分发（RE_DISPATCH）、提取码规则。
 */
object LinkUrls {

    /** 一次最多认几条链接：再多也不会有用，还拖慢每帧的胶囊刷新。 */
    const val MaxUrls = 20

    private val urlRegex =
        Regex("""(?i)[a-zA-Z][a-zA-Z0-9+.\-]*://[^\s\u3000\u4e00-\u9fff]+""")

    /**
     * 不带协议的域名（识屏用）：地址栏、聊天里显示的「github.com/Hunoia/Luno」很常见。
     * 前后都排除单词字符/`@`/`.`/`/`，避免把 `user@host.com`、包名、路径片段认成链接。
     */
    private val bareDomainRegex = Regex(
        """(?i)(?<![\w@./-])((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+([a-z]{2,24})(?::\d{2,5})?(?:/[^\s\u3000\u4e00-\u9fff<>"']*)?)""",
    )

    /** 认得出的顶级域（不带协议时用来挡 `.png`/`.apk`/包名之类的假链接）。 */
    private val KnownTlds = setOf(
        "com", "cn", "net", "org", "io", "tv", "me", "cc", "co", "app", "dev", "xyz",
        "info", "top", "site", "vip", "fun", "link", "gov", "edu", "shop", "live", "club",
        "jp", "hk", "tw", "uk", "de", "us", "ai", "so", "ly", "gg", "to", "fm", "in", "ru",
        "fr", "kr", "au", "ca", "nl", "se", "no", "fi", "pl", "it", "es", "br", "mx",
    )

    /** 句末/包裹标点：链接常被写在句子里，尾部这些不属于 URL。 */
    private const val TrailingPunctuation = ".,;:!?)]}>\"'、。，；：！？）】》」』…"

    /** 不允许分发的协议：能执行脚本或读本地资源，不该进「打开链接」。 */
    private val BlockedSchemes = setOf("javascript", "data", "file", "content")

    private val TemplateVarRegex = Regex("""\{(url|host|path|query|scheme|url_enc|url_dec)\}""")

    /**
     * 从混杂文本里按出现顺序提取链接（去重，最多 [MaxUrls] 条）。
     * [allowBareDomains] = true 时额外收「不带协议」的裸域名（补 `https://`），
     * 只给识屏用：剪贴板仍要求完整协议，避免把普通文本里的域名误判成链接。
     */
    fun extractUrls(text: String, allowBareDomains: Boolean = false): List<String> {
        if (text.isBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        for (hit in urlRegex.findAll(text)) {
            val url = trimTrailing(hit.value)
            if (!isDispatchable(url)) continue
            out += url
            if (out.size >= MaxUrls) break
        }
        if (allowBareDomains && out.size < MaxUrls) {
            for (hit in bareDomainRegex.findAll(text)) {
                val raw = trimTrailing(hit.groupValues[1])
                if (!looksLikeBareDomain(raw, hit.groupValues[2])) continue
                val url = "https://$raw"
                if (!isDispatchable(url)) continue
                out += url
                if (out.size >= MaxUrls) break
            }
        }
        return out.toList()
    }

    /** 裸域名的额外闸门：顶级域白名单 + 不像包名。 */
    private fun looksLikeBareDomain(raw: String, tld: String): Boolean {
        if (tld.lowercase() !in KnownTlds) return false
        return !looksLikePackageName(raw)
    }

    /**
     * `com.example.app` / `com.foo.dev` 这类串和裸域名长得一模一样：
     * 首段是常见包名前缀、段数 ≥ 3、末段又刚好是顶级域。真实域名里这种组合极少，
     * 直接当包名跳过（代价是 `io.github.com` 这类仍会通过——首段不是 com/org/net）。
     */
    private fun looksLikePackageName(raw: String): Boolean {
        val host = raw.substringBefore('/').substringBefore(':').lowercase()
        val segments = host.split('.').filter { it.isNotEmpty() }
        if (segments.size < 3) return false
        return segments.first() in PackagePrefixes
    }

    private val PackagePrefixes = setOf("com", "org", "net", "androidx", "android", "kotlin", "java")

    /** 文本里第一条可分发链接（胶囊/外部打开的单链接快路径）。 */
    fun firstUrl(text: String): String? = extractUrls(text).firstOrNull()

    fun isDispatchable(url: String): Boolean {
        val scheme = schemeOf(url)?.lowercase() ?: return false
        if (scheme in BlockedSchemes) return false
        // http(s) 必须有 host，否则 `https://` 这种半截串也会被认成链接。
        if (scheme == "http" || scheme == "https") return !hostOf(url).isNullOrBlank()
        return true
    }

    fun schemeOf(url: String): String? {
        val index = url.indexOf("://")
        if (index <= 0) return null
        val scheme = url.substring(0, index)
        if (!scheme[0].isLetter()) return null
        return scheme
    }

    /** 手动解析 host：JVM 单测里 `android.net.Uri` 不可用，这里也不值得为它引入依赖。 */
    fun hostOf(url: String): String? {
        val start = url.indexOf("://").takeIf { it > 0 }?.plus(3) ?: return null
        var end = url.length
        for (i in start until url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') {
                end = i
                break
            }
        }
        var authority = url.substring(start, end)
        // userinfo：取最后一个 '@' 之后（路径里可能还有 '@'，authority 里取最后一个是对的）。
        val at = authority.lastIndexOf('@')
        if (at >= 0) authority = authority.substring(at + 1)
        if (authority.startsWith("[")) {
            // IPv6 字面量：[::1]:8080
            val close = authority.indexOf(']')
            if (close < 0) return null
            return authority.substring(0, close + 1)
        }
        val colon = authority.indexOf(':')
        if (colon >= 0) authority = authority.substring(0, colon)
        return authority.ifBlank { null }
    }

    fun matches(url: String, rule: LinkDispatchRule): Boolean {
        val pattern = rule.pattern.trim()
        if (pattern.isEmpty()) return false
        if (rule.effectiveExcludes.any { url.contains(it, ignoreCase = true) }) return false
        return when (rule.matchType) {
            LinkMatchType.CONTAINS -> {
                val host = hostOf(url)
                if (host != null && hostMatches(host, pattern)) {
                    true
                } else {
                    url.contains(pattern, ignoreCase = true)
                }
            }
            LinkMatchType.DOMAIN -> {
                val host = hostOf(url) ?: return false
                hostMatches(host, pattern)
            }
            LinkMatchType.REGEX -> runCatching {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(url)
            }.getOrDefault(false)
            LinkMatchType.EXACT -> url.equals(pattern, ignoreCase = true)
        }
    }

    /** `host` 等于 `domain` 或是它的子域；`domain` 允许带前导点/通配前缀。 */
    fun hostMatches(host: String, domain: String): Boolean {
        val normalized = domain.trim().trimStart('.').removePrefix("*.").lowercase()
        if (normalized.isEmpty()) return false
        val hostLower = host.lowercase()
        return hostLower == normalized || hostLower.endsWith(".$normalized")
    }

    /**
     * 内网/本机地址：短链解析是后台联网请求，别让剪贴板里的 `192.168.x` / `localhost`
     * 变成对局域网服务的探测。纯函数，供 [LinkShortLinkResolver] 与单测共用。
     */
    fun isPrivateHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val bare = host.trim().removePrefix("[").removeSuffix("]")
        // 多个冒号 = IPv6 字面量（内网段太多，一律不联网）；单个冒号是 host:port。
        if (bare.count { it == ':' } > 1) return true
        val raw = bare.substringBefore(':')
        val lower = raw.lowercase()
        if (lower == "localhost" || lower.endsWith(".localhost") || lower.endsWith(".local")) {
            return true
        }
        val parts = lower.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return when {
            a == 10 || a == 127 || a == 0 -> true
            a == 192 && b == 168 -> true
            a == 172 && b in 16..31 -> true
            a == 169 && b == 254 -> true
            a == 100 && b in 64..127 -> true
            else -> false
        }
    }

    /** 规则表顺序取第一条命中的启用规则。 */
    fun firstMatch(url: String, rules: List<LinkDispatchRule>): LinkDispatchRule? =
        rules.firstOrNull { it.enabled && it.pattern.isNotBlank() && matches(url, it) }

    /**
     * 按模板改写 URL：`{url}` `{host}` `{path}` `{query}` `{scheme}` 以及
     * `{url_enc}` / `{url_dec}`；未识别的占位符原样保留。空模板返回原串。
     */
    fun applyTemplate(url: String, template: String): String {
        if (template.isBlank()) return url
        val values = mapOf(
            "url" to url,
            "host" to (hostOf(url) ?: ""),
            "path" to (pathOf(url) ?: ""),
            "query" to (queryOf(url) ?: ""),
            "scheme" to (schemeOf(url) ?: ""),
            "url_enc" to encode(url),
            "url_dec" to decode(url),
        )
        return TemplateVarRegex.replace(template) { match ->
            values[match.groupValues[1]] ?: match.value
        }
    }

    /** path：host 之后、`?`/`#` 之前（含前导 `/`，空则为空串）。 */
    fun pathOf(url: String): String? {
        val start = url.indexOf("://").takeIf { it > 0 }?.plus(3) ?: return null
        val slash = url.indexOf('/', start)
        if (slash < 0) return ""
        var end = url.length
        for (i in slash until url.length) {
            val c = url[i]
            if (c == '?' || c == '#') {
                end = i
                break
            }
        }
        return url.substring(slash, end)
    }

    /** query：`?` 之后、`#` 之前；没有 `?` 返回 null。 */
    fun queryOf(url: String): String? {
        val mark = url.indexOf('?').takeIf { it >= 0 } ?: return null
        val hash = url.indexOf('#', mark)
        return if (hash < 0) url.substring(mark + 1) else url.substring(mark + 1, hash)
    }

    private fun encode(value: String): String =
        runCatching { URLEncoder.encode(value, "UTF-8") }.getOrDefault(value)

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun trimTrailing(raw: String): String {
        var end = raw.length
        while (end > 0 && raw[end - 1] in TrailingPunctuation) end--
        return raw.substring(0, end)
    }
}
