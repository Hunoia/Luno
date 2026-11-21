package hunoia.luno.link

import android.content.Context
import hunoia.luno.config.model.RecognitionDefaults
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSettings

/**
 * 首装规则 seed：把 [RecognitionDefaults.seedRules]（全部文本规则 + 通知变体）与
 * [LinkPresets.seedable]（按已装应用生成的链接规则）一次性合并进规则表，落库后就是
 * 普通用户规则，可删可改。
 *
 * 「一次性」由 [RecognitionSettings.rulesSeeded] 标记保证——没有它，「用户故意删光
 * 预设规则」和「从未 seed」无法区分，删掉的规则会被复活。PM 扫描（几十 ms）只在
 * 未 seed 时做一次；已 seed 的启动路径只有一个 DataStore 读。
 */
suspend fun seedRecognitionRulesIfNeeded(
    context: Context,
    current: suspend () -> RecognitionSettings,
    update: suspend ((RecognitionSettings) -> RecognitionSettings) -> Unit,
) {
    if (current().rulesSeeded) return
    // 候选在 update 外算好（PM 查询不进 transform，避免冲突重试时重复扫包）；
    // 真正的判重与写入在 transform 内对着最新规则做，天然幂等。
    val candidates = RecognitionDefaults.seedRules + LinkPresets.seedable(context)
    update { settings ->
        if (settings.rulesSeeded) {
            settings
        } else {
            settings.copy(
                rules = settings.rules + missingSeedRules(settings.rules, candidates),
                rulesSeeded = true,
            )
        }
    }
}

/**
 * 待补的 seed 规则：与旧「添加常用规则」同一判重口径——
 * 文本 / 链接按 pattern（用户已有同匹配串的规则就不再塞），通知变体按名字
 * （它与普通规则同正则不同输出，pattern 判重会把它冤杀）。
 */
private fun missingSeedRules(
    existing: List<RecognitionRule>,
    candidates: List<RecognitionRule>,
): List<RecognitionRule> {
    val usedPatterns = existing
        .map { it.match.pattern.trim().lowercase() }
        .filterTo(mutableSetOf()) { it.isNotEmpty() }
    val usedNames = existing.mapTo(mutableSetOf()) { it.name }
    return candidates.filter { candidate ->
        if (candidate.output is RecognitionOutput.Notify) {
            usedNames.add(candidate.name)
        } else {
            usedPatterns.add(candidate.match.pattern.trim().lowercase())
        }
    }
}
