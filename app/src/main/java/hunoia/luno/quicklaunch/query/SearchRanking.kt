package hunoia.luno.quicklaunch.query

/**
 * 排序单元的共同视图：快速搜索的 [RankedHit] 与启动器应用的 [AppSearch] 排序
 * 都只依赖这五个字段，比较器集中在这里，避免两套排序键慢慢漂移。
 */
internal interface RankedResult {
    val tier: Int
    val offset: Int
    val usage: Double
    val title: String
    val titleLen: Int
}

/** 统一排序：tier → offset → 使用分 → 标题长度 → 字典序。 */
internal val RANK_COMPARATOR: Comparator<RankedResult> =
    compareByDescending<RankedResult> { it.tier }
        .thenBy { it.offset }
        .thenByDescending { it.usage }
        .thenBy { it.titleLen }
        .thenBy { it.title }
