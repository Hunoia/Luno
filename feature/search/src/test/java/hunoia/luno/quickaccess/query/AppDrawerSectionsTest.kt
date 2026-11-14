package hunoia.luno.quickaccess.query

import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.core.domain.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用抽屉分节的语义回归：分组键、`#` 位置、组内顺序必须等于快速启动器的排序口径，
 * 且常用区不许把没使用记录的应用冒充"常用"。
 */
class AppDrawerSectionsTest {

    private fun app(label: String, pkg: String = "pkg.$label", userId: Int = 0) =
        AppInfo(packageName = pkg, className = "$pkg.Main", label = label, userId = userId)

    private fun stats(vararg used: Pair<String, Int>): AppUsageStats {
        val count = used.associate { it.first to it.second.toLong() }
        // 全部记成刚刚启动：使用分只由次数决定，测试不受时钟影响。
        val recent = count.keys.associateWith { System.currentTimeMillis() }
        return AppUsageStats(launchCount = count, recentLaunchTime = recent)
    }

    @Test
    fun `groups by pinyin initial and puts other section last`() {
        val apps = listOf(
            app("微信", "pkg.wx"),
            app("Alipay", "pkg.alipay"),
            app("4K 壁纸", "pkg.wall"),
            app("淘宝", "pkg.tb"),
        )
        val layout = AppDrawerSections.layout(apps, AppUsageStats(), columns = 4)

        // 微信 → W（wei），淘宝 → T（tao），Alipay → A，数字开头 → #
        assertEquals(listOf('A', 'T', 'W', AppDrawerSections.OTHER), layout.sections.map { it.letter })
        assertEquals(listOf("4K 壁纸"), layout.sections.last().apps.map { it.label })
    }

    @Test
    fun `within a group apps follow usage score`() {
        val apps = listOf(app("百度"), app("Bilibili"), app("Baidu Map"))
        val layout = AppDrawerSections.layout(
            apps,
            stats("pkg.Bilibili" to 9, "pkg.Baidu Map" to 3),
            columns = 4,
        )
        val b = layout.sections.single { it.letter == 'B' }
        assertEquals(listOf("Bilibili", "Baidu Map", "百度"), b.apps.map { it.label })
    }

    @Test
    fun `top apps only take used ones and stay inside their section`() {
        val apps = listOf(app("Alpha"), app("Beta"), app("Gamma"), app("Delta"))
        val layout = AppDrawerSections.layout(
            apps,
            stats("pkg.Alpha" to 5, "pkg.Gamma" to 2),
            columns = 2,
        )
        // 列数 2 × 2 行 = 4，但只有两个应用有使用记录：不拿字典序补位。
        assertEquals(listOf("Alpha", "Gamma"), layout.topApps.map { it.label })
        assertEquals(4, layout.sections.sumOf { it.apps.size })
    }

    @Test
    fun `item indexes match the grid item order`() {
        val apps = listOf(app("Alpha"), app("Beta"), app("Charlie"))
        val layout = AppDrawerSections.layout(
            apps,
            stats("pkg.Alpha" to 5, "pkg.Beta" to 1),
            columns = 2,
        )
        // 滑杆条目：⭐ 在前，随后是实际存在的字母节。
        assertEquals(
            listOf(
                AppIndexEntry.Favorites,
                AppIndexEntry.Letter('A'),
                AppIndexEntry.Letter('B'),
                AppIndexEntry.Letter('C'),
            ),
            layout.indexEntries,
        )
        // item 序列：[⭐] [top x2] [组头A] [Alpha] [组头B] [Beta] [组头C] [Charlie]
        assertEquals(0, layout.headerItemIndex(AppIndexEntry.Favorites))
        assertEquals(3, layout.headerItemIndex(AppIndexEntry.Letter('A')))
        assertEquals(5, layout.headerItemIndex(AppIndexEntry.Letter('B')))
        assertEquals(7, layout.headerItemIndex(AppIndexEntry.Letter('C')))
        assertEquals(-1, layout.headerItemIndex(AppIndexEntry.Letter('Z')))

        assertEquals(AppIndexEntry.Favorites, layout.entryAt(0)) // ⭐ 组头
        assertEquals(AppIndexEntry.Favorites, layout.entryAt(2)) // 常用区的应用格也归属 ⭐
        assertEquals(AppIndexEntry.Letter('A'), layout.entryAt(3))
        assertEquals(AppIndexEntry.Letter('A'), layout.entryAt(4))
        assertEquals(AppIndexEntry.Letter('B'), layout.entryAt(5))
        assertEquals(AppIndexEntry.Letter('C'), layout.entryAt(7))
        assertEquals(AppIndexEntry.Letter('C'), layout.entryAt(8)) // 本节的应用格也归属本节
        assertTrue(layout.entryAt(9) == null)
    }

    @Test
    fun `no usage means no top section`() {
        val layout = AppDrawerSections.layout(listOf(app("Alpha"), app("Beta")), AppUsageStats(), columns = 4)
        assertTrue(layout.topApps.isEmpty())
        assertTrue(layout.indexEntries.none { it == AppIndexEntry.Favorites })
        assertEquals(0, layout.headerItemIndex(AppIndexEntry.Letter('A')))
        assertEquals(-1, layout.headerItemIndex(AppIndexEntry.Favorites))
    }

    @Test
    fun `clone stays right after its primary even after grouping`() {
        val primary = app("微信", "pkg.wx")
        val clone = app("微信", "pkg.wx", userId = 10)
        val layout = AppDrawerSections.layout(
            listOf(primary, app("Xmind", "pkg.xm"), clone),
            stats("pkg.xm" to 3),
            columns = 4,
        )
        val w = layout.sections.single { it.letter == 'W' }
        assertEquals(listOf(primary, clone), w.apps)
    }
}
