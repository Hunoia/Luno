package hunoia.luno.quicklaunch.query

import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.quicklaunch.model.AppInfo
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 手动性能基准：默认跳过，避免 CI 因机器差异抖动。
 *
 * 运行：
 * `LUNO_BENCHMARK=1 ./gradlew :app:testDebugUnitTest --tests '*QuickSearchBenchmarkTest'`
 *
 * 目标不是卡死具体毫秒数，而是给「预编译 plan / ASCII 快路径 / 排序键预存」一个
 * 可重复的观察入口，后续改匹配逻辑时能对比 p50/p95。
 */
class QuickSearchBenchmarkTest {

    @Test
    fun `sort 2000 apps`() {
        assumeTrue("set LUNO_BENCHMARK=1 to run", System.getenv("LUNO_BENCHMARK") == "1")

        val apps = (0 until 2_000).map { i ->
            AppInfo(
                packageName = "com.example.app$i",
                className = "MainActivity$i",
                label = "App $i",
            )
        }
        val settings = AppUsageStats()
        val queries = listOf("a", "app", "app1", "example", "1999")
            .map { q -> q.map { it.toString() } }

        // 预热索引 + JIT；正式测量前必须让 2000 条索引都进缓存。
        queries.forEach { AppSearch.sortApps(apps, settings, it) }

        val samples = ArrayList<Long>(queries.size * 5)
        repeat(5) {
            for (tokens in queries) {
                val start = System.nanoTime()
                AppSearch.sortApps(apps, settings, tokens)
                samples.add((System.nanoTime() - start) / 1_000_000)
            }
        }
        samples.sort()
        val p50 = samples[samples.size / 2]
        val p95 = samples[(samples.size * 95 / 100).coerceAtMost(samples.lastIndex)]
        println("QuickSearchBenchmark 2000 apps: p50=${p50}ms p95=${p95}ms samples=$samples")
        assertTrue("p95 too slow: ${p95}ms", p95 < 2_000)
    }
}
