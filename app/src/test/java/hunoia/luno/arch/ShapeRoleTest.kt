package hunoia.luno.arch

import org.junit.Assert.assertEquals
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
        themeRoot + "UiAppearance.kt",
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
     * 可调外观（§2.5）把半径交给用户，但**默认值与每个组件族的预算**仍必须自洽：
     * 逐族核对「默认半径 ≤ 该族最矮容器高 / (2(1+s))」，两种形态各算一次。
     * 组内中档（小元件 +4）按最大 48dp 容器校验。
     *
     * 从源码取值而不是引用常量：那会连带加载形状库的 class
     * （库用 Java 21 编译，测试 JVM 是 17，直接取常量会 UnsupportedClassVersionError）。
     */
    @Test
    fun `every shape group default fits its container budget`() {
        val uiAppearance = productionFiles.first { it.repoPath() == themeRoot + "UiAppearance.kt" }.text
        val settings = productionFiles
            .first { it.repoPath().endsWith("config/model/UiAppearanceSettings.kt") }
            .text

        // 组件族预算：enum ShapeRole 的 `List(56),` 之类。
        val budgets = Regex("""^ {4}(\w+)\((\d+)(?:,[^)]*)?\),""", RegexOption.MULTILINE)
            .findAll(uiAppearance.substringAfter("enum class ShapeRole"))
            .associate { it.groupValues[1] to it.groupValues[2].toFloat() }
        // 默认半径：`val list: ShapeSetting = ShapeSetting(radiusDp = 16),` 之类。
        val defaults = Regex(
            """val\s+(list|fab|menu|toolbar|compact|sheet)\s*:\s*ShapeSetting\s*=\s*ShapeSetting\(radiusDp\s*=\s*(\d+)\)""",
        ).findAll(settings).associate { it.groupValues[1] to it.groupValues[2].toFloat() }

        assertTrue("UiAppearance.kt 没解析到组件族预算：$budgets", budgets.size >= 6)
        assertTrue("UiAppearanceSettings.kt 没解析到默认圆角：$defaults", defaults.size == 6)

        val problems = mutableListOf<String>()
        // 连续曲率 0.48 / 标准圆弧 0。
        for ((modeName, s) in listOf("连续" to 0.48f, "标准" to 0f)) {
            for ((field, radius) in defaults) {
                val roleName = when (field) {
                    "list" -> "List"
                    "fab" -> "Fab"
                    "menu" -> "Menu"
                    "toolbar" -> "Toolbar"
                    "compact" -> "Compact"
                    "sheet" -> "Sheet"
                    else -> error("未知分组 $field")
                }
                val budget = budgets.getValue(roleName)
                val cap = budget / (2f * (1f + s))
                if (radius > cap) {
                    problems += "$field(${radius}dp) 超过 $modeName 形态上限 " +
                        "${"%.1f".format(cap)}dp（预算 ${budget}dp）"
                }
                // 小元件组的中档 = 小档 + 4，按中档自己的预算（48dp 级图标格）校验。
                if (field == "compact") {
                    val mediumCap = budgets.getValue("CompactMedium") / (2f * (1f + s))
                    if (radius + 4f > mediumCap) {
                        problems += "compact 中档(${radius + 4f}dp) 超过 $modeName 形态上限 " +
                            "${"%.1f".format(mediumCap)}dp（预算 ${budgets.getValue("CompactMedium")}dp）"
                    }
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** 默认半径必须还是 `Dimension.kt` 的档位值（改档位时两处一起改，别让默认值悄悄漂移）。 */
    @Test
    fun `default radii match dimension tokens`() {
        assertEquals(dpValue("ShapeLarge"), 16f, 0.01f)
        assertEquals(dpValue("ShapeSmall"), 8f, 0.01f)
        assertEquals(dpValue("ShapeExtraLarge"), 32f, 0.01f)
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
