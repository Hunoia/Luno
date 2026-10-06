package hunoia.luno.ui.automation

import hunoia.luno.config.model.AutomationRule

/**
 * 自动化草稿内存暂存。
 *
 * 进动作选择页不再把半成品写库——规则列表与引擎全程看不到未保存的草稿，
 * 返回编辑页后凭 id 取回，保存才落地、放弃即丢弃。
 * 进程死亡草稿丢失：可接受（本来就没保存）。
 */
object AutomationDraftHolder {
    private val drafts = mutableMapOf<String, AutomationRule>()

    @Synchronized
    fun stage(rule: AutomationRule) {
        drafts[rule.id] = rule
    }

    @Synchronized
    fun take(id: String): AutomationRule? = drafts[id]

    @Synchronized
    fun transform(id: String, transform: (AutomationRule) -> AutomationRule): Boolean {
        val current = drafts[id] ?: return false
        drafts[id] = transform(current)
        return true
    }

    @Synchronized
    fun remove(id: String) {
        drafts.remove(id)
    }
}
