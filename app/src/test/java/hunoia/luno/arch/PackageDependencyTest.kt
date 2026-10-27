package hunoia.luno.arch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 包级依赖门禁（Phase 0）。
 *
 * 只约束"重构目标里不允许的边"，不试图一次描述完整目标 allow-list。
 * [PENDING] 是当前已知、尚未拆掉的历史边：新增违规会让测试失败；修掉一条后
 * 必须同步删掉对应条目，否则第二条用例失败。这样清单只会单调收缩。
 *
 * 模块物理拆分后，这条测试退化为 `:app` 内的兜底；Gradle 依赖声明成为第一道门。
 */
class PackageDependencyTest {

    /** 目标架构中明确禁止的边（双向环 + 单向跨层）。 */
    private val forbidden: Set<Edge> = buildSet {
        addCycle("action", "runtime")
        addCycle("config", "action")
        addCycle("ui", "quicklaunch")
        addCycle("ui", "gridpanel")
        addCycle("runtime", "ui")
        addCycle("domain", "runtime")
        addCycle("service", "ui")
        addCycle("action", "quicklaunch")
        add(Edge("runtime", "gridpanel"))
        add(Edge("link", "quicklaunch"))
        add(Edge("config", "quicklaunch"))
    }

    /** 当前仍存在、待后续阶段拆除的历史边。只允许删，不允许加。 */
    private val pending: Set<Edge> = setOf(
        Edge("action", "runtime"),
        Edge("runtime", "action"),
        Edge("action", "config"),
        Edge("ui", "quicklaunch"),
        Edge("ui", "gridpanel"),
        Edge("gridpanel", "ui"),
        Edge("runtime", "ui"),
        Edge("ui", "runtime"),
        Edge("domain", "runtime"),
        Edge("runtime", "domain"),
        Edge("service", "ui"),
        Edge("ui", "service"),
        Edge("action", "quicklaunch"),
    )

    @Test
    fun `no new forbidden package edges`() {
        val actual = collectEdges()
        val unexpected = (actual intersect forbidden) - pending
        assertTrue(
            "新增了目标架构禁止的包依赖（请改依赖方向，或先更新方案）：\n" +
                unexpected.sorted().joinToString("\n") { "  ${it.from} -> ${it.to}" },
            unexpected.isEmpty(),
        )
    }

    @Test
    fun `pending edges are up to date`() {
        val actual = collectEdges()
        val stale = pending - actual
        assertEquals(
            "以下历史边已不存在，请从 PackageDependencyTest.pending 中删除：\n" +
                stale.sorted().joinToString("\n") { "  ${it.from} -> ${it.to}" },
            emptySet<Edge>(),
            stale,
        )
    }

    private fun collectEdges(): Set<Edge> {
        val root = File("src/main/java/hunoia/luno")
        assertTrue("找不到源码根目录：${root.absolutePath}", root.isDirectory)
        val edges = mutableSetOf<Edge>()
        root.listFiles().orEmpty().filter { it.isDirectory }.forEach { pkgDir ->
            val from = pkgDir.name
            pkgDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                file.useLines { lines ->
                    lines.forEach { line ->
                        val match = IMPORT_RE.find(line) ?: return@forEach
                        val to = match.groupValues[1]
                        if (to != from) edges += Edge(from, to)
                    }
                }
            }
        }
        return edges
    }

    private fun MutableSet<Edge>.addCycle(a: String, b: String) {
        add(Edge(a, b))
        add(Edge(b, a))
    }

    private data class Edge(val from: String, val to: String) : Comparable<Edge> {
        override fun compareTo(other: Edge): Int =
            compareValuesBy(this, other, { it.from }, { it.to })
    }

    private companion object {
        val IMPORT_RE = Regex("""^import\s+hunoia\.luno\.(\w+)[.\s]""")
    }
}
