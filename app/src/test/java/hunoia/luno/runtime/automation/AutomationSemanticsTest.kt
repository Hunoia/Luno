package hunoia.luno.runtime.automation

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.FireMode
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.RingerMode
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.config.model.RuleEffect
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.ThrottleKind
import hunoia.luno.config.model.ThrottlePolicy
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.action.model.resolveButtonTargetIds
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.NotificationPulse
import hunoia.luno.runtime.condition.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动化引擎语义回归（原 scripts/AutomationCheck.java 的 JVM 单测迁移版）：
 * 直接驱动真实 AutomationEngine / ConditionEvaluator / TimeWindow / RuleEffect.stepAt，
 * 不再维护 Java 转写副本。
 */
class AutomationSemanticsTest {

    private val fired = mutableListOf<String>()
    private val events = mutableListOf<AutomationEvent>()

    private fun engine() = AutomationEngine(
        onFireRule = { rule, _ -> fired.add(rule.id) },
        onEvents = { events.addAll(it) },
    )

    private fun ctx(
        nowMs: Long = 0L,
        packageName: String = "",
        notificationPosted: NotificationPulse? = null,
        bluetoothChanged: String? = null,
        screenEvent: ScreenEventType? = null,
        ringerMode: RingerMode = RingerMode.NORMAL,
    ) = ConditionContext(
        nowMs = nowMs,
        packageName = packageName,
        isLockScreen = false,
        isLauncher = false,
        isLandscape = false,
        isKeyboardInput = false,
        isCharging = false,
        batteryLevel = 100,
        minuteOfDay = 0,
        dayOfWeek = 1,
        networkType = NetworkType.WIFI,
        headphonesConnected = false,
        bluetoothAdapterOn = true,
        bluetoothAudioConnected = false,
        airplaneMode = false,
        ringerMode = ringerMode,
        volumePercent = emptyMap(),
        isMusicPlaying = false,
        isTorchOn = false,
        isDndOn = false,
        appChangedTo = null,
        screenEvent = screenEvent,
        chargingChangedTo = null,
        volumeChanged = null,
        notificationPosted = notificationPosted,
        bluetoothChanged = bluetoothChanged?.let { hunoia.luno.config.model.ConnectionDirection.valueOf(it) },
    )

    private fun appRule(id: String = "r1", fireOn: FireMode = FireMode.ENTER, throttle: ThrottlePolicy = ThrottlePolicy()) =
        AutomationRule(
            id = id,
            condition = WhenGroup(items = listOf(ConditionItem(type = ConditionType.FOREGROUND_APP, packageNames = listOf("com.x")))),
            effect = RuleEffect(steps = listOf(RuleStep(action = StoredAction("noop")))),
            fireOn = fireOn,
            throttle = throttle,
        )

    private fun evalOnce(engine: AutomationEngine, rule: AutomationRule, ctx: ConditionContext) {
        fired.clear(); events.clear()
        engine.evaluate(listOf(rule), ctx)
    }

    // ======== 边沿：ENTER / EXIT / BOTH / 脉冲 ========

    @Test
    fun `ENTER fires on rising edge only`() {
        val engine = engine()
        val rule = appRule()
        evalOnce(engine, rule, ctx(packageName = "com.x"))
        assertEquals(emptyList<String>(), fired) // 首评 prev=hit，不产生边沿
        evalOnce(engine, rule, ctx(packageName = "com.x"))
        assertEquals(emptyList<String>(), fired) // 保持不重触发
        evalOnce(engine, rule, ctx(packageName = "com.y"))
        assertEquals(emptyList<String>(), fired)
        evalOnce(engine, rule, ctx(packageName = "com.x"))
        assertEquals(listOf("r1"), fired) // 上升沿
    }

    @Test
    fun `EXIT fires on falling edge only`() {
        val engine = engine()
        val rule = appRule(fireOn = FireMode.EXIT)
        evalOnce(engine, rule, ctx(packageName = "com.x"))
        assertEquals(emptyList<String>(), fired) // 首评不产生下降沿
        evalOnce(engine, rule, ctx(packageName = "com.y"))
        assertEquals(listOf("r1"), fired) // 下降沿
        evalOnce(engine, rule, ctx(packageName = "com.y"))
        assertEquals(emptyList<String>(), fired)
    }

    @Test
    fun `BOTH fires on both edges`() {
        val engine = engine()
        val rule = appRule(fireOn = FireMode.BOTH)
        evalOnce(engine, rule, ctx(packageName = "com.x"))
        assertEquals(emptyList<String>(), fired)
        evalOnce(engine, rule, ctx(packageName = "com.y"))
        assertEquals(listOf("r1"), fired) // 下降沿
        evalOnce(engine, rule, ctx(packageName = "com.x"))
        assertEquals(listOf("r1"), fired) // 上升沿
    }

    @Test
    fun `pulse fires on first eval even for ENTER`() {
        val engine = engine()
        val rule = AutomationRule(
            id = "r1",
            condition = WhenGroup(items = listOf(ConditionItem(type = ConditionType.NOTIFICATION_POSTED))),
            effect = RuleEffect(steps = listOf(RuleStep(action = StoredAction("noop")))),
            fireOn = FireMode.ENTER,
        )
        evalOnce(engine, rule, ctx(notificationPosted = NotificationPulse("com.a", "T", "x")))
        assertEquals(listOf("r1"), fired) // 脉冲命中即边沿
        // 同一轮再来一次（无脉冲）：pulseEdge 无，且 prev=true → 不重触发
        evalOnce(engine, rule, ctx(notificationPosted = null))
        assertEquals(emptyList<String>(), fired)
    }

    @Test
    fun `pure event tree normalizes fireOn to ENTER`() {
        val engine = engine()
        // 纯事件树（无状态叶）+ fireOn BOTH：统一按 ENTER 处理
        val rule = AutomationRule(
            id = "r1",
            condition = WhenGroup(items = listOf(ConditionItem(type = ConditionType.NOTIFICATION_POSTED))),
            effect = RuleEffect(steps = listOf(RuleStep(action = StoredAction("noop")))),
            fireOn = FireMode.BOTH,
        )
        evalOnce(engine, rule, ctx(notificationPosted = NotificationPulse("com.a", "T", "x")))
        assertEquals(listOf("r1"), fired)
        // EXIT 归一为 ENTER 后，无脉冲的后续求值不触发（若按 EXIT 会因下降沿误触发）
        evalOnce(engine, rule, ctx())
        assertEquals(emptyList<String>(), fired)
    }

    // ======== 脉冲匹配细节（通知）========

    @Test
    fun `notification package filter and keyword are case-insensitive`() {
        val engine = engine()
        fun notifyRule(pkgs: List<String>, kw: String) = AutomationRule(
            id = "r1",
            condition = WhenGroup(items = listOf(ConditionItem(type = ConditionType.NOTIFICATION_POSTED, notifyPackages = pkgs, notifyKeyword = kw))),
            effect = RuleEffect(steps = listOf(RuleStep(action = StoredAction("noop")))),
        )
        evalOnce(engine, notifyRule(listOf("com.a"), "news"), ctx(notificationPosted = NotificationPulse("com.a", "Breaking NEWS", "x")))
        assertEquals(listOf("r1"), fired)
        evalOnce(engine, notifyRule(listOf("com.b"), "news"), ctx(notificationPosted = NotificationPulse("com.a", "news", "x")))
        assertEquals(emptyList<String>(), fired) // 包名不对
        evalOnce(engine, notifyRule(emptyList(), ""), ctx(notificationPosted = NotificationPulse("com.a", "t", "x")))
        assertEquals(listOf("r1"), fired) // 不限包名 + 空关键字
        evalOnce(engine, notifyRule(emptyList(), "news"), ctx(notificationPosted = null))
        assertEquals(emptyList<String>(), fired) // 无脉冲
    }

    @Test
    fun `bluetooth changed direction filter`() {
        val engine = engine()
        fun btRule(expected: hunoia.luno.config.model.ConnectionDirection?) = AutomationRule(
            id = "r1",
            condition = WhenGroup(items = listOf(ConditionItem(type = ConditionType.BLUETOOTH_CHANGED, connectionDirection = expected))),
            effect = RuleEffect(steps = listOf(RuleStep(action = StoredAction("noop")))),
        )
        evalOnce(engine, btRule(hunoia.luno.config.model.ConnectionDirection.CONNECTED), ctx(bluetoothChanged = "CONNECTED"))
        assertEquals(listOf("r1"), fired)
        evalOnce(engine, btRule(hunoia.luno.config.model.ConnectionDirection.CONNECTED), ctx(bluetoothChanged = "DISCONNECTED"))
        assertEquals(emptyList<String>(), fired)
        evalOnce(engine, btRule(null), ctx(bluetoothChanged = "DISCONNECTED"))
        assertEquals(listOf("r1"), fired) // 不限方向
        evalOnce(engine, btRule(null), ctx())
        assertEquals(emptyList<String>(), fired) // 无脉冲
    }

    // ======== 触发前预检 ========

    @Test
    fun `preflight skip does not consume throttle window`() {
        var blocked = true
        val engine = AutomationEngine(
            onFireRule = { rule, _ -> fired.add(rule.id) },
            onEvents = { events.addAll(it) },
            preflightBlockReason = { if (blocked) SkipReason.PERMISSION_MISSING else null },
        )
        val rule = AutomationRule(
            id = "r1",
            condition = WhenGroup(items = listOf(ConditionItem(type = ConditionType.NOTIFICATION_POSTED))),
            effect = RuleEffect(steps = listOf(RuleStep(action = StoredAction("noop")))),
            throttle = ThrottlePolicy(kind = ThrottleKind.COOLDOWN, cooldownMs = 60_000L),
        )
        fired.clear(); events.clear()
        engine.evaluate(listOf(rule), ctx(nowMs = 0, notificationPosted = NotificationPulse("com.a", "T", "x")))
        assertEquals(emptyList<String>(), fired)
        assertEquals("permission_missing", events.last().reason)

        blocked = false
        engine.evaluate(listOf(rule), ctx(nowMs = 1_000, notificationPosted = NotificationPulse("com.a", "T", "y")))
        assertEquals(listOf("r1"), fired) // 第一次预检失败没有写下 cooldown 时间戳
    }

    // ======== 节流：冷却 / 窗口 / 双活 ========

    @Test
    fun `cooldown throttles within window`() {
        val engine = engine()
        val rule = appRule(throttle = ThrottlePolicy(kind = ThrottleKind.COOLDOWN, cooldownMs = 1000))
        evalOnce(engine, rule, ctx(packageName = "com.y"))
        assertEquals(emptyList<String>(), fired) // 首评无边沿
        evalOnce(engine, rule, ctx(packageName = "com.x"))
        assertEquals(listOf("r1"), fired) // t=0 触发
        evalOnce(engine, rule, ctx(packageName = "com.y"))
        assertEquals(emptyList<String>(), fired)
        evalOnce(engine, rule, ctx(packageName = "com.x"))
        assertEquals(emptyList<String>(), fired) // 仍在冷却（第二次上升沿 t 未推进）
        assertEquals("throttled_cooldown", events.last().reason)
    }

    @Test
    fun `cooldown releases after window`() {
        val engine = engine()
        val rule = appRule(throttle = ThrottlePolicy(kind = ThrottleKind.COOLDOWN, cooldownMs = 1000))
        // t=0 首评不触发边沿但 lastCondition 已记录；t=5000 重新进入触发
        evalOnce(engine, rule, ctx(nowMs = 0, packageName = "com.x"))
        evalOnce(engine, rule, ctx(nowMs = 5000, packageName = "com.y"))
        evalOnce(engine, rule, ctx(nowMs = 6000, packageName = "com.x"))
        assertEquals(listOf("r1"), fired)
    }

    @Test
    fun `window throttles after max count in window`() {
        val engine = engine()
        val rule = appRule(throttle = ThrottlePolicy(kind = ThrottleKind.WINDOW, maxCount = 3, windowMs = 60000))
        var t = 1000L
        // ENTER 规则只认上升沿（y→x）：x y x y x y x y 触发 3 次，窗口记满
        var totalFired = 0
        for (i in 0 until 8) {
            evalOnce(engine, rule, ctx(nowMs = t, packageName = if (i % 2 == 0) "com.x" else "com.y"))
            totalFired += fired.size
            t += 500
        }
        assertEquals(3, totalFired)
        // 第 4 次上升沿：窗口未过期 → 拦
        evalOnce(engine, rule, ctx(nowMs = t, packageName = "com.x"))
        assertEquals(emptyList<String>(), fired)
        assertEquals("throttled_window", events.last().reason)
        // 窗口过期滑出 → 放行
        evalOnce(engine, rule, ctx(nowMs = t + 61000, packageName = "com.y"))
        evalOnce(engine, rule, ctx(nowMs = t + 61500, packageName = "com.x"))
        assertEquals(listOf("r1"), fired)
    }

    // ======== 时间窗（TimeWindow.matches）========

    @Test
    fun `time window weekday and cross-midnight semantics`() {
        val weekdays = setOf(2, 3, 4, 5, 6) // 周一..周五（Calendar 常量）
        assertTrue(TimeWindow(420, 540, weekdays).matches(480, 3)) // 工作日命中
        assertTrue(!TimeWindow(420, 540, weekdays).matches(480, 1)) // 周日（Calendar SUNDAY=1）不命中
        assertTrue(TimeWindow(420, 540).matches(480, 7)) // 空星期=每天
        assertTrue(TimeWindow(1380, 60).matches(30, 7)) // 跨午夜：1380..1440/0..60
        assertTrue(!TimeWindow(1380, 60).matches(600, 7))
    }

    @Test
    fun `ringer mode equality is three-state`() {
        // 语义锁：RingerMode 三态与 ctx.ringerMode 等值比较（老 silent 回落迁移已并入存储值）
        val engine = engine()
        val rule = AutomationRule(
            id = "r1",
            condition = WhenGroup(items = listOf(ConditionItem(type = ConditionType.RINGER_SILENT, ringerMode = RingerMode.VIBRATE))),
            effect = RuleEffect(steps = listOf(RuleStep(action = StoredAction("noop")))),
        )
        evalOnce(engine, rule, ctx(ringerMode = RingerMode.SILENT))
        assertEquals(emptyList<String>(), fired)
        evalOnce(engine, rule, ctx(ringerMode = RingerMode.VIBRATE))
        assertEquals(listOf("r1"), fired)
    }

    // ======== 步骤路径（RuleEffect.stepAt）========

    private val demoEffect = RuleEffect(
        steps = listOf(
            RuleStep(kind = RuleStepKind.ACTION, action = StoredAction("a0")),
            RuleStep(
                kind = RuleStepKind.IF,
                thenSteps = listOf(
                    RuleStep(kind = RuleStepKind.ACTION, action = StoredAction("then0")),
                    RuleStep(kind = RuleStepKind.ACTION, action = StoredAction("then1")),
                ),
                elseSteps = listOf(RuleStep(kind = RuleStepKind.ACTION, action = StoredAction("else0"))),
            ),
            RuleStep(kind = RuleStepKind.DELAY, delayMs = 500),
        ),
    )

    @Test
    fun `stepAt resolves top-level branch and rejects invalid paths`() {
        assertEquals("a0", demoEffect.stepAt("0")?.action?.typeId)
        assertEquals("then1", demoEffect.stepAt("1.then.1")?.action?.typeId)
        assertEquals("else0", demoEffect.stepAt("1.else.0")?.action?.typeId)
        assertNull(demoEffect.stepAt("5")) // 越界
        assertNull(demoEffect.stepAt("1.foo.0")) // 非法 token
        assertNull(demoEffect.stepAt("")) // 空
    }

    // ======== 触钮显隐 target 解析（resolveButtonTargetIds）========

    @Test
    fun `button target ALL EXCEPT ONLY`() {
        val all = listOf("a", "b", "c")
        assertEquals(setOf("a", "b", "c"), resolveButtonTargetIds(ButtonTarget.ALL, all, listOf("b")))
        assertEquals(setOf("a", "c"), resolveButtonTargetIds(ButtonTarget.EXCEPT, all, listOf("b")))
        assertEquals(setOf("b"), resolveButtonTargetIds(ButtonTarget.ONLY, all, listOf("b")))
    }
}
