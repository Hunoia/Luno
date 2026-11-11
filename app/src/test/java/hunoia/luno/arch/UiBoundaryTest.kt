package hunoia.luno.arch

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
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

    private val uiRoot = "app/src/main/java/hunoia/luno/ui/"

    private val forbiddenImportPrefixes = listOf(
        "hunoia.luno.config.ConfigProvider",
        "hunoia.luno.config.store",
        "hunoia.luno.config.repository",
        "hunoia.luno.runtime",
        "hunoia.luno.service",
    )

    private val uiFiles get() = productionFiles.filter { it.repoPath().startsWith(uiRoot) }

    private val bareDurationPatterns = listOf(
        Regex("""tween\(\s*\d"""),
        Regex("""durationMillis\s*=\s*\d"""),
        Regex("""\.setDuration\(\s*\d"""),
        Regex("""postDelayed\(\s*\d"""),
    )

    @Test
    fun `no bare duration literals in ui code`() {
        val problems = productionFiles
            .filter(::isUiCode)
            .flatMap { file ->
                file.text.lines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    val hit = bareDurationPatterns.any { it.containsMatchIn(code) }
                    if (hit) "${file.repoPath()}:${index + 1} 使用了裸时长数字（走 Motion 家族 spec / token）" else null
                }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `no legacy Anim tokens`() {
        // 动效时长统一由 MotionFamily 承载；`Anim*` 语义 token 已删除，残留会绕过档位。
        val pattern = Regex("""\bAnim[A-Z][A-Za-z0-9]*""")
        val problems = productionFiles
            .filter(::isUiCode)
            .flatMap { file ->
                file.text.lines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if (pattern.containsMatchIn(code)) {
                        "${file.repoPath()}:${index + 1} 残留 Anim* 动效 token"
                    } else {
                        null
                    }
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

    @Test
    fun `theme 不开启平台窗口 dim`() {
        // 平台 dim 不跟退场动画：退场结束、窗口移除那一帧背景会一下子变亮（「关窗最后卡一下」）。
        // 遮罩统一由 `DialogScrim` 画（ui-guidelines §4.4），透明弹窗主题必须 backgroundDimEnabled=false。
        val pattern = Regex("""backgroundDimEnabled"\s*>\s*true""")
        val values = resValuesXml()
        assertTrue(
            "没扫到 res/values XML，路径基准可能变了：${File("..").absolutePath}",
            values.any { it.name == "themes.xml" },
        )
        val offenders = values
            .filter { pattern.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()
        assertTrue(
            offenders.joinToString("\n", postfix = " 开了平台窗口 dim：改由 DialogScrim 画遮罩"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `ui 不自建 M3 AlertDialog`() {
        // 默认居中 AlertDialog 走平台窗口与窗口 dim，不跟 AnchoredDialog 的遮罩 / 退场动画
        // （ui-guidelines §4.4）：统一走 AnchoredDialog / ConfirmDialog / LunoSheet。
        val problems = productionFiles
            .filter(::isUiCode)
            .flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { it == "androidx.compose.material3.AlertDialog" }
                    .map { "${file.repoPath()} 直接引用 M3 AlertDialog" }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** main 源集下的 `res/values*` XML（跳过 build 目录，否则会扫到生成物）。 */
    private fun resValuesXml(): List<File> =
        File("..").walkTopDown()
            .onEnter { dir -> dir.name !in setOf("build", ".git", ".gradle", "node_modules") }
            .filter { file ->
                file.isFile && file.name.endsWith(".xml") &&
                    "/src/main/res/" in file.path.replace('\\', '/') &&
                    file.parentFile?.name?.startsWith("values") == true
            }
            .toList()

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
