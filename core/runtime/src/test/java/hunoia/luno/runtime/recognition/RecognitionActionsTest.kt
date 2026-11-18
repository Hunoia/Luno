package hunoia.luno.runtime.recognition

import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.core.domain.StoredAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 输出 → 动作集的门禁：消费者（胶囊菜单 / 识屏建议槽）只展示 [actionsOf]，
 * 这里钉死每一种输出的派生结果，防止「胶囊有复制、识屏没有」的漂移。
 */
class RecognitionActionsTest {

    @Test
    fun `copy outputs derive exactly one copy action`() {
        assertEquals(
            listOf(RecognitionAction.Copy),
            actionsOf(RecognitionOutput.Builtin(RecognitionBuiltin.Copy), emptyList()),
        )
        assertEquals(
            listOf(RecognitionAction.Copy),
            actionsOf(RecognitionOutput.Builtin(RecognitionBuiltin.CopyAndClear), emptyList()),
        )
    }

    @Test
    fun `open link derives open plus copy and survives empty urls`() {
        // 有链接：打开在前（主语义）。
        assertEquals(
            listOf(RecognitionAction.Open, RecognitionAction.Copy),
            actionsOf(RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink), listOf("https://a.com")),
        )
        // 没链接（规则写错 / 文本规则给了 OPEN）：只给复制，执行口会降级。
        assertEquals(
            listOf(RecognitionAction.Copy),
            actionsOf(RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink), emptyList()),
        )
    }

    @Test
    fun `share derives share plus copy`() {
        assertEquals(
            listOf(RecognitionAction.Share, RecognitionAction.Copy),
            actionsOf(RecognitionOutput.Builtin(RecognitionBuiltin.Share), emptyList()),
        )
    }

    @Test
    fun `action output derives run copy share and open only with urls`() {
        val output = RecognitionOutput.Action(StoredAction(typeId = "noop"))
        assertEquals(
            listOf(RecognitionAction.Run, RecognitionAction.Copy, RecognitionAction.Share),
            actionsOf(output, emptyList()),
        )
        assertEquals(
            listOf(RecognitionAction.Run, RecognitionAction.Copy, RecognitionAction.Share, RecognitionAction.Open),
            actionsOf(output, listOf("https://a.com")),
        )
    }

    @Test
    fun `notify output derives notify plus copy`() {
        assertEquals(
            listOf(RecognitionAction.Notify, RecognitionAction.Copy),
            actionsOf(RecognitionOutput.Notify(), emptyList()),
        )
    }

    @Test
    fun `recognized item exposes the same action set`() {
        val item = RecognizedItem(
            ruleId = "r",
            ruleName = "",
            value = "123456",
            urls = emptyList(),
            output = RecognitionOutput.Builtin(RecognitionBuiltin.CopyAndClear),
        )
        assertEquals(actionsOf(item.output, item.urls), item.actions)
    }
}
