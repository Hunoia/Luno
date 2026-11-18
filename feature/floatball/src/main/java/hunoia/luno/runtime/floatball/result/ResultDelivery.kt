package hunoia.luno.runtime.floatball.result

import hunoia.luno.core.LunoLog as Log
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.runtime.floatball.inbox.ResultItem
import hunoia.luno.runtime.recognition.RecognitionDelivery
import hunoia.luno.runtime.recognition.RecognitionExecutor
import hunoia.luno.runtime.recognition.RecognitionRoute
import hunoia.luno.runtime.recognition.RecognizedItem

/**
 * 一次分流的产出。
 *
 * [capsule] 要进结果胶囊；[executed] 是直执行档已经落地的项（服务据此弹提示）；
 * [suppressed] 是命中但被直执行去重挡住的项（不提示、不显示：它刚刚已经执行过）。
 */
internal data class DeliveredResults(
    val capsule: List<ResultItem>,
    val executed: List<RecognizedItem>,
    val suppressed: List<RecognizedItem>,
) {
    /** 这次输入是否命中了规则（含被去重挡掉的）：决定通知要不要落到通知胶囊。 */
    val matchedAnything: Boolean
        get() = executed.isNotEmpty() || suppressed.isNotEmpty() || capsule.isNotEmpty()

    companion object {
        val Empty = DeliveredResults(emptyList(), emptyList(), emptyList())
    }
}

/**
 * 识别结果 → 结果胶囊 + 直执行。
 *
 * 这里只做编排：问 [RecognitionDelivery] 该走哪条路，直执行档交给 [RecognitionExecutor]
 * （与胶囊点击、识屏工具条同一个执行口）。规则判定在 `Recognizer`，结果投影在
 * [ResultFactory]，本函数不碰任何一条。
 */
internal suspend fun deliverResults(
    items: List<RecognizedItem>,
    sourceKey: String,
    nowMs: Long,
    settings: RecognitionSettings,
    delivery: RecognitionDelivery,
    executor: RecognitionExecutor,
    sceneKey: String? = null,
): DeliveredResults {
    if (items.isEmpty()) return DeliveredResults.Empty
    val routed = delivery.route(items, sourceKey, nowMs)
    val capsule = routed.capsule.map { item ->
        ResultFactory.toResultItem(item, sourceKey, nowMs, sceneKey)
    }
    val executed = ArrayList<RecognizedItem>(routed.direct.size)
    for (item in routed.direct) {
        val ok = runCatching {
            executor.execute(
                item = item,
                settings = settings,
                label = item.ruleName.ifBlank { null },
                idempotencyKey = "direct:${item.ruleId}:$sourceKey",
            )
        }.getOrElse { error ->
            Log.w(LOG_TAG, "direct execute failed rule=${item.ruleId}", error)
            false
        }
        if (ok) executed += item
    }
    return DeliveredResults(capsule, executed, routed.suppressed)
}
