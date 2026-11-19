package hunoia.luno.runtime.scene

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 门禁的廉价拒绝路径：非情景事件与「没有任何规则」都必须在树遍历之前被挡掉。
 */
class SceneScanGateTest {

    private fun context(
        hasRules: Boolean = true,
        blocklist: Set<String> = emptySet(),
        allowlist: Boolean = false,
    ) = SceneScanContext(
        enabled = true,
        locked = false,
        interactive = true,
        launcher = false,
        hasRules = hasRules,
        sceneBlocklist = blocklist,
        sceneAllowlist = allowlist,
    )

    @Test
    fun `no rules rejects before scanning`() {
        val decision = SceneScanGate.decide(
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            context = context(hasRules = false),
            eventPackage = "com.example",
            ownPackage = "hunoia.luno",
        )
        assertEquals(SceneScanDecision.NoRules, decision)
    }

    @Test
    fun `focus and selection events are not scene events`() {
        assertEquals(
            SceneScanDecision.NotSceneEvent,
            SceneScanGate.decide(
                eventType = AccessibilityEvent.TYPE_VIEW_FOCUSED,
                context = context(),
                eventPackage = "com.example",
                ownPackage = "hunoia.luno",
            ),
        )
        assertEquals(
            SceneScanDecision.NotSceneEvent,
            SceneScanGate.decide(
                eventType = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                context = context(),
                eventPackage = "com.example",
                ownPackage = "hunoia.luno",
            ),
        )
    }

    @Test
    fun `normal content change is accepted`() {
        assertEquals(
            SceneScanDecision.Accepted,
            SceneScanGate.decide(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                context = context(),
                eventPackage = "com.example",
                ownPackage = "hunoia.luno",
            ),
        )
    }

    @Test
    fun `system ui packages and vendor plugins are rejected`() {
        // 原生与各厂商 SystemUI 及其插件：它们没有 TYPE_APPLICATION 窗口，事件却密，放行会
        // 抢占 expectedPackage 导致抓到前台应用后包名比对 drop（「经常不识别」）。
        listOf(
            "com.android.systemui",
            "com.miui.systemui",
            "miui.systemui.plugin",
            "com.oplus.systemui",
            "com.vivo.systemui",
        ).forEach { pkg ->
            assertEquals(
                "应拒绝系统 UI 包 $pkg",
                SceneScanDecision.SystemUi,
                SceneScanGate.decide(
                    eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    context = context(),
                    eventPackage = pkg,
                    ownPackage = "hunoia.luno",
                ),
            )
        }
    }

    @Test
    fun `blocklist rejects listed apps`() {
        assertEquals(
            SceneScanDecision.BlockedList,
            SceneScanGate.decide(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                context = context(blocklist = setOf("com.blocked")),
                eventPackage = "com.blocked",
                ownPackage = "hunoia.luno",
            ),
        )
    }

    @Test
    fun `blocklist passes other apps`() {
        assertEquals(
            SceneScanDecision.Accepted,
            SceneScanGate.decide(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                context = context(blocklist = setOf("com.blocked")),
                eventPackage = "com.other",
                ownPackage = "hunoia.luno",
            ),
        )
    }

    @Test
    fun `empty allowlist passes everything`() {
        assertEquals(
            SceneScanDecision.Accepted,
            SceneScanGate.decide(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                context = context(allowlist = true),
                eventPackage = "com.any",
                ownPackage = "hunoia.luno",
            ),
        )
    }

    @Test
    fun `allowlist passes listed apps`() {
        assertEquals(
            SceneScanDecision.Accepted,
            SceneScanGate.decide(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                context = context(blocklist = setOf("com.allowed"), allowlist = true),
                eventPackage = "com.allowed",
                ownPackage = "hunoia.luno",
            ),
        )
    }

    @Test
    fun `allowlist rejects unlisted apps`() {
        assertEquals(
            SceneScanDecision.BlockedList,
            SceneScanGate.decide(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                context = context(blocklist = setOf("com.allowed"), allowlist = true),
                eventPackage = "com.other",
                ownPackage = "hunoia.luno",
            ),
        )
    }
}
