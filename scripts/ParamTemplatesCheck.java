// ParamTemplates 扫描器的回归检查。
//
// 为什么要它：扫描器手写而非用正则（Android 的 java.util.regex 底层是 ICU，
// 不接受 `\{` 这类转义，曾导致无障碍服务进程崩溃）。手写逻辑没有编译器兜底，
// 改这块时用本文件验证边界。
//
// 它是 ParamTemplates.kt 中 tokenAt / collectTokens / resolve 的 Java 等价转写，
// **改 Kotlin 侧必须同步改这里**。
//
// 不引入 JUnit、不进 gradle，跑法：
//   cd scripts && javac -encoding UTF-8 ParamTemplatesCheck.java \
//     && java -Dfile.encoding=UTF-8 ParamTemplatesCheck
import java.util.*;

public class ParamTemplatesCheck {

    static boolean isNameStart(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    static boolean isNamePart(char c) {
        return isNameStart(c) || c == '.' || (c >= '0' && c <= '9');
    }

    // 对应 ParamTemplates.tokenAt：返回 {name, rangeFirst, rangeLast}，非法 token 返回 null
    static Object[] tokenAt(String text, int dollar) {
        if (dollar + 1 >= text.length() || text.charAt(dollar + 1) != '{') return null;
        int nameStart = dollar + 2;
        if (nameStart >= text.length() || !isNameStart(text.charAt(nameStart))) return null;
        int end = nameStart + 1;
        while (end < text.length() && isNamePart(text.charAt(end))) end++;
        if (end >= text.length() || text.charAt(end) != '}') return null;
        return new Object[]{text.substring(nameStart, end), dollar, end};
    }

    // 对应 ParamTemplates.collectTokens(text, out)
    static LinkedHashSet<String> collectTokens(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (text.indexOf('$') < 0) return out;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != '$') { i++; continue; }
            Object[] hit = tokenAt(text, i);
            if (hit == null) { i++; continue; }
            out.add((String) hit[0]);
            i = (Integer) hit[2] + 1;
        }
        return out;
    }

    // 对应 ParamTemplates.resolve
    static String resolve(String text, Map<String, String> values) {
        if (values.isEmpty() || text.indexOf('$') < 0) return text;
        StringBuilder builder = null;
        int copied = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != '$') { i++; continue; }
            Object[] hit = tokenAt(text, i);
            if (hit == null) { i++; continue; }
            String name = (String) hit[0];
            int rangeFirst = (Integer) hit[1];
            int rangeLast = (Integer) hit[2];
            if (values.containsKey(name)) {
                if (builder == null) builder = new StringBuilder(text.length() + 16);
                builder.append(text, copied, rangeFirst).append(values.get(name));
                copied = rangeLast + 1;
            }
            i = rangeLast + 1;
        }
        if (builder == null) return text;
        builder.append(text, copied, text.length());
        return builder.toString();
    }

    static int failures = 0;

    static void check(String label, String expected, String actual) {
        boolean ok = expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "  ok  " : "FAIL  ") + label
                + (ok ? " -> " + actual
                      : "\n        expected=[" + expected + "]\n        actual  =[" + actual + "]"));
    }

    public static void main(String[] args) {
        Map<String, String> v = new HashMap<>();
        v.put("pkg", "com.x");
        v.put("pkg.label", "设置");
        v.put("battery", "76");
        v.put("a", "1");
        v.put("b", "2");
        v.put("empty", "");

        check("空串", "", resolve("", v));
        check("无 token", "settings put", resolve("settings put", v));
        check("单 token", "com.x", resolve("${pkg}", v));
        check("前后缀", "pre-76-post", resolve("pre-${battery}-post", v));
        check("未知 token 原样保留", "${trigger.app}", resolve("${trigger.app}", v));
        check("相邻 token", "12", resolve("${a}${b}", v));
        check("无花括号", "$pkg", resolve("$pkg", v));
        check("未闭合", "${pkg", resolve("${pkg", v));
        check("非法起始字符", "${1abc}", resolve("${1abc}", v));
        check("token 后接文本", "com.xsuffix", resolve("${pkg}suffix", v));
        check("多段混合", "xcom.xy76z", resolve("x${pkg}y${battery}z", v));
        check("双美元", "$com.x", resolve("$${pkg}", v));
        check("带点 token", "设置", resolve("${pkg.label}", v));
        check("空 token 名", "${}", resolve("${}", v));
        check("已知 + 未知混合", "com.x${unknown}", resolve("${pkg}${unknown}", v));
        check("结尾单个美元", "abc$", resolve("abc$", v));
        check("嵌套病态输入(不崩溃不递归)", "${com.x}", resolve("${${pkg}}", v));
        check("已知值替换为空串", "a--b", resolve("a-${empty}-b", v));
        check("空 map 时原样", "${pkg}", resolve("${pkg}", new HashMap<>()));
        check("shell 变量不受影响", "am start ${HOME}", resolve("am start ${HOME}", v));
        check("trigger.volume 取值",
                "com.tencent.mm_MUSIC_UP",
                resolve("${trigger.volume}", Map.of("trigger.volume", "com.tencent.mm_MUSIC_UP")));
        check("替换值含 ${} 不二次展开", "[${a}]", resolve("[${pkg}]", Map.of("pkg", "${a}")));

        check("tokenize 集合", "[pkg, battery, nope]",
                collectTokens("x${pkg}y${battery}${nope}").toString());
        check("tokenize 无 token", "[]", collectTokens("plain text").toString());
        check("tokenize 去重", "[pkg]", collectTokens("${pkg}${pkg}").toString());
        check("tokenize 只认合法 token", "[pkg]",
                collectTokens("${1x} $- ${} ${ ${pkg}").toString());

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
