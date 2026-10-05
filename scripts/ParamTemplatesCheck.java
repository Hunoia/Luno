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

    // 对应 ParamTemplates.collectTokens(text, out)（含表达式操作数名提取）
    static LinkedHashSet<String> collectTokens(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (text.indexOf('$') < 0) return out;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != '$') { i++; continue; }
            Object[] hit = tokenAt(text, i);
            if (hit == null) {
                Object[] expr = expressionAt(text, i);
                if (expr == null) { i++; continue; }
                collectExpressionTokens((String[]) expr[0], out);
                i = (Integer) expr[1] + 1;
                continue;
            }
            out.add((String) hit[0]);
            i = (Integer) hit[2] + 1;
        }
        return out;
    }

    // 对应 ParamTemplates.resolve（含表达式求值分支）
    static String resolve(String text, Map<String, String> values) {
        if (values.isEmpty() || text.indexOf('$') < 0) return text;
        StringBuilder builder = null;
        int copied = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) != '$') { i++; continue; }
            Object[] hit = tokenAt(text, i);
            if (hit == null) {
                Object[] expr = expressionAt(text, i);
                if (expr == null) { i++; continue; }
                String replacement = evaluate((String[]) expr[0], values);
                if (replacement == null) { i++; continue; }
                int rangeFirst = i;
                int rangeLast = (Integer) expr[1];
                if (builder == null) builder = new StringBuilder(text.length() + 16);
                builder.append(text, copied, rangeFirst).append(replacement);
                copied = rangeLast + 1;
                i = rangeLast + 1;
                continue;
            }
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

    // ---- 表达式（对应 ParamTemplates.expressionAt/evaluate/ExprParser） ----
    // 返回 {String[]{kind,left,op,right}, rangeLast}，kind: N=变量名 L=字面量；失败返回 null
    static Object[] expressionAt(String text, int dollar) {
        if (dollar + 1 >= text.length() || text.charAt(dollar + 1) != '{') return null;
        int end = dollar + 2;
        while (end < text.length() && text.charAt(end) != '}') end++;
        if (end >= text.length()) return null;
        String[] expr = parseExpression(text.substring(dollar + 2, end));
        if (expr == null) return null;
        return new Object[]{expr, end};
    }

    static void collectExpressionTokens(String[] expr, LinkedHashSet<String> out) {
        if (expr[0].equals("N")) out.add(expr[1]);
        if (expr[3] != null && expr[2].equals("N")) out.add(expr[3]);
    }

    static String evaluate(String[] expr, Map<String, String> values) {
        String left = resolveOperand(expr[0], expr[1], values);
        if (left == null) return null;
        if (expr[4] == null) return left;
        String right = resolveOperand(expr[2], expr[3], values);
        if (right == null) return null;
        String op = expr[4];
        Double ln = toDoubleOrNull(left);
        Double rn = toDoubleOrNull(right);
        if (ln != null && rn != null) {
            switch (op) {
                case "==": return Boolean.toString(ln.equals(rn));
                case "!=": return Boolean.toString(!ln.equals(rn));
                case ">": return Boolean.toString(ln > rn);
                case "<": return Boolean.toString(ln < rn);
                case ">=": return Boolean.toString(ln >= rn);
                case "<=": return Boolean.toString(ln <= rn);
                default: return null;
            }
        }
        switch (op) {
            case "==": return Boolean.toString(left.equals(right));
            case "!=": return Boolean.toString(!left.equals(right));
            default: return null;
        }
    }

    static String resolveOperand(String kind, String text, Map<String, String> values) {
        if (kind.equals("N")) return values.get(text);
        return text;
    }

    static Double toDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String[] parseExpression(String body) {
        int[] pos = {0};
        skipSpaces(body, pos);
        String[] left = parseOperand(body, pos);
        if (left == null) return null;
        skipSpaces(body, pos);
        if (pos[0] >= body.length()) return new String[]{left[0], left[1], null, null, null};
        String op = parseOp(body, pos);
        if (op == null) return null;
        skipSpaces(body, pos);
        String[] right = parseOperand(body, pos);
        if (right == null) return null;
        skipSpaces(body, pos);
        if (pos[0] != body.length()) return null;
        return new String[]{left[0], left[1], right[0], right[1], op};
    }

    static String[] parseOperand(String body, int[] pos) {
        if (pos[0] >= body.length()) return null;
        char c = body.charAt(pos[0]);
        if (c == '\'') {
            int end = body.indexOf('\'', pos[0] + 1);
            if (end < 0) return null;
            String lit = body.substring(pos[0] + 1, end);
            pos[0] = end + 1;
            return new String[]{"L", lit};
        }
        if (isNameStart(c)) {
            int start = pos[0]++;
            while (pos[0] < body.length() && isNamePart(body.charAt(pos[0]))) pos[0]++;
            return new String[]{"N", body.substring(start, pos[0])};
        }
        if (c == '-' || Character.isDigit(c)) {
            int start = pos[0]++;
            boolean dot = false;
            while (pos[0] < body.length()) {
                char d = body.charAt(pos[0]);
                if (Character.isDigit(d)) pos[0]++;
                else if (d == '.' && !dot) { dot = true; pos[0]++; }
                else break;
            }
            String num = body.substring(start, pos[0]);
            if (num.equals("-") || num.equals(".") || num.equals("-.")) return null;
            return new String[]{"L", num};
        }
        return null;
    }

    static String parseOp(String body, int[] pos) {
        String[] ops = {"==", "!=", ">=", "<=", ">", "<"};
        for (String op : ops) {
            if (body.startsWith(op, pos[0])) {
                pos[0] += op.length();
                return op;
            }
        }
        return null;
    }

    static void skipSpaces(String body, int[] pos) {
        while (pos[0] < body.length() && Character.isWhitespace(body.charAt(pos[0]))) pos[0]++;
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
        check("比较数字真", "true", resolve("${battery>20}", v));
        check("比较数字假", "false", resolve("${battery>90}", v));
        check("比较相等字符串", "true", resolve("${pkg=='com.x'}", v));
        check("无引号右值按变量查找不到保留", "${pkg==com.x}", resolve("${pkg==com.x}", v));
        check("比较单引号", "true", resolve("${pkg=='com.x'}", v));
        check("比较空格容忍", "true", resolve("${battery >= 76 }", v));
        check("比较未知变量保留", "${nope>1}", resolve("${nope>1}", v));
        check("比较非数字大小保留", "${pkg>20}", resolve("${pkg>20}", v));
        check("比较语法非法保留", "${battery>>20}", resolve("${battery>>20}", v));
        check("表达式tokenize", "[battery]", collectTokens("x${battery>20}y").toString());
        check("旧用例不受影响", "com.x", resolve("${pkg}", v));
        check("tokenize 无 token", "[]", collectTokens("plain text").toString());
        check("tokenize 去重", "[pkg]", collectTokens("${pkg}${pkg}").toString());
        check("tokenize 只认合法 token", "[pkg]",
                collectTokens("${1x} $- ${} ${ ${pkg}").toString());

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
