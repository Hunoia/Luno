package hunoia.luno.arch

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI 边界门禁：页面只读 domain 门面，不直接触达配置存储、运行态实现或 `:service` 客户端。
 *
 * 允许的 config 包只剩纯契约 / 结果类型（如 `config.backup.RestorePrecheckResult`）；
 * 一旦页面重新 import `ConfigProvider`、`runtime.*`、`service.*`，这里直接失败。
 */
class UiBoundaryTest {

    private val uiRoot = File("src/main/java/hunoia/luno/ui")

    private val forbidden = listOf(
        "hunoia.luno.config.ConfigProvider",
        "hunoia.luno.config.store",
        "hunoia.luno.config.repository",
        "import hunoia.luno.runtime.",
        "import hunoia.luno.service.",
    )

    @Test
    fun `ui does not reach into storage runtime or service`() {
        assertTrue("缺少 UI 目录：${uiRoot.absolutePath}", uiRoot.isDirectory)
        val problems = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                forbidden.filter { text.contains(it) }.map { token ->
                    "${file.relativeTo(uiRoot).path} 引用了 $token"
                }.asSequence()
            }
            .toList()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
