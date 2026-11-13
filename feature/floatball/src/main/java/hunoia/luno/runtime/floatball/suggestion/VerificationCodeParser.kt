package hunoia.luno.runtime.floatball.suggestion

/**
 * 严格验证码识别。
 *
 * 只认「关键词 + 4–8 位纯数字」：对每个数字取最近的标记（关键词或排除词），
 * 最近标记必须是关键词；且最终只剩唯一候选值时才返回。
 * 这样「订单号 123456」「尾号 8888」「验证码 1234，订单 5678」都不会误判。
 */
internal object VerificationCodeParser {

    private val keywordRegex = Regex(
        "(?i)(" +
            "验证码|校验码|动态码|确认码|安全码|授权码|登录码|一次性密码|" +
            "verification\\s+code|verify\\s+code|security\\s+code|" +
            "one[\\s-]?time\\s+(?:password|code)|\\botp\\b|passcode" +
            ")",
    )

    private val exclusionRegex = Regex(
        "(" +
            "订单号?|单号|快递|运单|物流|手机号码?|电话号码?|尾号|卡号|银行卡|" +
            "账号|帐号|日期|时间|积分|余额|优惠|立减|满|减|年|月|日|" +
            "金额|价格|运费|券|编号|序号|工号|房间号|门牌" +
            ")",
    )

    /** 纯数字 4–8 位，前后都不能再接数字（10 位手机号/长订单号不会被截一段）。 */
    private val numberRegex = Regex("(?<![0-9])([0-9]{4,8})(?![0-9])")

    /** 标记和数字之间允许的最大间隔：再远就不是同一条语义了。 */
    private const val MaxMarkerDistance = 24

    /**
     * 解析候选验证码；无法确定唯一候选时返回 null。
     * 传入多段文本（标题 / 正文 / bigText）会按换行拼接。
     */
    fun parse(vararg texts: String?): String? {
        val normalized = texts.filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString("\n") { normalizeDigits(it) }
        if (normalized.isEmpty()) return null

        val markers = findMarkers(normalized)
        if (markers.none { it.keyword }) return null

        val candidates = LinkedHashSet<String>()
        for (match in numberRegex.findAll(normalized)) {
            val start = match.range.first
            val end = match.range.last
            // 先看数字前面最近的标记：验证码通常是「关键词 + 数字」。
            // 前面若是排除词（订单号/尾号/手机号…），即使后面又出现「验证码」也直接判负。
            val previous = markers.filter { it.end < start }
                .minByOrNull { start - it.end }
            val followedBy = markers.filter { it.start > end }
                .minByOrNull { it.start - end }
            val keywordDistance = when {
                previous != null -> if (previous.keyword) start - previous.end else -1
                followedBy != null -> if (followedBy.keyword) followedBy.start - end else -1
                else -> -1
            }
            if (keywordDistance < 0) continue
            if (keywordDistance > MaxMarkerDistance) continue
            candidates += match.groupValues[1]
        }
        return candidates.singleOrNull()
    }

    private fun findMarkers(text: String): List<Marker> {
        val out = ArrayList<Marker>()
        keywordRegex.findAll(text).forEach {
            out += Marker(it.range.first, it.range.last, keyword = true)
        }
        exclusionRegex.findAll(text).forEach {
            out += Marker(it.range.first, it.range.last, keyword = false)
        }
        return out
    }

    /** 全角数字归一成半角，避免「验证码１２３４」漏识别。 */
    private fun normalizeDigits(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (ch in raw) {
            builder.append(
                when (ch) {
                    in '\uFF10'..'\uFF19' -> ('0' + (ch - '\uFF10'))
                    else -> ch
                },
            )
        }
        return builder.toString()
    }

    private data class Marker(
        val start: Int,
        val end: Int,
        val keyword: Boolean,
    )
}
