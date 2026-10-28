package hunoia.luno.arch

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * feature 配置边界门禁：UI / Surface 不得直连 [hunoia.luno.config.ConfigProvider]。
 *
 * 允许直接触达配置存储的只有各 feature 的 domain 门面实现，以及明确的服务侧
 * 资源回收逻辑。新增 feature UI 若绕过门面，会在这里失败。
 */
class FeatureConfigBoundaryTest {

    private val featureRoot = File("../feature")

    private val facadeFiles = setOf(
        "actions/src/main/java/hunoia/luno/domain/action/ActionLibrary.kt",
        "floatball/src/main/java/hunoia/luno/domain/floatball/FloatBallSettingsCenter.kt",
        "gridpanel/src/main/java/hunoia/luno/domain/gridpanel/GridPanelCenter.kt",
        "gridpanel/src/main/java/hunoia/luno/gridpanel/PanelResources.kt",
        "link/src/main/java/hunoia/luno/domain/link/LinkSettingsCenter.kt",
        "search/src/main/java/hunoia/luno/domain/search/SearchCenter.kt",
    )

    @Test
    fun `feature ui only touches config through domain facades`() {
        assertTrue("feature source root not found: ${featureRoot.absolutePath}", featureRoot.isDirectory)
        val offenders = featureRoot.walkTopDown()
            .filter {
                it.isFile &&
                    it.extension == "kt" &&
                    "/src/main/" in it.path &&
                    "/build/" !in it.path
            }
            .filter { it.readText().contains("ConfigProvider") }
            .map { it.relativeTo(featureRoot).invariantSeparatorsPath }
            .filterNot { it in facadeFiles }
            .toList()

        assertTrue(
            "feature 代码绕过配置门面：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
