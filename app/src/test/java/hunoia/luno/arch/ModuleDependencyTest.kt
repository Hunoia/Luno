package hunoia.luno.arch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块依赖门禁（Phase 4）。
 *
 * 以 `build.gradle.kts` 的 `project(":...")` 声明为事实源，对照本文件的 allow-list。
 * 附录 B 的目标形态尚未完全收敛（actions / gesture 等仍有记录在 refactor-progress
 * 的横向边），这里先锁住当前已评审的依赖图：新增未列出的边会失败，收缩时同步删条目。
 * 包级 `PackageDependencyTest` 在模块化完成后退化为重复信息，已由本测试取代。
 */
class ModuleDependencyTest {

    /** 模块 → 允许依赖的模块（只允许这些，且必须与声明完全一致）。 */
    private val knownModules: Set<String> = setOf(
        ":core:common", ":core:domain", ":core:bridge", ":core:data", ":core:runtime",
        ":ui:design", ":capability:launch",
        ":feature:execute", ":feature:actions", ":feature:conditions", ":feature:automation",
        ":feature:link", ":feature:search", ":feature:quicklaunch", ":feature:gesture",
        ":feature:floatball", ":feature:gridpanel", ":app",
    )

    private val allowList: Map<String, Set<String>> = mapOf(
        ":core:common" to emptySet(),
        ":core:domain" to emptySet(),
        ":core:bridge" to setOf(":core:common", ":core:domain"),
        ":core:data" to setOf(":core:common", ":core:domain", ":core:bridge"),
        ":core:runtime" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
        ),
        ":ui:design" to setOf(":core:common", ":core:domain", ":core:bridge"),
        ":capability:launch" to setOf(
            ":core:common", ":core:domain", ":core:data", ":core:bridge",
        ),
        ":feature:execute" to setOf(":core:common", ":core:domain", ":core:bridge"),
        // 偏差：动作定义/执行器与展示元数据仍同模块（ActionDefinition 完全二分挂账）。
        ":feature:actions" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:execute", ":ui:design",
        ),
        ":feature:conditions" to setOf(
            ":core:common", ":core:domain", ":core:bridge",
        ),
        ":feature:automation" to setOf(
            ":core:common", ":core:domain", ":core:bridge",
            ":core:runtime", ":feature:conditions", ":feature:execute",
        ),
        ":feature:link" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:execute", ":ui:design",
        ),
        ":feature:search" to setOf(
            ":core:common", ":core:domain", ":core:data", ":core:bridge",
            ":ui:design", ":capability:launch",
        ),
        ":feature:quicklaunch" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:execute", ":feature:search", ":ui:design",
        ),
        ":feature:gesture" to setOf(
            ":core:common", ":core:domain", ":core:data", ":core:bridge",
            ":core:runtime", ":feature:actions", ":feature:execute", ":ui:design",
        ),
        ":feature:floatball" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:actions", ":feature:automation",
            ":feature:conditions", ":feature:execute", ":feature:gesture",
            ":feature:link", ":ui:design",
        ),
        ":feature:gridpanel" to setOf(
            ":capability:launch",
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:actions", ":feature:automation",
            ":feature:execute", ":feature:gesture", ":feature:quicklaunch",
            ":feature:search", ":ui:design",
        ),
        ":app" to (knownModules - ":app"),
    )

    @Test
    fun `settings includes exactly the known modules`() {
        val included = parseIncludes(File("../settings.gradle.kts"))
        assertEquals(allowList.keys, included)
    }

    @Test
    fun `module dependencies match the reviewed graph`() {
        val problems = mutableListOf<String>()
        for ((module, allowed) in allowList) {
            val buildFile = moduleBuildFile(module)
            assertTrue("缺少模块构建文件：${buildFile.path}", buildFile.isFile)
            val actual = parseProjectDependencies(buildFile)
            val unexpected = actual - allowed
            val missing = allowed - actual
            if (unexpected.isNotEmpty()) {
                problems += "$module 新增了未评审依赖：${unexpected.sorted().joinToString(", ")}"
            }
            if (missing.isNotEmpty()) {
                problems += "$module 依赖已收缩，请同步 allow-list：${missing.sorted().joinToString(", ")}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun moduleBuildFile(module: String): File {
        val path = module.removePrefix(":").replace(':', File.separatorChar)
        return File("../$path/build.gradle.kts")
    }

    private fun parseIncludes(file: File): Set<String> {
        val regex = Regex("""include\("(:[^"]+)"\)""")
        return file.readLines()
            .flatMap { line -> regex.findAll(line).map { it.groupValues[1] }.toList() }
            .toSet()
    }

    private fun parseProjectDependencies(file: File): Set<String> {
        val regex = Regex("""project\("(:[^"]+)"\)""")
        return file.readLines()
            .flatMap { line -> regex.findAll(line).map { it.groupValues[1] }.toList() }
            .toSet()
    }
}
