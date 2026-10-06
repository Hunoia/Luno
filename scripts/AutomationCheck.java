import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 自动化 v2 语义校验（mirror Kotlin：AutomationEngine 边沿/节流 + effective* 迁移 + 新脉冲匹配）。
 * 跑法：javac -encoding UTF-8 AutomationCheck.java && java -Dfile.encoding=UTF-8 AutomationCheck
 */
public class AutomationCheck {

    static int failures = 0;

    static void check(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        System.out.println((ok ? "  ok  " : "  FAIL ") + name + " -> " + actual);
        if (!ok) {
            System.out.println("        expected: " + expected);
            failures++;
        }
    }

    // ======== 转写：边沿（mirror AutomationEngine.evaluate）========
    static boolean edge(String fireOn, boolean hit, boolean prev, boolean pulseEdge) {
        switch (fireOn) {
            case "ENTER": return hit && (!prev || pulseEdge);
            case "EXIT": return !hit && prev;
            case "BOTH": return hit != prev || (hit && pulseEdge);
            default: return false;
        }
    }

    static boolean edge(String fireOn, boolean hit, boolean prev) {
        return edge(fireOn, hit, prev, false);
    }

    // ======== 转写：节流（mirror throttleBlockReason/markFired）========
    // 值驱动：off 总闸放行；冷却（cooldownMs>0）与窗口（count>=1 且 window>=1s）
    // 各算各，先冷后窗。
    static String throttleBlock(String kind, Long lastFired, ArrayDeque<Long> window,
                                long nowMs, long cooldownMs, int maxCount, long windowMs) {
        if ("off".equals(kind)) return null;
        if (cooldownMs > 0) {
            if (lastFired != null && nowMs - lastFired < cooldownMs) return "throttled_cooldown";
        }
        if (maxCount >= 1 && windowMs >= 1000L) {
            while (!window.isEmpty() && nowMs - window.peekFirst() >= windowMs) window.removeFirst();
            if (window.size() >= maxCount) return "throttled_window";
        }
        return null;
    }

    // ======== 转写：effectiveSteps / effectiveThrottle（mirror 迁移）========
    static List<String> effectiveSteps(List<String> steps, boolean hasAction) {
        if (!steps.isEmpty()) return steps;
        if (hasAction) return Collections.singletonList("action-legacy");
        return Collections.<String>emptyList();
    }

    static String effectiveThrottle(String kind, long legacyCooldownMs) {
        if (!"off".equals(kind)) return kind;
        if (legacyCooldownMs > 0) return "cooldown";
        return "off";
    }

    // ======== 转写：新脉冲匹配（mirror ConditionEvaluator）========
    static boolean notificationMatches(List<String> packages, String keyword,
                                       String pkg, String title, String text) {
        if (pkg == null) return false; // 无脉冲
        if (!packages.isEmpty() && !packages.contains(pkg)) return false;
        if (keyword == null || keyword.isEmpty()) return true;
        String kw = keyword.toLowerCase();
        return title.toLowerCase().contains(kw) || text.toLowerCase().contains(kw);
    }

    static boolean connectionMatches(String expected, String pulse) {
        if (pulse == null) return false;
        return expected == null || expected.equals(pulse);
    }

    static boolean scheduledMatches(List<Integer> minutes, int minuteOfDay, boolean tickHit) {
        if (!tickHit) return false;
        if (minutes.isEmpty()) return false;
        return minutes.contains(minuteOfDay);
    }

    /** ticker 侧星期门（mirror TimeConditionTicker）：空集=每天。 */
    static boolean scheduledDue(List<Integer> minutes, List<Integer> weekdays,
                                int minuteOfDay, int weekday) {
        if (!minutes.contains(minuteOfDay)) return false;
        return weekdays.isEmpty() || weekdays.contains(weekday);
    }

    // ======== 转写：触钮显隐 target 解析（mirror InternalExecutors）========
    static List<String> resolveButtons(String target, List<String> all, List<String> selected) {
        if ("EXCEPT".equals(target)) {
            List<String> out = new ArrayList<>(all);
            out.removeAll(selected);
            return out;
        }
        if ("ONLY".equals(target)) return new ArrayList<>(selected);
        return new ArrayList<>(all);
    }

    // ======== 转写：步骤路径（mirror RuleEffect.stepAt）========
    // 树形 steps 简化为：List<Object>，Action 步=String，IF 步=List<List<String>>[then, else]。
    @SuppressWarnings("unchecked")
    static Object stepAt(List<Object> steps, String path) {
        if (path == null || path.isEmpty()) return null;
        String[] tokens = path.split("\\.");
        List<Object> current = steps;
        Object step = null;
        for (int t = 0; t < tokens.length; t++) {
            String tok = tokens[t];
            if (tok.equals("then") || tok.equals("else")) {
                if (!(step instanceof List)) return null;
                List<Object> branches = (List<Object>) step;
                current = (List<Object>) (tok.equals("then") ? branches.get(0) : branches.get(1));
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(tok);
            } catch (NumberFormatException e) {
                return null;
            }
            if (index < 0 || index >= current.size()) return null;
            step = current.get(index);
            if (t == tokens.length - 1) return step;
            // 下一个 token 必须是 then/else，否则路径非法
        }
        return step;
    }

    public static void main(String[] args) {
        // 边沿：ENTER 上升沿
        check("ENTER 上升沿触发", true, edge("ENTER", true, false));
        check("ENTER 保持不重触发", false, edge("ENTER", true, true));
        check("ENTER 首评 prev=true 不触发", false, edge("ENTER", true, true));
        // 边沿：EXIT 下降沿
        check("EXIT 下降沿触发", true, edge("EXIT", false, true));
        check("EXIT 上升沿不触发", false, edge("EXIT", true, false));
        check("EXIT 保持 false 不触发", false, edge("EXIT", false, false));
        // 边沿：BOTH
        check("BOTH 上升沿触发", true, edge("BOTH", true, false));
        check("BOTH 下降沿触发", true, edge("BOTH", false, true));
        check("BOTH 保持不触发", false, edge("BOTH", true, true));
        // 边沿：脉冲命中即边沿（只影响 ENTER/BOTH 进入侧）
        check("ENTER 脉冲首评触发", true, edge("ENTER", true, true, true));
        check("ENTER 脉冲未命中不触发", false, edge("ENTER", false, true, true));
        check("ENTER 无脉冲保持不触发", false, edge("ENTER", true, true, false));
        check("EXIT 脉冲不影响", false, edge("EXIT", true, true, true));
        check("BOTH 脉冲进入侧触发", true, edge("BOTH", true, true, true));
        check("BOTH 脉冲未命中不触发", false, edge("BOTH", false, false, true));

        // 节流：cooldown
        check("cooldown 窗内拦", "throttled_cooldown",
                throttleBlock("cooldown", 1000L, new ArrayDeque<Long>(), 1500L, 1000L, 1, 60000L));
        check("cooldown 窗外放行", null,
                throttleBlock("cooldown", 1000L, new ArrayDeque<Long>(), 2000L, 1000L, 1, 60000L));
        check("cooldown 首触发放行", null,
                throttleBlock("cooldown", null, new ArrayDeque<Long>(), 2000L, 1000L, 1, 60000L));
        // 节流：window（3次/60s）
        ArrayDeque<Long> w = new ArrayDeque<>(Arrays.asList(1000L, 2000L, 3000L));
        check("window 满拦", "throttled_window",
                throttleBlock("window", null, w, 4000L, 0L, 3, 60000L));
        ArrayDeque<Long> w2 = new ArrayDeque<>(Arrays.asList(1000L, 2000L));
        check("window 未满放行", null,
                throttleBlock("window", null, w2, 4000L, 0L, 3, 60000L));
        ArrayDeque<Long> w3 = new ArrayDeque<>(Arrays.asList(1000L, 2000L, 3000L));
        check("window 过期滑出放行", null,
                throttleBlock("window", null, w3, 70000L, 0L, 3, 60000L));
        check("off 不拦", null,
                throttleBlock("off", 1000L, new ArrayDeque<Long>(), 1500L, 1000L, 1, 60000L));
        check("off 存量默认值也不拦", null,
                throttleBlock("off", null, new ArrayDeque<>(Arrays.asList(1000L)), 70000L, 0L, 1, 60000L));
        // 双活：冷却命中优先报冷却
        ArrayDeque<Long> bw = new ArrayDeque<>(Arrays.asList(1000L, 2000L, 3000L));
        check("both 冷却命中", "throttled_cooldown",
                throttleBlock("both", 1000L, bw, 1500L, 1000L, 3, 60000L));
        // 双活：冷却已过但窗口满，报窗口
        ArrayDeque<Long> bw2 = new ArrayDeque<>(Arrays.asList(1000L, 2000L, 3000L));
        check("both 窗口满", "throttled_window",
                throttleBlock("both", 1000L, bw2, 2500L, 1000L, 3, 60000L));
        // 存量 cooldown 带窗口默认值：窗口满同样拦（接受的行为增量，编辑器如实显示）
        ArrayDeque<Long> lw = new ArrayDeque<>(Arrays.asList(1000L));
        check("存量cooldown 窗口满拦", "throttled_window",
                throttleBlock("cooldown", null, lw, 30000L, 30000L, 1, 60000L));

        // 迁移：老 action 包单步 / 新 steps 优先 / throttle 回落
        check("迁移 老action包单步", 1, effectiveSteps(Collections.<String>emptyList(), true).size());
        check("迁移 新steps优先", 2,
                effectiveSteps(Arrays.asList("a", "b"), true).size());
        check("迁移 老cooldown回落", "cooldown", effectiveThrottle("off", 5000L));
        check("迁移 新throttle优先", "window", effectiveThrottle("window", 5000L));
        check("迁移 无节流", "off", effectiveThrottle("off", 0L));

        // 新脉冲：通知
        check("通知 包名+关键字命中", true, notificationMatches(
                Arrays.asList("com.tencent.mm"), "在吗", "com.tencent.mm", "张三", "在吗？回话"));
        check("通知 包名不对", false, notificationMatches(
                Arrays.asList("com.tencent.mm"), "", "com.xiaomi.sms", "10086", "话费"));
        check("通知 关键字大小写不敏感", true, notificationMatches(
                Collections.<String>emptyList(), "HELLO", "com.a.b", "say Hello", "x"));
        check("通知 无脉冲", false, notificationMatches(
                Collections.<String>emptyList(), "", null, "", ""));
        // 新脉冲：连接方向
        check("蓝牙 方向匹配", true, connectionMatches("CONNECTED", "CONNECTED"));
        check("蓝牙 不限方向", true, connectionMatches(null, "DISCONNECTED"));
        check("蓝牙 方向不对", false, connectionMatches("CONNECTED", "DISCONNECTED"));
        check("蓝牙 无脉冲", false, connectionMatches(null, null));
        // 新脉冲：定时
        check("定时 分钟命中", true, scheduledMatches(Arrays.asList(480, 1080), 480, true));
        check("定时 分钟不命中", false, scheduledMatches(Arrays.asList(480), 481, true));
        check("定时 非tick不命中", false, scheduledMatches(Arrays.asList(480), 480, false));
        check("定时 空表不命中", false, scheduledMatches(Collections.<Integer>emptyList(), 480, true));
        // ticker 星期门：Calendar.SUNDAY=1..SATURDAY=7
        check("定时 星期命中", true, scheduledDue(
                Arrays.asList(480), Arrays.asList(2, 3, 4, 5, 6), 480, 3));
        check("定时 星期不命中", false, scheduledDue(
                Arrays.asList(480), Arrays.asList(2, 3, 4, 5, 6), 480, 1));
        check("定时 空星期=每天", true, scheduledDue(
                Arrays.asList(480), Collections.<Integer>emptyList(), 480, 7));
        // 步骤路径：顶层 / 分支 then / 非法
        List<Object> demoSteps = new ArrayList<Object>(Arrays.<Object>asList(
                "action-0",
                Arrays.<Object>asList(
                        new ArrayList<Object>(Arrays.<Object>asList("then-0", "then-1")),
                        new ArrayList<Object>(Arrays.<Object>asList("else-0"))),
                "delay-2"));
        check("路径 顶层", "action-0", stepAt(demoSteps, "0"));
        check("路径 分支then", "then-1", stepAt(demoSteps, "1.then.1"));
        check("路径 分支else", "else-0", stepAt(demoSteps, "1.else.0"));
        check("路径 越界null", null, stepAt(demoSteps, "5"));
        check("路径 非法token null", null, stepAt(demoSteps, "1.foo.0"));
        check("路径 空null", null, stepAt(demoSteps, ""));
        // 触钮显隐 target
        List<String> allButtons = Arrays.asList("a", "b", "c");
        check("target ALL", Arrays.asList("a", "b", "c"),
                resolveButtons("ALL", allButtons, Arrays.asList("b")));
        check("target EXCEPT", Arrays.asList("a", "c"),
                resolveButtons("EXCEPT", allButtons, Arrays.asList("b")));
        check("target ONLY", Arrays.asList("b"),
                resolveButtons("ONLY", allButtons, Arrays.asList("b")));

        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("ALL PASS");
    }
}
