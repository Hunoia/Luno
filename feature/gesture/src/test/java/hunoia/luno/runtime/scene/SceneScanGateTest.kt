package hunoia.luno.runtime.scene

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 门禁的廉价拒绝路径：非情景事件与「没有任何规则」都必须在树遍历之前被挡掉。
 */
class SceneScanGateTest {

    private fun context(hasRules: Boolean = true) = SceneScanContext(
        enabled = true,
        locked = false,
        interactive = true,
        launcher = false,
        hasRules = hasRules,
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
}
