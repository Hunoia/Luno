package hunoia.luno.runtime.scene

/**
 * 界面上的「地址栏域名」与真实链接的 host 对账（纯逻辑，方便 JVM 单测）。
 *
 * 特权通道拿到的 URL 是「日志里最后一条」，它可能是上一屏的。要把它绑到当前屏，靠的是界面
 * 自己写着的那一行：微信 / QQ 的内置浏览器顶部只显示域名（`www.xiaohongshu.com`），
 * 浏览器类应用显示 `example.com/path`。两者能对上的 host 才认为这条链接属于这块界面。
 *
 * 这里不判断「哪一片文本是地址栏」，只回答「这段文本能不能当 host」和「两个 host 是不是
 * 同一个站」。定位地址栏是调用方的事（见 `ScreenSceneScanner`）。
 */
internal object SceneUrlMatch {

    /** 一个 leaf 里能当 host 的部分：允许带 scheme / 路径 / 端口，不允许空白与汉字。 */
    private val hostLike = Regex(
        """^(?:[a-z][a-z0-9+.\-]*://)?((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24})(?::\d{2,5})?(?:[/?#]\S*)?$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 从一段界面文本里取出 host；不像 host 的返回 null。
     *
     * 判据刻意收窄：整段必须就是一个「主机名 + 可选路径」，而不是包含关系——正文里提到
     * `github.com/xxx` 的整句话不该被当成地址栏。
     */
    fun hostOfLeaf(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 200) return null
        // 汉字直接否决：地址栏不会有中文，带中文的几乎都是正文。
        if (trimmed.any { it.code > 0x2E80 }) return null
        val raw = hostLike.find(trimmed)?.groupValues?.get(1) ?: return null
        val host = raw.lowercase()
        // 纯数字 / 单段（localhost 除外）不算。
        if (!host.contains('.')) return null
        return host
    }

    /**
     * 两个 host 是否同一个站：相等，或一方是另一方的子域。
     *
     * 只做「按点切分的后缀」比较，避免 `bilibili.com.evil.com` 这类前缀伪装命中。
     */
    fun sameSite(a: String?, b: String?): Boolean {
        val left = a?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        val right = b?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        if (left == right) return true
        return left.endsWith(".$right") || right.endsWith(".$left")
    }
}
