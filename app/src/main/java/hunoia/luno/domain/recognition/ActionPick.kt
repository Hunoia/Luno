package hunoia.luno.domain.recognition

import android.content.Context
import hunoia.luno.picker.ActionSelect
import hunoia.luno.ui.actionselect.ActionSelectHostActivity
import java.util.UUID

/**
 * 「挑一个动作」的页面入口：识别器规则的「执行动作」输出用。
 *
 * 选择页是独立 Activity（`:service` 进程，供浮层用），没有 Activity Result 通道，
 * 选中结果走进程内 `Events` 的 `ActionPickedEvent`。这里只负责「生成 token + 起页 +
 * 判定这次事件是不是我这条」——页面用 `Events.SubscribeEvent` 订阅即可，不必自己管注销。
 */
object ActionPick {

    /**
     * 起选择页并返回本次挑选的 token；`null` = 起不来（调用方给个提示即可）。
     *
     * 同一个 token 只对这一次挑选有效：两个页面同时挑动作也不会互相收到对方的。
     */
    fun launch(context: Context): String? {
        val token = UUID.randomUUID().toString()
        val started = ActionSelectHostActivity.start(
            context,
            ActionSelect(pickActionToken = token),
        )
        return token.takeIf { started }
    }
}
