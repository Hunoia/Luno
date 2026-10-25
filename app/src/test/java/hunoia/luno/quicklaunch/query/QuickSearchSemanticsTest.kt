package hunoia.luno.quicklaunch.query

import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.quicklaunch.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快速启动器搜索与排序的语义回归（原 scripts/QuickSearchCheck.java 的 JVM 单测迁移版）。
 * 直接调用生产代码（AppSearch / QuickSearchIndex），不再维护 Java 转写副本：
 * 改 Kotlin 侧逻辑时由编译器暴露签名变化，语义漂移由本测试暴露。
 *
 * 手建 AppSearchIndex（不依赖 TinyPinyin 的变体生成）；微基准不迁移到单测，
 * 仍在 scripts 里保留人工观察（见 docs/architecture.md 若有引用）。
 */
class QuickSearchSemanticsTest {

    private fun tokens(vararg ts: String) = ts.toList()

    /** 与 QuickSearchIndexSearch.tokenize 同规则：单字 token（只留字母数字）。 */
    private fun charTokens(s: String): List<String> =
        s.lowercase().filter { it.isLetterOrDigit() }.map { it.toString() }

    private fun foldTokens(q: String): List<String> = charTokens(AppSearch.foldPinyin(q))

    private fun index(
        raw: String,
        pinyin: String = raw,
        initials: String = raw,
        altPinyins: List<String> = emptyList(),
        altPinyinsFolded: List<String> = emptyList(),
    ) = AppSearch.AppSearchIndex(
        raw = raw,
        pinyin = pinyin,
        initials = initials,
        pinyinFolded = AppSearch.foldPinyin(pinyin),
        altPinyins = altPinyins,
        altPinyinsFolded = altPinyinsFolded,
    )

    private fun tier(initials: String, pinyin: String, raw: String, ts: List<String>): Int =
        AppSearch.matchTier(index(raw, pinyin, initials), ts, AppSearch.compileTokenMasks(ts)).tier

    private fun tierFull(initials: String, pinyin: String, raw: String, ts: List<String>): TieredMatch =
        AppSearch.matchTier(index(raw, pinyin, initials), ts, AppSearch.compileTokenMasks(ts))

    private fun fuzzy(pinyinFolded: String, foldedTs: List<String>): Int =
        AppSearch.matchTierFuzzy(index(raw = pinyinFolded, pinyin = pinyinFolded), foldedTs).tier

    // ======== V1 可达性 + 分档：按首字母逐字按键，initials 锚定命中 → tier 2 ========

    @Test
    fun `V1 initials anchor hits tier 2`() {
        // 微信 weixin/wx：qw 锚 wei（w∈qw），zx 锚 xin（x∈zx）
        assertEquals(2, tier("wx", "weixin", "微信", tokens("qw", "zx")))
        assertEquals(2, tier("zfb", "zhifubao", "支付宝", tokens("zx", "df", "bn")))
        // 连续块语义：跳字缩写死——gddt 里 d,t 不相邻，无连续块 → -1
        assertEquals(-1, tier("gddt", "gaodeditu", "高德地图", tokens("gh", "df", "ty")))
        assertEquals(2, tier("jd", "jingdong", "京东", tokens("jk", "df")))
        assertEquals(2, tier("jy", "jianying", "剪映", tokens("jk", "ty")))
        // 连续块语义：3 键对上后三位 "qyl"（q∈qw,y∈ty,l），中段命中 tier 0
        assertEquals(0, tier("qqyl", "qqyinle", "qq音乐", tokens("qw", "ty", "l")))
    }

    // ======== V3 可达性 + 分档：从末字起打，锚定失败 → 降档 tier 0 ========

    @Test
    fun `V3 end-of-word hits degrade to tier 0`() {
        assertEquals(0, tier("wx", "weixin", "微信", tokens("zx")))
        assertEquals(0, tier("jd", "jingdong", "京东", tokens("df")))
        assertEquals(0, tier("tb", "taobao", "淘宝", tokens("bn")))
        assertEquals(0, tier("gddt", "gaodeditu", "高德地图", tokens("ty")))
        assertEquals(0, tier("jy", "jianying", "剪映", tokens("ty")))
        assertEquals(0, tier("zh", "zhihu", "知乎", tokens("gh")))
    }

    @Test
    fun `initials anchor ranks above degraded`() {
        val anchor = tierFull("wx", "weixin", "微信", tokens("qw", "zx"))
        val degraded = tierFull("wx", "weixin", "微信", tokens("zx"))
        assertTrue(anchor.tier > degraded.tier)
    }

    // ======== 非 ASCII token 回落（掩码 = -1）========

    @Test
    fun `non-ascii token falls back and hits at tier 0`() {
        val m = AppSearch.matchTier(index("mima", "mima", "密码"), tokens("密"), AppSearch.compileTokenMasks(tokens("密")))
        assertEquals(0, m.tier)
    }

    @Test
    fun `ascii token does not hit fullwidth text`() {
        // 全角 'ａ'(U+FF41) 不在 a-z 范围
        assertEquals(-1, tier("ａ", "ａ", "ａ", tokens("qw")))
    }

    // ======== emptyMatches 两处相反语义 ========

    @Test
    fun `empty tokens semantics differ by emptyMatches flag`() {
        assertTrue(matchesTokens("abc", emptyList(), true))
        assertTrue(!matchesTokens("abc", emptyList(), false))
    }

    // ======== 空查询: 全部保留, 档位恒为 0 ========

    @Test
    fun `empty query keeps all apps`() {
        val now = System.currentTimeMillis()
        val labels = listOf("微信", "支付宝", "高德地图", "京东", "剪映", "QQ音乐", "知乎", "淘宝")
        val apps = labels.map { AppInfo(it, "", it) }
        val settings = QuickAppLauncherSettings(
            launchCount = labels.associate { "$it/" to 1L },
            recentLaunchTime = labels.associate { "$it/" to now },
        )
        val result = AppSearch.sortApps(apps, settings, emptyList())
        assertEquals(8, result.size)
    }

    // ======== 排序键全序: 档位 → offset → 使用分 → 长度 → 字典序 ========

    private fun sortNow(labels: List<Triple<String, Long, Long>>, ts: List<String>): List<String> {
        val now = System.currentTimeMillis()
        val apps = labels.map { AppInfo(it.first, "", it.first) }
        val settings = QuickAppLauncherSettings(
            launchCount = labels.associate { it.first + "/" to it.second },
            recentLaunchTime = labels.associate { it.first + "/" to (now - it.third) },
        )
        return AppSearch.sortApps(apps, settings, ts).map { it.label }
    }

    @Test
    fun `sort by decayed usage then lexicographic`() {
        // A=ln(11)≈2.398, C=ln(11)*0.5^(1/3)≈1.903, B=ln(6)≈1.792
        val r = sortNow(
            listOf(Triple("AAA", 10L, 0L), Triple("BB", 5L, 0L), Triple("CC", 10L, 86400000L)),
            emptyList(),
        )
        assertEquals("AAA,CC,BB", r.joinToString(","))
        // 同 score 同长度 → 字典序
        val r2 = sortNow(listOf(Triple("DD2", 5L, 0L), Triple("DD1", 5L, 0L)), emptyList())
        assertEquals("DD1,DD2", r2.joinToString(","))
    }

    @Test
    fun `contiguous block beats sparse by tier not lexicographic`() {
        // 抖音 initials "dy" 相邻 tier 2；ascii 串 "dkactivityzdydz" 中段 @10 有相邻 "dy" tier 0
        val r = sortNow(
            listOf(Triple("dkactivityzdydz", 0L, 0L), Triple("抖音", 0L, 0L)),
            tokens("d", "y"),
        )
        assertEquals(listOf("抖音", "dkactivityzdydz"), r)
        // 红烧鱼 hsy 里 h,y 不相邻 → 出局，只剩 xx会议（xxhy 中段相邻）
        val r2 = sortNow(
            listOf(Triple("红烧鱼", 0L, 0L), Triple("xx会议", 0L, 0L)),
            tokens("h", "y"),
        )
        assertEquals(listOf("xx会议"), r2)
    }

    // ======== 模糊音折叠 + 降档 ========

    @Test
    fun `foldPinyin normalizes to canonical form`() {
        assertEquals("yinyue", AppSearch.foldPinyin("yingyue"))
        assertEquals("zihu", AppSearch.foldPinyin("zhihu"))
        assertEquals("san", AppSearch.foldPinyin("shang"))
        assertEquals("san", AppSearch.foldPinyin("sang"))
        assertEquals("cen", AppSearch.foldPinyin("chen"))
        assertEquals("weixin", AppSearch.foldPinyin("weixin"))
        // 单次扫描与旧的连续 replace 逐段等价：跨段衔接也要一致。
        assertEquals("zan", AppSearch.foldPinyin("zhang"))
        assertEquals("con", AppSearch.foldPinyin("chong"))
        assertEquals("sen", AppSearch.foldPinyin("sheng"))
        assertEquals("zin", AppSearch.foldPinyin("zhing"))
        assertEquals("anon", AppSearch.foldPinyin("angong"))
        assertEquals("onin", AppSearch.foldPinyin("onging"))
    }

    @Test
    fun `fuzzy yingyue hits yinyue at tier 0 only`() {
        // 打全了：yingyue 对 yinyue，精确 -1，模糊 0
        assertEquals(-1, tier("yy", "yinyue", "音乐", tokens("y", "i", "n", "g", "y", "u", "e")))
        assertEquals(0, fuzzy("yinyue", foldTokens("yingyue")))
        // 打对了还是高档：yinyue 精确 tier 1（initials 太短锚不上，pinyin 锚上）
        assertEquals(1, tier("yy", "yinyue", "音乐", tokens("y", "i", "n", "y", "u", "e")))
        // 平舌打成翘舌：zhi 对 zi（资），精确 -1，模糊 0
        assertEquals(0, fuzzy("zi", foldTokens("zhi")))
        // 不相关的：wang 对 yinyue，模糊也是 -1
        assertEquals(-1, fuzzy("yinyue", foldTokens("wang")))
    }

    // ======== 反向模糊音对称（打短中长 = 打长中短，折叠归一后同一计算）========

    @Test
    fun `fuzzy fold is symmetric`() {
        assertEquals(0, fuzzy("zi", foldTokens("zi")))
        // 连续块语义：精确只认相邻，"zhi" 里无 "zi" 相邻 → -1（打 zi 找知走模糊）
        assertEquals(-1, tier("z", "zhi", "知", tokens("z", "i")))
        assertEquals(0, fuzzy("sen", foldTokens("sen")))
        assertEquals(0, fuzzy("min", foldTokens("min")))
        assertEquals(0, fuzzy("zao", foldTokens("zao")))
        assertEquals(0, fuzzy("ci", foldTokens("ci")))
        // 反例仍不中：sin vs xin（s/x 本来就不同声母，折叠不归一）
        assertEquals(-1, fuzzy("xin", foldTokens("sin")))
    }

    // ======== 多音字变体同档 ========

    @Test
    fun `polyphone variant hits via altPinyins`() {
        // QQ音乐：基准 qqyinle + 变体 qqyinyue；打 yinyue 命中变体中段块 → tier 0
        val m = AppSearch.matchTier(
            index("qqyinle", "qqyinle", "qqyl", altPinyins = listOf("qqyinyue"), altPinyinsFolded = listOf("qqyinyue")),
            tokens("y", "i", "n", "y", "u", "e"),
            AppSearch.compileTokenMasks(tokens("y", "i", "n", "y", "u", "e")),
        )
        assertEquals(0, m.tier)
        // 变体折叠：打 yingyue 中变体（折叠后同归 yinyue）
        assertEquals(0, AppSearch.matchTierFuzzy(
            index(raw = "qqyinle", pinyin = "qqyinle", altPinyinsFolded = listOf("qqyinyue")),
            foldTokens("yingyue"),
        ).tier)
    }

    // ======== 连续块：相邻对位赢，稀疏子序列出局 ========

    @Test
    fun `contiguous block span and offset semantics`() {
        // 抖音 initials "dy"：[d,y] 相邻，span 2 offset 0
        val dy = tierFull("dy", "douyin", "抖音", tokens("d", "y"))
        assertEquals(2, dy.tier)
        assertEquals(2, dy.span)
        assertEquals(0, dy.offset)
        // fallback 串 "dkactivityzdydz" @11 处真有相邻 "dy"，合法中段命中 tier 0 offset 11
        val mid = tierFull("dkactivityzdydz", "neicun", "内存", tokens("d", "y"))
        assertEquals(0, mid.tier)
        assertEquals(11, mid.offset)
        // xx会议 initials "xxhy"：[h,y] 中段相邻 span 2 offset 2，tier 0
        val hy = tierFull("xxhy", "xxhuiyi", "xx会议", tokens("h", "y"))
        assertEquals(0, hy.tier)
        assertEquals(2, hy.span)
        assertEquals(2, hy.offset)
        // 红烧鱼 hsy 里 h,y 不相邻 → -1（跳字缩写死）
        assertEquals(-1, tierFull("hsy", "hongshaoyu", "红烧鱼", tokens("h", "y")).tier)
    }

    // ======== 剪贴记录匹配（QuickSearchIndexSearch.matchClips）========

    private fun matchClips(pinned: List<String>, clips: List<String>, q: String): List<String> =
        matchClipPool(pinned, clips, charTokens(q), foldTokens(q))
            .map { (it.result as QuickSearchResult.ClipEntry).text }

    @Test
    fun `clip matches query hit`() {
        // he vs hello world → tier 1（0 位块），命中
        val he = matchClips(emptyList(), listOf("hello world"), "he")
        assertEquals(listOf("hello world"), he)
    }

    @Test
    fun `clip matches only within first 64 chars`() {
        // head64 截断：y 只出现在第 64+ 位 → 不命中；在 0 位 → 命中
        val xs64 = "x".repeat(64)
        assertTrue(matchClips(emptyList(), listOf(xs64 + "yyyyyy"), "y").isEmpty())
        assertEquals(1, matchClips(emptyList(), listOf("yyyyyy" + xs64), "y").size)
    }

    @Test
    fun `clip matches cap at 10 with pinned first`() {
        // 上限 10：15 条全命中只回 10
        val fifteen = (0 until 15).map { "hello$it" }
        assertEquals(10, matchClips(emptyList(), fifteen, "he").size)
        // 固定源内置顶：同 tier/offset 下固定排前
        val pinFirst = matchClips(listOf("hello pinned"), listOf("hello world", "hello there"), "he")
        assertEquals("hello pinned", pinFirst.first())
    }
}
