package hunoia.luno.quicklaunch.query

/**
 * 排序单元的共同视图：快速搜索的 [RankedHit] 与启动器应用的 [AppSearch] 排序
 * 都只依赖这五个字段，比较器集中在这里，避免两套排序键慢慢漂移。
 *
 * [sortTitle] 是排序用的归一化串（应用侧是索引 raw，搜索侧是展示标题），
 * 不是展示字段，别拿它渲染。
 */
interface RankedResult {
    val tier: Int
    val offset: Int
    val usage: Double
    val sortTitle: String
    val sortTitleLen: Int
}

/** 统一排序：tier → offset → 使用分 → 标题长度 → 字典序。 */
val RANK_COMPARATOR: Comparator<RankedResult> =
    compareByDescending<RankedResult> { it.tier }
        .thenBy { it.offset }
        .thenByDescending { it.usage }
        .thenBy { it.sortTitleLen }
        .thenBy { it.sortTitle }
