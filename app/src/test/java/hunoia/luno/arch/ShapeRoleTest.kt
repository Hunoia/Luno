package hunoia.luno.arch

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 形状门禁（ui-guidelines §2.1.1 / §2.1.4 / §7）。
 *
 * 圆角只有四个角色：容器、胶囊、正圆、直角。角色由 `ui:design` 的主题层产出，
 * 页面与组件只挑角色，不构造形状、不选曲率 —— 否则同屏会出现「一个软一个硬」，
 * 且改平滑度时无法一处生效。
 */
class ShapeRoleTest {

    private val themeRoot = "ui/design/src/main/java/hunoia/luno/ui/theme/"

    /** 允许直接构造形状的文件：token 定义本身，以及 M3 `Shapes` 的映射（它要 CornerBasedShape，不能收 Shape）。 */
    private val shapeDefinitionFiles = setOf(
        themeRoot + "Dimension.kt",
        themeRoot + "ShapeRoles.kt",
        themeRoot + "PercentSquircleShape.kt",
        themeRoot + "GeneratorTheme.kt",
    )

    @Test
    fun `rounded corner shape stays inside theme`() {
        val problems = productionFiles
            .filter { !it.repoPath().startsWith(themeRoot) }
            .filter { it.codeText().contains("RoundedCornerShape") }
            .map { "${it.repoPath()} 直接构造了 RoundedCornerShape，改用 §2.1.1 的角色 token" }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `squircle shape is only built by theme tokens`() {
        val problems = productionFiles
            .filter { it.repoPath() !in shapeDefinitionFiles }
            .filter { it.codeText().contains("SquircleShape") }
            .map { "${it.repoPath()} 直接构造了 SquircleShape，改用 §2.1.1 的角色 token / containerShape()" }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `shape library does not leak outside ui design`() {
        val problems = productionFiles
            .filter { !it.repoPath().startsWith(themeRoot) }
            .filter { file -> file.imports.any { it.name.startsWith("sv.lib.squircleshape") } }
            .map { "${it.repoPath()} import 了形状库类型，形状库只允许出现在主题层" }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `smoothing is not overridden outside theme`() {
        val literalSmoothing = Regex("""smoothing\s*=\s*\d""")
        val problems = productionFiles
            .filter { it.repoPath() !in shapeDefinitionFiles }
            .flatMap { file ->
                file.text.lines().mapIndexedNotNull { index, line ->
                    when {
                        literalSmoothing.containsMatchIn(line) ->
                            "${file.repoPath()}:${index + 1} 写了平滑度字面量，用 ContainerSmoothing / CapsuleSmoothing"
                        "CornerSmoothing." in line ->
                            "${file.repoPath()}:${index + 1} 引用了 CornerSmoothing 档位，平滑度是角色属性（§2.1.3）"
                        else -> null
                    }
                }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * §2.1.2 第 3 条：容器必须满足 `2·r·(1+s) ≤ h`，否则端部控制点互侵、被静默 clamp。
     *
     * 绑定用例是列表行：最小高 `MinItemHeightNoSecondary`、半径 `ShapeLarge`。
     * 改平滑度或档位时这条会先红，避免「越界后发现弧被拍平」。
     *
     * 从源码取值而不是引用 `Dimension.kt` 的常量：那会连带加载形状库的 class
     * （库用 Java 21 编译，测试 JVM 是 17，直接取常量会 UnsupportedClassVersionError）。
     */
    @Test
    fun `large shape stays inside the row height budget`() {
        val s = smoothingFraction("ContainerSmoothing")
        val radius = dpValue("ShapeLarge")
        val height = dpValue("MinItemHeightNoSecondary")
        val maxRadius = height / (2f * (1f + s))
        assertTrue(
            "ShapeLarge=${radius}dp 超过行高上限 ${maxRadius}dp（s=$s）；改档位时先重算 §2.1.2 第 3 条",
            radius <= maxRadius,
        )
    }

    private fun dimensionSource(): String =
        productionFiles.first { it.repoPath() == themeRoot + "Dimension.kt" }.text

    private fun dpValue(name: String): Float {
        val match = Regex("""val\s+$name\s*=\s*([\d.]+)\.dp""").find(dimensionSource())
        assertTrue("Dimension.kt 里找不到 $name", match != null)
        return match!!.groupValues[1].toFloat()
    }

    /** 形状库只提供 5 个离散平滑度：None 0 / Small 20 / Medium 48 / High 67 / Full 100。 */
    private fun smoothingFraction(name: String): Float {
        val match = Regex("""val\s+$name\s*=\s*CornerSmoothing\.(\w+)""").find(dimensionSource())
        assertTrue("Dimension.kt 里找不到 $name", match != null)
        return when (match!!.groupValues[1]) {
            "None" -> 0f
            "Small" -> 20f
            "Medium" -> 48f
            "High" -> 67f
            "Full" -> 100f
            else -> error("未知平滑度档位 ${match.groupValues[1]}")
        } / 100f
    }
}
