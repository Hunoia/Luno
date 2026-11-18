package hunoia.luno.runtime.overlay

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.PortalReleaseMode
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.runtime.recognition.RecognizedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 幽灵卡工具条的几何 / 命中共用同一份纯函数：渲染与松手判定一旦分叉，就会出现
 * “看着拖到「复制」上了、松手却没执行”。这里把坐标口径、四边取向、跟手夹取与松手结算钉死。
 */
class PortalLayoutTest {

    private val density = 2f
    private val screenW = 1000f
    private val screenH = 2000f
    private val insets = PortalLayout.Insets(top = 60f, bottom = 40f)

    private fun bar(
        finger: Offset,
        count: Int = 3,
        insets: PortalLayout.Insets = this.insets,
        slotMainPx: Float = 0f,
        anchorPx: Float? = null,
    ) = PortalLayout.bar(
        finger = finger,
        screenWidth = screenW,
        screenHeight = screenH,
        density = density,
        count = count,
        insets = insets,
        slotMainPx = slotMainPx,
        anchorAxis = anchorPx,
    )

    // ---- 边缘取向 ----

    @Test
    fun `finger near bottom shows the bottom bar`() {
        val b = bar(Offset(500f, 1940f))
        assertNotNull(b)
        assertEquals(PortalEdge.Bottom, b!!.edge)
        assertTrue(!b.isVertical)
        // 贴边距 = 12dp = 24px，inset 40px -> 条底边在 2000-40-24
        assertEquals(1936f, b.bounds.bottom, 0.01f)
        // 条厚 = 单格 48dp + 上下 padding 6dp
        val depth = (PortalLayout.SlotDp + PortalLayout.PaddingDp * 2) * density
        assertEquals(1936f - depth, b.bounds.top, 0.01f)
    }

    @Test
    fun `finger near left edge shows the vertical left bar`() {
        val b = bar(Offset(30f, 900f))
        assertNotNull(b)
        assertEquals(PortalEdge.Left, b!!.edge)
        assertTrue(b.isVertical)
        assertEquals(24f, b.bounds.left, 0.01f)
    }

    @Test
    fun `finger near top edge respects the top inset`() {
        val b = bar(Offset(500f, 100f))
        assertNotNull(b)
        assertEquals(PortalEdge.Top, b!!.edge)
        assertEquals(60f + 24f, b.bounds.top, 0.01f)
    }

    @Test
    fun `finger in the middle shows no bar`() {
        assertNull(bar(Offset(500f, 1000f)))
    }

    @Test
    fun `screen corners resolve deterministically`() {
        // 左下角：底边与左边都在带宽内，等距/不等距都按最近的一条；这里底边更近。
        assertEquals(PortalEdge.Bottom, bar(Offset(20f, 1950f))!!.edge)
        // 左上角：顶边更近（60+带宽内），左边也近 -> 顶边距离 40，左边 20 -> 选左边。
        assertEquals(PortalEdge.Left, bar(Offset(20f, 100f))!!.edge)
    }

    // ---- 沿边跟手 + 夹取 ----

    @Test
    fun `bar is anchored once and does not follow the finger`() {
        val anchor = Offset(500f, 1940f)
        val locked = bar(anchor, anchorPx = anchor.x)
        // 手指沿底边滑动：整条位置不变，只有指到哪一格会变（否则永远选不中同一个动作）。
        val slid = PortalLayout.bar(
            finger = Offset(800f, 1940f),
            screenWidth = screenW,
            screenHeight = screenH,
            density = density,
            count = 3,
            insets = insets,
            anchorAxis = anchor.x,
        )!!
        assertEquals(locked!!.bounds.left, slid.bounds.left, 0.01f)
        assertEquals(locked.bounds.width, slid.bounds.width, 0.01f)
    }

    @Test
    fun `vertical bar keeps its axis while the finger slides along the edge`() {
        val b = PortalLayout.bar(
            finger = Offset(30f, 1500f),
            screenWidth = screenW,
            screenHeight = screenH,
            density = density,
            count = 3,
            insets = insets,
            anchorAxis = 900f,
        )!!
        assertEquals(PortalEdge.Left, b.edge)
        // 锚点 900f 是条中心；条高 = 3 格 + 2 缝 + 2 padding。
        val total = b.bounds.height
        assertEquals(900f - total / 2f, b.bounds.top, 0.01f)
    }

    @Test
    fun `without an anchor the bar centers on the finger`() {
        val center = bar(Offset(500f, 1940f))!!
        val left = bar(Offset(200f, 1940f))!!
        assertTrue(left.bounds.left < center.bounds.left)
        assertEquals(center.bounds.width, left.bounds.width, 0.01f)
    }

    @Test
    fun `bar is clamped inside the safe area`() {
        val b = bar(Offset(0f, 1940f), count = 3)!!
        assertTrue(b.bounds.left >= 24f)
        val right = bar(Offset(1000f, 1940f), count = 3)!!
        assertTrue(right.bounds.right <= screenW - 24f)
    }

    @Test
    fun `too many slots shrink instead of overflowing`() {
        val count = 40
        val b = bar(Offset(500f, 1940f), count = count)!!
        assertTrue(b.bounds.left >= 0f)
        assertTrue(b.bounds.right <= screenW)
        assertEquals(count, b.slots.size)
        assertTrue(b.slots.last().right <= b.bounds.right + 0.01f)
    }

    @Test
    fun `slots are laid out inside the bar in order`() {
        val b = bar(Offset(500f, 1940f))!!
        assertEquals(3, b.slots.size)
        assertTrue(b.slots[0].right <= b.slots[1].left)
        assertTrue(b.slots[1].right <= b.slots[2].left)
    }

    @Test
    fun `zero count or invalid screen yields no bar`() {
        assertNull(bar(Offset(500f, 1940f), count = 0))
        assertNull(
            PortalLayout.bar(
                finger = Offset(0f, 0f),
                screenWidth = 0f,
                screenHeight = 0f,
                density = density,
                count = 3,
            ),
        )
    }

    // ---- 命中 ----

    @Test
    fun `hit returns the nearest slot along the bar axis`() {
        val b = bar(Offset(500f, 1940f))!!
        val midY = b.slots[1].center.y
        assertEquals(1, PortalLayout.hit(b, Offset(b.slots[1].center.x, midY)))
        assertEquals(0, PortalLayout.hit(b, Offset(b.slots[0].center.x, b.slots[0].center.y)))
        assertEquals(2, PortalLayout.hit(b, Offset(b.slots[2].center.x, b.slots[2].center.y)))
    }

    @Test
    fun `hit tolerates the gap between slots`() {
        val b = bar(Offset(500f, 1940f))!!
        val gapX = (b.slots[0].right + b.slots[1].left) / 2f
        // 缝隙属于整条范围，取最近的一格，不让用户必须压线。
        assertNotNull(PortalLayout.hit(b, Offset(gapX, b.slots[0].center.y)))
    }

    @Test
    fun `hit outside the bar is null`() {
        val b = bar(Offset(500f, 1940f))!!
        assertNull(PortalLayout.hit(b, Offset(500f, 600f)))
        assertNull(PortalLayout.hit(null, Offset(500f, 1940f)))
    }

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

    @Test
    fun `bar translation keeps slot geometry aligned with bounds`() {
        val b = bar(Offset(500f, 1940f))!!
        val moved = b.translated(-10f, -20f)
        assertEquals(b.bounds.left - 10f, moved.bounds.left, 0.01f)
        assertEquals(b.slots[0].top - 20f, moved.slots[0].top, 0.01f)
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
