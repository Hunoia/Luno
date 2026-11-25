package hunoia.luno.arch

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI 框架门禁：页面只允许走 `LunoPage` + 内容助手，形状 / 间距只允许走 token。
 *
 * 迁移期用 `ui-framework-baseline.txt` 记录存量违规（按 规则|文件 计数），只拦新增；
 * 文件改好后的计数低于基线自动放行，重新生成基线即收窄。新文件没有任何豁免。
 */
class UiFrameworkTest {

    private val designRoot = "ui/design/src/main/java/"

    private val baseline: Map<String, Int> by lazy {
        val stream = javaClass.getResourceAsStream("/ui-framework-baseline.txt")
            ?: return@lazy emptyMap()
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.split('|')
                    if (parts.size != 3) null else "${parts[0]}|${parts[1]}" to parts[2].toInt()
                }
                .toMap()
        }
    }

    private data class Rule(
        val id: String,
        val pattern: Regex,
        val include: (path: String) -> Boolean,
    )

    private val rules = listOf(
        Rule("removed-page-template", Regex("""\b(LunoScreen|LunoScreenLazy|LunoScreenPlain|HomeShell)\b""")) { true },
        Rule("scaffold-outside-design", Regex("""\bScaffold\s*\(""")) { it !in designRoot && !it.startsWith(designRoot) },
        Rule(
            "raw-dp",
            Regex("""\b\d+(?:\.\d+)?\.dp\b"""),
        ) { path ->
            isStrictUi(path) &&
                !path.endsWith("ui/theme/Dimension.kt") &&
                !path.endsWith("ui/theme/DockDimension.kt")
        },
        Rule("raw-font-size", Regex("""\bfontSize\s*=\s*\d""")) { path ->
            isStrictUi(path) && !path.contains("/ui/theme/")
        },
        Rule("manual-segmented-shape", Regex("""\bsegmentedShape\s*\(""")) { path ->
            isPageOrSettings(path)
        },
        Rule("direct-lazy-column", Regex("""\bLazyColumn\s*\(""")) { path ->
            isPageOrSettings(path)
        },
        Rule("direct-material-card", Regex("""\bCard\s*\(""")) { path ->
            isPageOrSettings(path)
        },
    )

    @Test
    fun `pages follow the Luno framework`() {
        val problems = mutableListOf<String>()
        rules.forEach { rule ->
            counts(rule).forEach { (path, count) ->
                val allowed = baseline["${rule.id}|$path"] ?: 0
                if (count > allowed) {
                    problems += "$path  ${rule.id}: $count > 基线 $allowed"
                }
            }
        }
        assertTrue(
            buildString {
                appendLine("UI 框架门禁失败（新增违规）：")
                problems.forEach { appendLine("  $it") }
                appendLine("修复后运行 `python3 scripts/ui_baseline.py` 更新基线。")
            },
            problems.isEmpty(),
        )
    }

    private fun counts(rule: Rule): Map<String, Int> =
        productionFiles
            .filter { rule.include(it.repoPath()) }
            .mapNotNull { file ->
                val hits = rule.pattern.findAll(file.codeText()).count()
                if (hits == 0) null else file.repoPath() to hits
            }
            .toMap()

    private fun isStrictUi(path: String): Boolean =
        path.startsWith("app/src/main/java/hunoia/luno/ui/home/") ||
            path.startsWith("app/src/main/java/hunoia/luno/ui/settings/") ||
            (path.startsWith("feature/") && "/settings/" in path)

    private fun isPageOrSettings(path: String): Boolean =
        path.startsWith("app/src/main/java/hunoia/luno/ui/") ||
            (path.startsWith("feature/") && ("/settings/" in path || "/ui/" in path))
}