package hunoia.luno.core.domain

/** 执行历史的容量约束：数据层裁剪与展示查询共用。 */
object AutomationHistoryLimits {
    const val MAX_EVENTS = 100
    const val MAX_PER_RULE_QUERY = 20
    /** 跳过事件最多占一半：另一半点位留给真实触发记录。 */
    const val MAX_SKIP_EVENTS = 50
}
