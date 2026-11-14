package hunoia.luno.quickaccess.query

import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.core.domain.AppInfo

/**
 * 应用抽屉的一个字母分节。[letter] 为 [AppDrawerSections.OTHER] 时表示首字符取不到
 * A–Z 首字母（数字、符号、无拼音读音的文字），固定排在所有字母节之后。
 */
data class AppSection(val letter: Char, val apps: List<AppInfo>)

/** 字母滑杆的一个条目：⭐ 常用区（仅在有常用应用时出现）或某个字母节。 */
sealed interface AppIndexEntry {
    /** 顶部常用区（⭐）。 */
    data object Favorites : AppIndexEntry

    /** 一个字母节（`#` 表示非字母开头的应用）。 */
    data class Letter(val letter: Char) : AppIndexEntry
}

/**
 * 应用抽屉布局：顶部常用区 + 字母分节。
 *
 * **item 序列契约**（分节网格与字母滑杆共用，改顺序必须两处同步）：
 * `[⭐ 全宽组头] + [topApps 各占一格]`（常用区为空时这两段都不出现），
 * 之后每节是 `[全宽组头] + [该节应用各占一格]`。
 * 滑杆跳转与"当前字母"判定只认这套下标，不另存映射。
 */
data class AppDrawerLayout(
    val topApps: List<AppInfo>,
    val sections: List<AppSection>,
) {
    /**
     * 滑杆条目：⭐ 常用区在最前（仅当有常用应用），随后是实际存在的字母节——
     * 只列真实存在的条目，保证每次跳转都有落点。
     */
    val indexEntries: List<AppIndexEntry>
        get() = buildList {
            if (topApps.isNotEmpty()) add(AppIndexEntry.Favorites)
            sections.forEach { add(AppIndexEntry.Letter(it.letter)) }
        }

    /** 常用区占的 item 数：⭐ 组头 + 应用各占一格；无常用记录时整段不出现。 */
    private val topItemCount: Int get() = if (topApps.isEmpty()) 0 else topApps.size + 1

    /** 条目对应的组头在 LazyGrid 中的 item 下标；条目不存在返回 -1。 */
    fun headerItemIndex(entry: AppIndexEntry): Int = when (entry) {
        AppIndexEntry.Favorites -> if (topApps.isEmpty()) -1 else 0
        is AppIndexEntry.Letter -> {
            var index = topItemCount
            var found = -1
            for (section in sections) {
                if (section.letter == entry.letter) {
                    found = index
                    break
                }
                index += 1 + section.apps.size
            }
            found
        }
    }

    /** item 下标落在哪个条目；越界返回 null。常用区的应用格也归属 ⭐。 */
    fun entryAt(itemIndex: Int): AppIndexEntry? {
        if (itemIndex < 0) return null
        if (itemIndex < topItemCount) return AppIndexEntry.Favorites
        var index = topItemCount
        for (section in sections) {
            val end = index + 1 + section.apps.size
            if (itemIndex < end) return AppIndexEntry.Letter(section.letter)
            index = end
        }
        return null
    }
}

/**
 * 抽屉分节的唯一实现。分组不另起排序：先走 [AppSearch.sortApps]（空搜索词即
 * 使用分 → 标题长度 → 字典序，分身已跟在主应用后面），再在已排好的序列上做
 * **稳定切分**。因此组内顺序天然是使用频率序，分身也不会被拆到别组。
 */
object AppDrawerSections {

    /** 非字母节标识，与系统联系人 / 启动器的 `#` 习惯一致。 */
    const val OTHER: Char = '#'

    /**
     * @param columns 网格列数（与分节网格一致，决定常用区一行放几个）。
     * @param topRows 常用区行数；常用区最多 `columns * topRows` 个。
     *
     * 常用区只收有使用记录的应用：一条记录都没有时整段不出现，而不是拿字典序
     * 前几名冒充"常用"。常用区里的应用**仍留在自己的字母节内**——否则会出现
     * "明明装了这个应用，在字母节里找不到"。
     */
    fun layout(
        apps: List<AppInfo>,
        usageStats: AppUsageStats,
        columns: Int,
        topRows: Int = 2,
    ): AppDrawerLayout {
        val sorted = AppSearch.sortApps(apps, usageStats, emptyList())
        val cap = columns * topRows
        val usage = UsageScores(
            usageStats.launchCount,
            usageStats.recentLaunchTime,
            System.currentTimeMillis(),
        )
        val top = if (cap <= 0) {
            emptyList()
        } else {
            sorted.asSequence()
                .filter { usage.score(it.packageName) > 0.0 }
                .take(cap)
                .toList()
        }

        val groups = LinkedHashMap<Char, MutableList<AppInfo>>()
        for (app in sorted) {
            groups.getOrPut(initialOf(app.label)) { ArrayList() }.add(app)
        }
        // `#` 恒排最后：先按"是不是 OTHER"排，再按字母排，不依赖字符编码顺序。
        val sections = groups.entries
            .map { AppSection(it.key, it.value) }
            .sortedWith(compareBy({ it.letter == OTHER }, { it.letter }))

        // 分组键取 label 首字母：分身标题与主应用相同 → 首字母必然相同，
        // sortApps 做过的「分身紧跟主应用」在稳定切分后仍然成立。
        return AppDrawerLayout(top, sections)
    }

    /** 首字母：中文取拼音首字母（复用 [AppSearch] 索引），其余只认 A–Z。 */
    private fun initialOf(label: String): Char {
        val initial = AppSearch.buildIndex(label).initials.firstOrNull() ?: return OTHER
        val upper = initial.uppercaseChar()
        return if (upper in 'A'..'Z') upper else OTHER
    }
}
