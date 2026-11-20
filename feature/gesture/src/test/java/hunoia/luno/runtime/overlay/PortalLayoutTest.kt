package hunoia.luno.runtime.overlay

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.PortalReleaseMode
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.runtime.recognition.RecognizedItem
import hunoia.luno.ui.overlay.EdgeBarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 识屏幽灵卡的**卡片几何**与**松手结算**。
 *
 * 工具条本身的几何 / 命中已收口到 `:ui:design` 的 `EdgeBarLayout`（与剪贴板部件的拖拽
 * 动作条共用，门禁 `EdgeBarLayoutTest`）；这里只保留「卡片怎么让位」「松手怎么结算」
 * 与「动作集怎么派生」—— 三者仍要求渲染与判定同源。
 */
class PortalLayoutTest {

    private val density = 2f
    private val screenW = 1000f
    private val screenH = 2000f

    private fun bar(finger: Offset, count: Int = 3) = EdgeBarLayout.bar(
        finger = finger,
        screenWidth = screenW,
        screenHeight = screenH,
        density = density,
        count = count,
        insets = EdgeBarLayout.Insets(top = 60f, bottom = 40f),
    )

    // ---- 松手结算 ----

    @Test
    fun `release on a slot runs it`() {
        val b = bar(Offset(500f, 1940f))!!
        val point = Offset(b.slots[1].center.x, b.slots[1].center.y)
        val release = resolvePortalRelease(b, point, PortalReleaseMode.Cancel)
        assertTrue(release is PortalRelease.Act)
        assertEquals(1, (release as PortalRelease.Act).index)
    }

    @Test
    fun `release off the bar obeys the setting`() {
        val b = bar(Offset(500f, 1940f))!!
        val miss = Offset(500f, 700f)
        assertEquals(
            PortalRelease.Cancel,
            resolvePortalRelease(b, miss, PortalReleaseMode.Cancel),
        )
        assertEquals(
            PortalRelease.Stay,
            resolvePortalRelease(b, miss, PortalReleaseMode.StayInPicker),
        )
    }

    @Test
    fun `release without any bar obeys the setting`() {
        assertEquals(
            PortalRelease.Cancel,
            resolvePortalRelease(null, Offset(500f, 1000f), PortalReleaseMode.Cancel),
        )
        assertEquals(
            PortalRelease.Stay,
            resolvePortalRelease(null, Offset(500f, 1000f), PortalReleaseMode.StayInPicker),
        )
    }

    // ---- 卡片几何 ----

    @Test
    fun `card is centered on finger and uses given size`() {
        val c = PortalLayout.cardRect(Offset(500f, 900f), screenW, screenH, density, 400f, 150f)
        assertEquals(300f, c.left, 0.01f)
        assertEquals(825f, c.top, 0.01f)
        assertEquals(500f, c.center.x, 0.01f)
        assertEquals(900f, c.center.y, 0.01f)
    }

    @Test
    fun `card yields to the bar so it never covers the slot`() {
        val b = bar(Offset(500f, 1940f))!!
        val c = PortalLayout.cardRect(
            finger = Offset(500f, 1940f),
            screenWidth = screenW,
            screenHeight = screenH,
            density = density,
            cardWidthPx = 400f,
            cardHeightPx = 200f,
            bar = b,
        )
        assertTrue(c.bottom <= b.bounds.top)
    }

    @Test
    fun `card falls back to max size when unmeasured`() {
        val c = PortalLayout.cardRect(Offset(500f, 900f), screenW, screenH, density, 0f, 0f)
        assertEquals(PortalLayout.CardMaxWidthDp * density, c.width, 0.01f)
        assertEquals(PortalLayout.CardEstimatedHeightDp * density, c.height, 0.01f)
    }

    @Test
    fun `card is clamped inside the screen at edges`() {
        val c = PortalLayout.cardRect(Offset(990f, 1990f), screenW, screenH, density)
        assertTrue(c.right <= screenW)
        assertTrue(c.bottom <= screenH)
    }

    // ---- 工具条格子 ----

    @Test
    fun `text content exposes copy search share float without save`() {
        val actions = portalActionsFor(PortalContent(text = "hello"))
        assertEquals(
            listOf(PortalAction.Copy, PortalAction.Search, PortalAction.Share, PortalAction.Float),
            actions,
        )
    }

    @Test
    fun `image content exposes copy share float save without search`() {
        val actions = portalActionsFor(PortalContent(imageSize = 100 to 200))
        assertEquals(
            listOf(PortalAction.Copy, PortalAction.Share, PortalAction.Float, PortalAction.Save),
            actions,
        )
    }

    @Test
    fun `qr content exposes copy search share float plus open link`() {
        val actions = portalActionsFor(
            PortalContent(qrText = "https://example.com/a", url = "https://example.com/a"),
        )
        assertEquals(
            listOf(
                PortalAction.Copy, PortalAction.Search, PortalAction.Share, PortalAction.Float,
                PortalAction.OpenLink,
            ),
            actions,
        )
    }

    @Test
    fun `open link slot yields to an OPEN rule for the same url`() {
        val url = "https://example.com/a"
        val slots = portalSlotsFor(
            content = PortalContent(text = "see $url", url = url),
            recognized = listOf(openRule(url)),
        )
        // 规则槽在前；内容动作里的「打开链接」被同一个链接的规则槽吃掉，只剩一个。
        assertEquals(1, slots.count { it is PortalSlot.Suggestion })
        assertEquals(
            0,
            slots.count { it is PortalSlot.Action && it.action == PortalAction.OpenLink },
        )
        assertEquals("rule:open", slots.first().id)
    }

    /** 规则打开的是另一条链接：内容动作的「打开链接」（本次识别文本里的链接）仍要保留。 */
    @Test
    fun `open link slot stays when the rule does not cover it`() {
        val slots = portalSlotsFor(
            content = PortalContent(
                text = "see https://example.com/a",
                url = "https://example.com/a",
            ),
            recognized = listOf(openRule("https://other.example/b")),
        )
        assertEquals(
            1,
            slots.count { it is PortalSlot.Action && it.action == PortalAction.OpenLink },
        )
    }

    @Test
    fun `duplicate suggestion values collapse to one slot`() {
        val slots = portalSlotsFor(
            content = PortalContent(text = "code 1234"),
            recognized = listOf(
                copyRule("code-a", "1234"),
                copyRule("code-b", "1234"),
            ),
        )
        assertEquals(1, slots.count { it is PortalSlot.Suggestion })
    }

    @Test
    fun `suggestions precede content actions`() {
        val slots = portalSlotsFor(
            content = PortalContent(text = "code 1234"),
            recognized = listOf(copyRule("code", "1234")),
        )
        assertTrue(slots.first() is PortalSlot.Suggestion)
        assertTrue(slots.drop(1).all { it is PortalSlot.Action })
    }

    private fun copyRule(id: String, value: String) = RecognizedItem(
        ruleId = id,
        ruleName = "验证码",
        value = value,
        urls = emptyList(),
        output = RecognitionOutput.Builtin(RecognitionBuiltin.Copy),
    )

    private fun openRule(url: String) = RecognizedItem(
        ruleId = "open",
        ruleName = "链接",
        value = url,
        urls = listOf(url),
        output = RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink),
    )
}
