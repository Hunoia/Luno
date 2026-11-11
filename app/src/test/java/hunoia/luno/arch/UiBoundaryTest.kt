package hunoia.luno.arch

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI 边界门禁：页面只读 domain 门面，不直接触达配置存储、运行态实现或 `:service` 客户端。
 *
 * 允许的 config 包只剩纯契约 / 结果类型（如 `config.backup.RestorePrecheckResult`）；
 * 一旦页面重新 import `ConfigProvider`、`runtime.*`、`service.*`，这里直接失败。
 */
class UiBoundaryTest {

    private val uiRoot = "app/src/main/java/hunoia/luno/ui/"

    private val forbiddenImportPrefixes = listOf(
        "hunoia.luno.config.ConfigProvider",
        "hunoia.luno.config.store",
        "hunoia.luno.config.repository",
        "hunoia.luno.runtime",
        "hunoia.luno.service",
    )

    private val uiFiles get() = productionFiles.filter { it.repoPath().startsWith(uiRoot) }

    @Test
    fun `no bare tween literals in ui code`() {
        val problems = productionFiles
            .filter(::isUiCode)
            .flatMap { file ->
                file.text.lines().mapIndexedNotNull { index, line ->
                    val at = line.indexOf("tween(")
                    val bare = at >= 0 && line.getOrNull(at + "tween(".length)?.isDigit() == true
                    if (bare) "${file.repoPath()}:${index + 1} 使用了裸 tween 数字" else null
                }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `ui does not reach into storage runtime or service`() {
        assertTrue("缺少 UI 目录：$uiRoot", uiFiles.isNotEmpty())

        val problems = uiFiles.flatMap { file ->
            file.imports
                .map { it.name }
                .filter { name -> forbiddenImportPrefixes.any { matchesImport(name, it) } }
                .map { name -> "${file.repoPath()} 引用了 $name" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `system animation scale is not applied twice`() {
        // Compose 的 Recomposer 已把系统动画时长缩放注入 MotionDurationScale，tween/spring
        // 会自动跟随；生产代码再读一次设置手乘就是双乘（0.5x 下时长变 1/4）。
        val problems = productionFiles.flatMap { file ->
            file.text.lines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                if ("ANIMATOR_DURATION_SCALE" in code) {
                    "${file.repoPath()}:${index + 1} 手读系统动画缩放会与 Compose 双乘"
                } else {
                    null
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** 与搬迁前一致：app 只看 ui 包，feature / ui 模块全量。 */
    private fun isUiCode(file: KoFileDeclaration): Boolean =
        when {
            file.moduleName == "app" -> file.repoPath().startsWith(uiRoot)
            file.moduleName.startsWith("feature/") -> true
            file.moduleName.startsWith("ui/") -> true
            else -> false
        }

    private fun matchesImport(name: String, prefix: String): Boolean =
        name == prefix || name.startsWith("$prefix.")
}
