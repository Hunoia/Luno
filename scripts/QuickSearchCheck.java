// 快速启动器搜索与排序的回归检查。
//
// 它是 AppSearch.kt 中 compileTokenMasks / charMatches / matchWithMasks /
// matchTier / matchTierFuzzy / foldPinyin / sortApps 的 Java 等价转写，
// **改 Kotlin 侧必须同步改这里**。多音字变体来自 houbb 库，脚本喂预建串，不覆盖。
//
// 不依赖 TinyPinyin：直接喂预建的索引串（raw / pinyin / initials）。
//
// 不引入 JUnit、不进 gradle，跑法：
//   cd scripts && javac -encoding UTF-8 QuickSearchCheck.java \
//     && java -Dfile.encoding=UTF-8 QuickSearchCheck
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
import java.util.*;

public class QuickSearchCheck {

    // ======== 转写：compileTokenMasks ========

    static long[] compileTokenMasks(List<String> tokens) {
        long[] masks = new long[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            long mask = 0L;
            for (char c : tokens.get(i).toCharArray()) {
                long bit;
                if (c >= 'a' && c <= 'z') bit = 1L << (c - 'a');
                else if (c >= 'A' && c <= 'Z') bit = 1L << (c - 'A');
                else if (c >= '0' && c <= '9') bit = 1L << (c - '0' + 26);
                else { mask = -1L; break; }
                mask |= bit;
            }
            masks[i] = mask;
        }
        return masks;
    }

    // ======== 转写：charMatches ========

    static boolean charMatches(char ch, long mask) {
        long bit;
        if (ch >= 'a' && ch <= 'z') bit = 1L << (ch - 'a');
        else if (ch >= 'A' && ch <= 'Z') bit = 1L << (ch - 'A');
        else if (ch >= '0' && ch <= '9') bit = 1L << (ch - '0' + 26);
        else return false;
        return (mask & bit) != 0L;
    }

    // ======== 转写：matchWithMasks（连续块） ========
    // 返回 [tier, span, offset]：1=ANCHOR（0 位块）, 0=DEGRADED（中段块）, -1=NO_MATCH。
    // 只认连续块：token 依次占相邻字符，首个块赢；span 恒 = masks.length；稀疏子序列不是命中。

    static final int NO_MATCH = -1;
    static final int DEGRADED = 0;
    static final int ANCHOR = 1;

    static int[] matchWithMasks(String text, long[] masks) {
        if (masks.length == 0) return new int[]{DEGRADED, 0, 0};
        if (masks.length > text.length()) return new int[]{NO_MATCH, Integer.MAX_VALUE, Integer.MAX_VALUE};

        for (int start = 0; start <= text.length() - masks.length; start++) {
            boolean ok = true;
            for (int i = 0; i < masks.length; i++) {
                if (!charMatches(text.charAt(start + i), masks[i])) { ok = false; break; }
            }
            if (ok) return start == 0
                ? new int[]{ANCHOR, masks.length, 0}
                : new int[]{DEGRADED, masks.length, start};
        }
        return new int[]{NO_MATCH, Integer.MAX_VALUE, Integer.MAX_VALUE};
    }

    /** 代表选取：tier 大者胜，再比 offset 小者胜（连续块 span 恒定不参比，mirror Kotlin）。 */
    static int[] bestField(int[] a, int[] b) {
        if (a[0] != b[0]) return a[0] > b[0] ? a : b;
        return a[2] <= b[2] ? a : b;
    }

    // ======== 转写：matchTier ========
    // 返回: 2=initials anchor, 1=pinyin/raw/alt anchor, 0=degraded, -1=no match
    // Kotlin 侧 matchTier(index, tokens, masks) 会遍历 index.altPinyins（多音字变体，
    // 每次只换一个多音字，cap 8），与基准串同档。这里用重载 mirror：老调用走空变体。

    static int matchTier(String initials, String pinyin, String raw, List<String> tokens, long[] masks) {
        return matchTierFull(initials, pinyin, raw, Collections.<String>emptyList(), tokens, masks)[0];
    }

    static int matchTier(String initials, String pinyin, String raw, List<String> altPinyins,
                         List<String> tokens, long[] masks) {
        return matchTierFull(initials, pinyin, raw, altPinyins, tokens, masks)[0];
    }

    static int[] matchTierFull(String initials, String pinyin, String raw, List<String> altPinyins,
                               List<String> tokens, long[] masks) {
        if (tokens.isEmpty()) return new int[]{0, 0, 0};

        if (masks.length > 0) {
            boolean hasNonAscii = false;
            for (long m : masks) if (m == -1L) { hasNonAscii = true; break; }
            if (hasNonAscii) {
                return (matchesTokens(initials, tokens, true)
                    || matchesTokens(pinyin, tokens, true)
                    || matchesTokens(raw, tokens, true))
                    ? new int[]{0, tokens.size(), 0}
                    : new int[]{NO_MATCH, Integer.MAX_VALUE, Integer.MAX_VALUE};
            }
        }

        // 代表选取：各字段连续块按档位+起点取最优（mirror Kotlin matchTier）。
        // initials 0 位块独占 tier 2；其余字段 0 位块 tier 1；中段块 tier 0。
        // 返回既有数组（3 元 [tier,span,offset]，零新分配）。
        List<int[]> cands = new ArrayList<>();
        int[] ini = matchWithMasks(initials, masks);
        cands.add(ini[0] == ANCHOR ? new int[]{2, ini[1], ini[2]} : ini);
        cands.add(addAnchor(pinyin, masks));
        for (String alt : altPinyins) cands.add(addAnchor(alt, masks));
        cands.add(addAnchor(raw, masks));
        int[] best = cands.get(0);
        for (int i = 1; i < cands.size(); i++) best = bestField(best, cands.get(i));
        return best;
    }

    static int[] addAnchor(String text, long[] masks) {
        int[] r = matchWithMasks(text, masks);
        return r[0] == ANCHOR ? new int[]{1, r[1], r[2]} : r;
    }

    // ======== 转写：foldPinyin（模糊音折叠）========
    // 与 Kotlin 侧 AppSearch.foldPinyin 同顺序：先三字母后双字母。
    // 折叠是归一化（双边都折到同一短式），所以天然对称：打长中短（zhi→zi）
    // 和打短中长（zi→zhi）是同一计算，不需要反向展开函数，下面的对称 case 锁定这一点。

    static String foldPinyin(String s) {
        String t = s.toLowerCase();
        t = t.replace("ing", "in").replace("eng", "en").replace("ang", "an").replace("ong", "on");
        t = t.replace("zh", "z").replace("ch", "c").replace("sh", "s");
        return t;
    }

    static List<String> foldedTokens(String q) {
        String f = foldPinyin(q);
        List<String> out = new ArrayList<>();
        for (char c : f.toCharArray()) {
            if (Character.isLetterOrDigit(c)) out.add(String.valueOf(Character.toLowerCase(c)));
        }
        return out;
    }

    // ======== 转写：matchTierFuzzy（只比折叠串，命中恒 0）========
    // Kotlin 侧还会遍历 index.altPinyinsFolded（变体折叠串），这里重载 mirror。

    static int matchTierFuzzy(String pinyinFolded, List<String> foldedTokens) {
        return matchTierFuzzyFull(pinyinFolded, Collections.<String>emptyList(), foldedTokens)[0];
    }

    static int matchTierFuzzy(String pinyinFolded, List<String> altFolded, List<String> foldedTokens) {
        return matchTierFuzzyFull(pinyinFolded, altFolded, foldedTokens)[0];
    }

    static int[] matchTierFuzzyFull(String pinyinFolded, List<String> altFolded, List<String> foldedTokens) {
        int[] none = new int[]{NO_MATCH, Integer.MAX_VALUE, Integer.MAX_VALUE};
        if (foldedTokens.isEmpty()) return none;
        long[] masks = compileTokenMasks(foldedTokens);
        for (long m : masks) if (m == -1L) return none;
        int[] best = matchWithMasks(pinyinFolded, masks);
        for (String alt : altFolded) best = bestField(best, matchWithMasks(alt, masks));
        // 命中恒 tier 0（归一化，mirror Kotlin）；只走精确失败后的冷路径，不在 bench 里。
        return best[0] >= DEGRADED ? new int[]{0, best[1], best[2]} : none;
    }

    // ======== 转写：matchesTokens（旧窗口匹配，用于非 ASCII 回落和 pageMatchesTokens）========

    static boolean matchesTokens(String text, List<String> tokens, boolean emptyMatches) {
        if (tokens.isEmpty()) return emptyMatches;
        if (tokens.size() > text.length()) return false;
        for (int start = 0; start <= text.length() - tokens.size(); start++) {
            boolean ok = true;
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                char ch = text.charAt(start + i);
                if (token.length() == 1 && Character.isDigit(token.charAt(0))) {
                    if (token.charAt(0) != ch) { ok = false; break; }
                } else if (!containsIgnoreCase(token, ch)) { ok = false; break; }
            }
            if (ok) return true;
        }
        return false;
    }

    static boolean containsIgnoreCase(String token, char ch) {
        for (char t : token.toCharArray())
            if (Character.toLowerCase(t) == Character.toLowerCase(ch)) return true;
        return false;
    }

    // ======== 转写：sortApps ========

    static class TestApp {
        final String label, raw, pinyin, initials;
        final long launchCount, recentLaunchTime;
        final List<String> altPinyins, altPinyinsFolded;
        TestApp(String label, String raw, String pinyin, String initials, long launchCount, long recentLaunchTime) {
            this(label, raw, pinyin, initials, launchCount, recentLaunchTime,
                Collections.<String>emptyList(), Collections.<String>emptyList());
        }
        TestApp(String label, String raw, String pinyin, String initials, long launchCount, long recentLaunchTime,
                List<String> altPinyins, List<String> altPinyinsFolded) {
            this.label = label; this.raw = raw; this.pinyin = pinyin; this.initials = initials;
            this.launchCount = launchCount; this.recentLaunchTime = recentLaunchTime;
            this.altPinyins = altPinyins; this.altPinyinsFolded = altPinyinsFolded;
        }
    }

    static class SearchEntry {
        final TestApp app;
        final int matchTier;
        final int offset;
        final double usageScore;
        final String labelLower;
        final int labelLen;
        SearchEntry(TestApp app, int matchTier, int offset, double usageScore) {
            this.app = app; this.matchTier = matchTier; this.offset = offset;
            this.usageScore = usageScore;
            this.labelLower = app.raw; this.labelLen = app.label.length();
        }
    }

    static List<TestApp> sortApps(List<TestApp> apps, long now, List<String> tokens) {
        long[] masks = tokens.isEmpty() ? new long[0] : compileTokenMasks(tokens);
        List<SearchEntry> entries = new ArrayList<>(apps.size());
        for (TestApp app : apps) {
            int[] m = matchTierFull(app.initials, app.pinyin, app.raw, app.altPinyins, tokens, masks);
            if (m[0] < 0) continue;
            double usageScore = 0;
            if (app.launchCount > 0 && app.recentLaunchTime > 0) {
                double ageDays = (now - app.recentLaunchTime) / 86400000.0;
                // 与 Kotlin AppSearch.decayedUsage 同公式：ln(1+count) * 0.5^(age天/3)。
                usageScore = Math.log(1.0 + app.launchCount) * Math.pow(0.5, ageDays / 3.0);
            }
            entries.add(new SearchEntry(app, m[0], m[2], usageScore));
        }
        entries.sort((a, b) -> {
            // 连续块语义：tier → offset → 使用分：mirror Kotlin sortApps。
            int c = Integer.compare(b.matchTier, a.matchTier);
            if (c != 0) return c;
            c = Integer.compare(a.offset, b.offset);
            if (c != 0) return c;
            c = Double.compare(b.usageScore, a.usageScore);
            if (c != 0) return c;
            c = Integer.compare(a.labelLen, b.labelLen);
            if (c != 0) return c;
            return a.labelLower.compareTo(b.labelLower);
        });
        List<TestApp> result = new ArrayList<>(entries.size());
        for (SearchEntry e : entries) result.add(e.app);
        return result;
    }

    // ======== 测试基础设施 ========

    static int failures = 0;

    static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.println((ok ? "  ok  " : "FAIL  ") + label);
    }

    static void check(String label, boolean expected, boolean actual) {
        boolean ok = expected == actual;
        if (!ok) failures++;
        System.out.println((ok ? "  ok  " : "FAIL  ") + label
                + (ok ? "" : "\n        expected=" + expected + "\n        actual  =" + actual));
    }

    static void check(String label, int expected, int actual) {
        boolean ok = expected == actual;
        if (!ok) failures++;
        System.out.println((ok ? "  ok  " : "FAIL  ") + label
                + (ok ? " -> " + actual
                      : "\n        expected=" + expected + "\n        actual  =" + actual));
    }

    static void check(String label, String expected, String actual) {
        boolean ok = expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "  ok  " : "FAIL  ") + label
                + (ok ? " -> " + actual
                      : "\n        expected=[" + expected + "]\n        actual  =[" + actual + "]"));
    }

    static List<String> tokens(String... ts) { return Arrays.asList(ts); }

    // ======== 测试数据 ========

    // 真实 label 的索引（手建，不调 TinyPinyin）
    static final TestApp WEIXIN   = new TestApp("微信",   "微信",       "weixin",     "wx",   10, 0);
    static final TestApp ZHIFUBAO = new TestApp("支付宝", "支付宝",     "zhifubao",   "zfb",  20, 0);
    static final TestApp GAODE    = new TestApp("高德地图","高德地图",   "gaodeditu",  "gddt",  5, 0);
    static final TestApp JINGDONG = new TestApp("京东",   "京东",       "jingdong",   "jd",    8, 0);
    static final TestApp JIANYING = new TestApp("剪映",   "剪映",       "jianying",   "jy",    3, 0);
    static final TestApp QQYINYUE = new TestApp("QQ音乐", "qq音乐",     "qqyinle",    "qqyl",  6, 0);
    static final TestApp ZHIHU    = new TestApp("知乎",   "知乎",       "zhihu",      "zh",    2, 0);
    static final TestApp TAOBAO   = new TestApp("淘宝",   "淘宝",       "taobao",     "tb",    4, 0);

    public static void main(String[] args) {
        // ======== V1 可达性 + 分档 ========
        // 按首字母逐字按键，initials 锚定命中 → tier 2

        check("V1 微信 qw zx -> tier 2",
                2, matchTier(WEIXIN.initials, WEIXIN.pinyin, WEIXIN.raw, tokens("qw", "zx"), compileTokenMasks(tokens("qw", "zx"))));
        check("V1 支付宝 zx df bn -> tier 2",
                2, matchTier(ZHIFUBAO.initials, ZHIFUBAO.pinyin, ZHIFUBAO.raw, tokens("zx", "df", "bn"), compileTokenMasks(tokens("zx", "df", "bn"))));
        // 连续块语义：跳字缩写死——gddt 里 d,t 不相邻，无连续块 → -1
        check("连续块 V1 高德地图 gh df ty -> -1",
                -1, matchTier(GAODE.initials, GAODE.pinyin, GAODE.raw, tokens("gh", "df", "ty"), compileTokenMasks(tokens("gh", "df", "ty"))));
        check("V1 京东 jk df -> tier 2",
                2, matchTier(JINGDONG.initials, JINGDONG.pinyin, JINGDONG.raw, tokens("jk", "df"), compileTokenMasks(tokens("jk", "df"))));
        check("V1 剪映 jk ty -> tier 2",
                2, matchTier(JIANYING.initials, JIANYING.pinyin, JIANYING.raw, tokens("jk", "ty"), compileTokenMasks(tokens("jk", "ty"))));
        // 连续块语义：3 键对上后三位 "qyl"（q∈qw,y∈ty,l），中段命中 tier 0 offset 1；
        // 不再是 tier 2，想置顶打 4 键 qqyl。
        check("连续块 V1 QQ音乐 qw ty l -> tier 0",
                0, matchTier(QQYINYUE.initials, QQYINYUE.pinyin, QQYINYUE.raw, tokens("qw", "ty", "l"), compileTokenMasks(tokens("qw", "ty", "l"))));

        // ======== V3 可达性 + 分档 ========
        // 从末字起打，锚定失败 → 降级 → tier 0，但必须命中

        check("V3 微信 zx -> tier 0",
                0, matchTier(WEIXIN.initials, WEIXIN.pinyin, WEIXIN.raw, tokens("zx"), compileTokenMasks(tokens("zx"))));
        check("V3 京东 df -> tier 0",
                0, matchTier(JINGDONG.initials, JINGDONG.pinyin, JINGDONG.raw, tokens("df"), compileTokenMasks(tokens("df"))));
        check("V3 淘宝 bn -> tier 0",
                0, matchTier(TAOBAO.initials, TAOBAO.pinyin, TAOBAO.raw, tokens("bn"), compileTokenMasks(tokens("bn"))));
        check("V3 高德地图 ty -> tier 0",
                0, matchTier(GAODE.initials, GAODE.pinyin, GAODE.raw, tokens("ty"), compileTokenMasks(tokens("ty"))));
        check("V3 剪映 ty -> tier 0",
                0, matchTier(JIANYING.initials, JIANYING.pinyin, JIANYING.raw, tokens("ty"), compileTokenMasks(tokens("ty"))));
        check("V3 知乎 gh -> tier 0",
                0, matchTier(ZHIHU.initials, ZHIHU.pinyin, ZHIHU.raw, tokens("gh"), compileTokenMasks(tokens("gh"))));

        // ======== 排序档位 2 → 1 → 0 顺序 ========

        check("排序: initials anchor (2) 排在 degraded (0) 前",
                true, matchTier(WEIXIN.initials, WEIXIN.pinyin, WEIXIN.raw, tokens("qw", "zx"), compileTokenMasks(tokens("qw", "zx")))
                    > matchTier(WEIXIN.initials, WEIXIN.pinyin, WEIXIN.raw, tokens("zx"), compileTokenMasks(tokens("zx"))));

        // 连续块语义：qianwen 里无 [qw,er] 相邻（首字锚定+稀疏捡字已死），只剩中段 "we" 块 → tier 0
        check("连续块 qianwen vs qw er -> tier 0",
                0, matchTier("qw", "qianwen", "千问", tokens("qw", "er"), compileTokenMasks(tokens("qw", "er"))));

        // ======== 非 ASCII token 回落（掩码 = -1）========

        check("非 ASCII token 回落: 密 命中 密码",
                true, matchTier("mima", "mima", "密码", tokens("密"), compileTokenMasks(tokens("密"))) >= 0);
        check("非 ASCII token 回落档位为 0",
                0, matchTier("mima", "mima", "密码", tokens("密"), compileTokenMasks(tokens("密"))));

        // ======== 非 ASCII 文本不被 ASCII token 命中 ========
        // 全角字符不被 ASCII token 命中（与旧 containsIgnoreCase 行为一致）
        // raw/pinyin/initials 全为全角 'ａ'(U+FF41)，不在 a-z 范围
        check("ASCII token 不命中全角文本",
                -1, matchTier("ａ", "ａ", "ａ", tokens("qw"), compileTokenMasks(tokens("qw"))));

        // ======== emptyMatches 两处相反语义 ========

        check("emptyMatches=true: 空 token 匹配全部", true,  matchesTokens("abc", tokens(), true));
        check("emptyMatches=false: 空 token 不匹配",  false, matchesTokens("abc", tokens(), false));

        // ======== 空查询: 全部保留, 档位恒为 0 ========

        long now = System.currentTimeMillis();
        List<TestApp> allApps = Arrays.asList(WEIXIN, ZHIFUBAO, GAODE, JINGDONG, JIANYING, QQYINYUE, ZHIHU, TAOBAO);
        List<TestApp> emptyResult = sortApps(allApps, now, tokens());
        check("空查询: 8 个全保留", 8, emptyResult.size());
        for (TestApp app : emptyResult) {
            long[] emptyMasks = new long[0];
            check("空查询 " + app.label + " 档位=0",
                    0, matchTier(app.initials, app.pinyin, app.raw, tokens(), emptyMasks));
        }

        // ======== 排序键全序: 档位 → score → 长度 → 字典序 ========

        long nowSort = 1000000000000L; // 固定时间避免抖动
        TestApp a = new TestApp("AAA", "aaa", "aaa", "aaa", 10, nowSort);
        TestApp b = new TestApp("BB",  "bb",  "bb",  "bb",   5, nowSort);
        TestApp c = new TestApp("CC",  "cc",  "cc",  "cc",  10, nowSort - 86400000L); // 1 天前
        List<TestApp> sortResult = sortApps(Arrays.asList(b, c, a), nowSort, tokens());
        // A=ln(11)≈2.398, C=ln(11)*0.5^(1/3)≈1.903, B=ln(6)≈1.792
        check("排序: A(≈2.398) > C(≈1.903) > B(≈1.792)", "AAA,CC,BB",
                sortResult.get(0).label + "," + sortResult.get(1).label + "," + sortResult.get(2).label);

        // 同 score 同长度 → 字典序
        TestApp d1 = new TestApp("DD1", "dd1", "dd1", "dd1", 5, nowSort);
        TestApp d2 = new TestApp("DD2", "dd2", "dd2", "dd2", 5, nowSort);
        List<TestApp> sortResult2 = sortApps(Arrays.asList(d2, d1), nowSort, tokens());
        check("同 score 同长度 → 字典序 dd1 < dd2", "DD1,DD2",
                sortResult2.get(0).label + "," + sortResult2.get(1).label);

        // ======== 模糊音折叠 + 降档 ========

        check("fold yingyue -> yinyue", "yinyue", foldPinyin("yingyue"));
        check("fold zhihu -> zihu", "zihu", foldPinyin("zhihu"));
        check("fold shang -> san（sh+ang 两步）", "san", foldPinyin("shang"));
        check("fold sang -> san（与 shang 同归一）", "san", foldPinyin("sang"));
        check("fold chen -> cen", "cen", foldPinyin("chen"));
        check("fold weixin 不变", "weixin", foldPinyin("weixin"));

        // 打全了：yingyue 对 yinyue，精确 -1，模糊 0
        check("精确 yingyue vs yinyue -> -1",
                -1, matchTier("yy", "yinyue", "音乐", tokens("y", "i", "n", "g", "y", "u", "e"), compileTokenMasks(tokens("y", "i", "n", "g", "y", "u", "e"))));
        check("模糊 yingyue vs yinyue -> 0",
                0, matchTierFuzzy("yinyue", foldedTokens("yingyue")));
        // 打对了还是高档：yinyue 精确 tier 1（initials 太短锚不上，pinyin 锚上）
        check("精确 yinyue vs yinyue -> 1",
                1, matchTier("yy", "yinyue", "音乐", tokens("y", "i", "n", "y", "u", "e"), compileTokenMasks(tokens("y", "i", "n", "y", "u", "e"))));
        // 平舌打成翘舌：zhi 对 zi（资），精确 -1，模糊 0
        check("模糊 zhi vs zi -> 0",
                0, matchTierFuzzy("zi", foldedTokens("zhi")));
        // 不相关的：wang 对 yinyue，模糊也是 -1
        check("模糊 wang vs yinyue -> -1",
                -1, matchTierFuzzy("yinyue", foldedTokens("wang")));

        // ======== 反向模糊音对称（打短中长 = 打长中短，折叠归一后同一计算）========
        // zi→zhi：查折叠串 zi，打 zi 与打 zhi 同分
        check("对称 zi vs zhi折叠zi -> 0",
                0, matchTierFuzzy("zi", foldedTokens("zi")));
        // 连续块语义：精确只认相邻，"zhi" 里无 "zi" 相邻 → -1（打 zi 找知走模糊，那条还活着）
        check("连续块 精确 zi vs zhi -> -1",
                -1, matchTier("z", "zhi", "知", tokens("z", "i"), compileTokenMasks(tokens("z", "i"))));
        // sen→shen：打 sen 中 深（折叠同归 sen）
        check("对称 sen vs shen折叠sen -> 0",
                0, matchTierFuzzy("sen", foldedTokens("sen")));
        // min→ming：打 min 中 明（折叠同归 min）
        check("对称 min vs ming折叠min -> 0",
                0, matchTierFuzzy("min", foldedTokens("min")));
        // zao→zhao：打 zao 中 找（折叠同归 zao）
        check("对称 zao vs zhao折叠zao -> 0",
                0, matchTierFuzzy("zao", foldedTokens("zao")));
        // ci→chi：打 ci 中 吃（折叠同归 ci）
        check("对称 ci vs chi折叠ci -> 0",
                0, matchTierFuzzy("ci", foldedTokens("ci")));
        // 反例仍不中：sin vs xin（s/x 本来就不同声母，折叠不归一）
        check("反例 sin vs xin -> -1",
                -1, matchTierFuzzy("xin", foldedTokens("sin")));

        // ======== 多音字变体同档（Kotlin altPinyins/altPinyinsFolded）========
        // QQ音乐：基准 qqyinle + 变体 qqyinyue；打 yinyue 变体降级命中 tier 0
        TestApp yinyueApp = new TestApp("QQ音乐", "qq音乐", "qqyinle", "qqyl", 6, 0,
            Arrays.asList("qqyinyue"), Arrays.asList("qqyinyue"));
        check("变体 yinyue vs qqyinyue -> 0",
                0, matchTier(yinyueApp.initials, yinyueApp.pinyin, yinyueApp.raw,
                    yinyueApp.altPinyins,
                    tokens("y", "i", "n", "y", "u", "e"),
                    compileTokenMasks(tokens("y", "i", "n", "y", "u", "e"))));
        // 变体折叠：打 yingyue 中变体（折叠后同归 yinyue）
        check("变体折叠 yingyue vs qqyinyue -> 0",
                0, matchTierFuzzy("qqyinyue", yinyueApp.altPinyinsFolded, foldedTokens("yingyue")));

        // ======== 连续块：相邻对位赢，稀疏子序列出局（hy/dy 修法）========
        // 抖音 initials "dy"：[d,y] 相邻，span 2 offset 0
        check("span dy vs dy -> [2,2,0]",
                "[2, 2, 0]", Arrays.toString(matchTierFull("dy", "douyin", "抖音",
                    Collections.<String>emptyList(), tokens("d", "y"), compileTokenMasks(tokens("d", "y")))));
        // 连续块语义：fallback 串 "dkactivityzdydz" @10 处真有相邻 "dy"（...z[d,y]dz），
        // 是合法中段命中 tier 0 offset 10——排得低，不再靠字典序偷赢。
        check("连续块 dy vs dkactivityzdydz -> 0",
                0, matchTier("dkactivityzdydz", "neicun", "内存",
                    Collections.<String>emptyList(), tokens("d", "y"), compileTokenMasks(tokens("d", "y"))));
        // 连续块语义：抖音 tier 2 在前，内存中段 tier 0 在后——这次靠档位，不靠字典序。
        TestApp dyApp = new TestApp("抖音", "抖音", "douyin", "dy", 0, 0);
        TestApp memApp = new TestApp("内存", "内存", "neicun", "dkactivityzdydz", 0, 0);
        List<TestApp> dyResult = sortApps(Arrays.asList(memApp, dyApp), nowSort, tokens("d", "y"));
        check("排序 dy: 抖音(t2) > 内存(t0中段)", "抖音,内存",
                dyResult.get(0).label + "," + dyResult.get(1).label);

        // ======== 连续块语义：稀疏子序列不再是命中，无块即出局（旧逻辑下这里变红）========
        // xx会议 initials "xxhy"：[h,y] 中段相邻 span 2 offset 2，tier 0（连续块活着）
        check("复现 hy vs xxhy -> [0,2,2]",
                "[0, 2, 2]", Arrays.toString(matchTierFull("xxhy", "xxhuiyi", "xx会议",
                    Collections.<String>emptyList(), tokens("h", "y"), compileTokenMasks(tokens("h", "y")))));
        // 连续块语义：红烧鱼 hsy 里 h,y 不相邻 → -1（跳字缩写死）
        check("连续块 hy vs hsy -> -1",
                -1, matchTier("hsy", "hongshaoyu", "红烧鱼",
                    Collections.<String>emptyList(), tokens("h", "y"), compileTokenMasks(tokens("h", "y"))));
        // 连续块语义：红烧鱼出局，只剩会议。
        TestApp huiyiConf = new TestApp("xx会议", "xx会议", "xxhuiyi", "xxhy", 0, 0);
        TestApp hongshao = new TestApp("红烧鱼", "红烧鱼", "hongshaoyu", "hsy", 0, 0);
        List<TestApp> gapResult = sortApps(Arrays.asList(hongshao, huiyiConf), nowSort, tokens("h", "y"));
        check("排序 hy: 红烧鱼出局，只剩会议", 1, gapResult.size());
        check("排序 hy: 剩的是会议", "xx会议", gapResult.get(0).label);

        // ======== micro-bench: N=300 单 token 分配量 ========

        List<TestApp> bench = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            String s = "app" + i;
            bench.add(new TestApp(s, s, s, s, 1, nowSort));
        }
        java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
        ThreadMXBean mx = base instanceof ThreadMXBean ? (ThreadMXBean) base : null;
        if (mx != null && mx.isThreadAllocatedMemorySupported()) {
            long before = mx.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < 100; i++) sortApps(bench, nowSort, tokens("qw"));
            long after = mx.getCurrentThreadAllocatedBytes();
            double kbPerCall = (after - before) / 1024.0 / 100;
            System.out.printf("  bench  N=300 单 token: %.1f KB/次%n", kbPerCall);
            // 现状约 384 KB/次；方案 C 应 < 60 KB/次
            check("分配量 < 60 KB/次", kbPerCall < 60);
        } else {
            // 无 ThreadAllocatedBytes：只跑时间
            long t0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) sortApps(bench, nowSort, tokens("qw"));
            long ms = (System.nanoTime() - t0) / 1000000;
            System.out.printf("  bench  N=300 单 token: %d ms/1000次 (无分配计量)%n", ms);
            check("bench 完成", true);
        }

        // ======== 规模 bench（文件量级）：5000 文件名 + 500 条×300 字正文，只打印不设线 ========
        // 镜像无 houbb（真机 houbb 成本另算），这里只看匹配循环随规模的伸缩 + 分配。
        List<TestApp> big = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            String s = "IMG_202401" + (i % 30) + "_" + i + ".jpg";
            big.add(new TestApp(s, s, s, s, 0, 0));
        }
        StringBuilder bodySb = new StringBuilder();
        for (int i = 0; i < 300; i++) bodySb.append((char) ('a' + (i % 26)));
        String bodyStr = bodySb.toString();
        for (int i = 0; i < 500; i++) big.add(new TestApp(bodyStr, bodyStr, bodyStr, bodyStr, 0, 0));
        for (int i = 0; i < 5; i++) sortApps(big, nowSort, tokens("h", "y"));
        long t0 = System.nanoTime();
        int reps = 20;
        for (int i = 0; i < reps; i++) sortApps(big, nowSort, tokens("h", "y"));
        long ms = (System.nanoTime() - t0) / 1000000;
        System.out.printf("  scale  N=%d 双 token: %d ms/%d次 (%.1f ms/次)%n", big.size(), ms, reps, ms * 1.0 / reps);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
