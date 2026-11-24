package hunoia.luno.runtime

import hunoia.luno.core.domain.libraryEntryId
import hunoia.luno.execute.ExecuteSettlement
import hunoia.luno.runtime.floatball.FloatBallCommand

/**
 * 动作落定 → 动作动效的唯一转换点。
 *
 * 只认可执行成功（失败 / 需授权不播，避免把失败演成成功）；动效本身由悬浮球服务播放：
 * - 原始请求是动作库引用（`internal.libraryRef`）→ 只查条目绑定，动作类型不回退；
 * - 其余动作 → 按 `typeId` 查类型绑定（音量 +/- 这类变体共用一条）。
 *
 * 服务未运行 / 未绑定 / 预设缺失时 `playActionAlert` 为空或内部静默跳过——
 * 动作照常算成功，附加反馈不改变落定语义。
 */
internal object ActionAlertTrigger {

    fun observe(settlement: ExecuteSettlement) {
        if (!settlement.succeeded) return
        val stored = settlement.request.stored
        val entryId = stored.libraryEntryId()
        val typeId = if (entryId == null) stored.typeId else null
        FloatBallCommand.playActionAlert?.invoke(typeId, entryId)
    }
}