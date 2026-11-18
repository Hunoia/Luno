package hunoia.luno.runtime.recognition

/**
 * 一条识别结果的去向。
 *
 * 分流的唯一依据是 [RecognizedItem.isDirect]（来自规则的 `display`），不在这里判输出类型：
 * 直执行档也能配「复制」「分享」「动作」，它们共用同一个执行口。
 */
sealed interface RecognitionRoute {

    /** 直接执行：调用方立刻交给 `RecognitionExecutor`，不进任何队列。 */
    data class Direct(val item: RecognizedItem) : RecognitionRoute

    /** 命中直执行档，但同一段值刚执行过：不重复跑，也不再显示。 */
    data class Suppressed(val item: RecognizedItem) : RecognitionRoute

    /** 进结果胶囊，等用户点。 */
    data class Capsule(val item: RecognizedItem) : RecognitionRoute
}

/** 一次分流的完整产出：去哪、以及被去重挡掉的都是什么。 */
class RoutedResults(routes: List<RecognitionRoute>) {

    val routes: List<RecognitionRoute> = routes

    val capsule: List<RecognizedItem> = routes.filterIsInstance<RecognitionRoute.Capsule>()
        .map { it.item }

    val direct: List<RecognizedItem> = routes.filterIsInstance<RecognitionRoute.Direct>()
        .map { it.item }

    val suppressed: List<RecognizedItem> = routes.filterIsInstance<RecognitionRoute.Suppressed>()
        .map { it.item }

    /** 这次输入是否命中了规则（含被去重挡掉的）：通知据此决定要不要落到通知胶囊。 */
    val matchedAnything: Boolean get() = routes.isNotEmpty()
}

/**
 * 识别结果 → 去向的分流器（纯逻辑 + 有界去重表）。
 *
 * 两件事必须在这里做，否则每个调用点都要各自实现一遍：
 *
 * 1. **直执行档的去重**：剪贴板轮询与情景重扫会把同一段文本反复喂进来。没有去重表，
 *    一条「命中即执行」的规则会在同一份剪贴板内容上反复跑动作。
 * 2. **胶囊档不参与去重**：胶囊由收件箱自己按 `id` 合并、按 TTL 清理，这里再拦一道会让
 *    用户滑掉卡片后连重弹的机会都没有。
 *
 * 去重键 = `直执行|规则 id|来源键|命中的值`：值变了就该再执行一次（用户又复制了一段新内容），
 * 值没变才是重复。
 */
class RecognitionDelivery(
    private val windowMs: () -> Long = { DefaultDirectWindowMs },
    private val maxEntries: Int = MaxEntries,
) {

    /** 直执行档的去重表：key → 上次落地时刻。有界，超出按插入序淘汰最旧。 */
    private val executedAt = LinkedHashMap<String, Long>(maxEntries)

    /**
     * 分流一批结果。[nowMs] 由调用方注入（时钟语义与调用点已有的一致）。
     *
     * 顺序保持识别器给出的顺序：同一次输入里「直执行」与「胶囊」混合时，
     * 落地的先后不影响胶囊里显示的顺序。
     */
    fun route(items: List<RecognizedItem>, sourceKey: String, nowMs: Long): RoutedResults {
        if (items.isEmpty()) return RoutedResults(emptyList())
        val out = ArrayList<RecognitionRoute>(items.size)
        for (item in items) {
            if (!item.isDirect) {
                out += RecognitionRoute.Capsule(item)
                continue
            }
            val key = directKey(item, sourceKey)
            if (isRecentlyExecuted(key, nowMs)) {
                out += RecognitionRoute.Suppressed(item)
                continue
            }
            markExecuted(key, nowMs)
            out += RecognitionRoute.Direct(item)
        }
        return RoutedResults(out)
    }

    /** 该 key 是否还在去重窗内；顺带清掉已过期的记录，避免表里长期挂着失效项。 */
    fun isRecentlyExecuted(key: String, nowMs: Long): Boolean {
        val at = executedAt[key] ?: return false
        if (nowMs - at < windowMs()) return true
        executedAt.remove(key)
        return false
    }

    /** 记一次落地（或一次尝试）。执行失败也要记：否则同一条失败结果会被无限重试。 */
    fun markExecuted(key: String, nowMs: Long) {
        executedAt.remove(key)
        executedAt[key] = nowMs
        while (executedAt.size > maxEntries) {
            val oldest = executedAt.keys.firstOrNull() ?: break
            executedAt.remove(oldest)
        }
    }

    fun clear() = executedAt.clear()

    companion object {
        /**
         * 直执行去重窗：与「滑掉后不重弹」同量级。
         *
         * 太短会在情景重扫里重复执行同一屏的动作，太长会让用户「隔一会儿又复制同一段」
         * 时没有反应。10 秒覆盖一次输入的生命周期足够。
         */
        const val DefaultDirectWindowMs = 10_000L

        /** 直执行去重表上限：超出按插入序淘汰最旧。 */
        const val MaxEntries = 16

        /** 去重键：同一规则 + 同一来源 + 同一段命中值 = 同一次落地。 */
        fun directKey(item: RecognizedItem, sourceKey: String): String =
            "direct|${item.ruleId}|$sourceKey|${item.value}"
    }
}
