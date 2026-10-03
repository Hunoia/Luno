// 快速启动器搜索与排序的回归检查。
//
// 它是 AppSearch.kt 中 compileTokenMasks / charMatches / matchWithMasks /
// matchTier / sortApps 的 Java 等价转写，**改 Kotlin 侧必须同步改这里**。
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

    // ======== 转写：matchWithMasks ========
    // 返回: 1=ANCHOR, 0=DEGRADED, -1=NO_MATCH

    static final int NO_MATCH = -1;
    static final int DEGRADED = 0;
    static final int ANCHOR = 1;

    static int matchWithMasks(String text, long[] masks) {
        if (masks.length == 0) return DEGRADED;
        if (masks.length > text.length()) return NO_MATCH;

        if (charMatches(text.charAt(0), masks[0])) {
            int pos = 1;
            boolean ok = true;
            for (int i = 1; i < masks.length; i++) {
                boolean found = false;
                while (pos < text.length()) {
                    if (charMatches(text.charAt(pos), masks[i])) { found = true; pos++; break; }
                    pos++;
                }
                if (!found) { ok = false; break; }
            }
            if (ok) return ANCHOR;
        }

        int pos = 0;
        boolean allMatched = true;
        for (int i = 0; i < masks.length; i++) {
            boolean found = false;
            while (pos < text.length()) {
                if (charMatches(text.charAt(pos), masks[i])) { found = true; pos++; break; }
                pos++;
            }
            if (!found) { allMatched = false; break; }
        }
        return allMatched ? DEGRADED : NO_MATCH;
    }

    // ======== 转写：matchTier ========
    // 返回: 2=initials anchor, 1=pinyin/raw anchor, 0=degraded, -1=no match

    static int matchTier(String initials, String pinyin, String raw, List<String> tokens, long[] masks) {
        if (tokens.isEmpty()) return 0;

        if (masks.length > 0) {
            boolean hasNonAscii = false;
            for (long m : masks) if (m == -1L) { hasNonAscii = true; break; }
            if (hasNonAscii) {
                return (matchesTokens(initials, tokens, true)
                    || matchesTokens(pinyin, tokens, true)
                    || matchesTokens(raw, tokens, true)) ? 0 : -1;
            }
        }

        int ini = matchWithMasks(initials, masks);
        if (ini == ANCHOR) return 2;
        boolean anyDegraded = (ini == DEGRADED);

        int py = matchWithMasks(pinyin, masks);
        if (py == ANCHOR) return 1;
        anyDegraded = anyDegraded || (py == DEGRADED);

        int r = matchWithMasks(raw, masks);
        if (r == ANCHOR) return 1;
        anyDegraded = anyDegraded || (r == DEGRADED);

        return anyDegraded ? 0 : -1;
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
        TestApp(String label, String raw, String pinyin, String initials, long launchCount, long recentLaunchTime) {
            this.label = label; this.raw = raw; this.pinyin = pinyin; this.initials = initials;
            this.launchCount = launchCount; this.recentLaunchTime = recentLaunchTime;
        }
    }

    static class SearchEntry {
        final TestApp app;
        final int matchTier;
        final double usageScore;
        final String labelLower;
        final int labelLen;
        SearchEntry(TestApp app, int matchTier, double usageScore) {
            this.app = app; this.matchTier = matchTier; this.usageScore = usageScore;
            this.labelLower = app.raw; this.labelLen = app.label.length();
        }
    }

    static List<TestApp> sortApps(List<TestApp> apps, long now, List<String> tokens) {
        long[] masks = tokens.isEmpty() ? new long[0] : compileTokenMasks(tokens);
        List<SearchEntry> entries = new ArrayList<>(apps.size());
        for (TestApp app : apps) {
            int tier = matchTier(app.initials, app.pinyin, app.raw, tokens, masks);
            if (tier < 0) continue;
            double usageScore = 0;
            if (app.launchCount > 0 && app.recentLaunchTime > 0) {
                double ageDays = (now - app.recentLaunchTime) / 86400000.0;
                usageScore = app.launchCount / (1.0 + ageDays / 7.0);
            }
            entries.add(new SearchEntry(app, tier, usageScore));
        }
        entries.sort((a, b) -> {
            int c = Integer.compare(b.matchTier, a.matchTier);
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
        check("V1 高德地图 gh df ty -> tier 2",
                2, matchTier(GAODE.initials, GAODE.pinyin, GAODE.raw, tokens("gh", "df", "ty"), compileTokenMasks(tokens("gh", "df", "ty"))));
        check("V1 京东 jk df -> tier 2",
                2, matchTier(JINGDONG.initials, JINGDONG.pinyin, JINGDONG.raw, tokens("jk", "df"), compileTokenMasks(tokens("jk", "df"))));
        check("V1 剪映 jk ty -> tier 2",
                2, matchTier(JIANYING.initials, JIANYING.pinyin, JIANYING.raw, tokens("jk", "ty"), compileTokenMasks(tokens("jk", "ty"))));
        check("V1 QQ音乐 qw ty l -> tier 2",
                2, matchTier(QQYINYUE.initials, QQYINYUE.pinyin, QQYINYUE.raw, tokens("qw", "ty", "l"), compileTokenMasks(tokens("qw", "ty", "l"))));

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

        // pinyin anchor → tier 1（initials 太短无法锚定，但 pinyin 够长）
        // 千问: initials="qw"(2字), pinyin="qianwen"(7字), tokens=["qw","er"]
        // initials "qw": anchor 'q' ok, but 'w' 不匹配 {e,r} → no match
        // pinyin "qianwen": anchor 'q' ok, 后续子序列找到 'e' → ANCHOR → tier 1
        check("pinyin anchor -> tier 1",
                1, matchTier("qw", "qianwen", "千问", tokens("qw", "er"), compileTokenMasks(tokens("qw", "er"))));

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
        // A score=10, C score=10/(1+1/7)≈8.75, B score=5
        check("排序: A(score=10) > C(score≈8.75) > B(score=5)", "AAA,CC,BB",
                sortResult.get(0).label + "," + sortResult.get(1).label + "," + sortResult.get(2).label);

        // 同 score 同长度 → 字典序
        TestApp d1 = new TestApp("DD1", "dd1", "dd1", "dd1", 5, nowSort);
        TestApp d2 = new TestApp("DD2", "dd2", "dd2", "dd2", 5, nowSort);
        List<TestApp> sortResult2 = sortApps(Arrays.asList(d2, d1), nowSort, tokens());
        check("同 score 同长度 → 字典序 dd1 < dd2", "DD1,DD2",
                sortResult2.get(0).label + "," + sortResult2.get(1).label);

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

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
