package hunoia.luno.arch

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * feature 配置边界门禁：UI / Surface 不得直连 [hunoia.luno.config.ConfigProvider]。
 *
 * 允许直接触达配置存储的只有各 feature 的 domain 门面实现，以及明确的服务侧
 * 资源回收逻辑。新增 feature UI 若绕过门面，会在这里失败。
 */
class FeatureConfigBoundaryTest {

    private val featureRoot = "feature/"

    private val facadeFiles = setOf(
        "feature/actions/src/main/java/hunoia/luno/domain/action/ActionLibrary.kt",
        "feature/floatball/src/main/java/hunoia/luno/domain/floatball/FloatBallSettingsCenter.kt",
        "feature/gridpanel/src/main/java/hunoia/luno/domain/gridpanel/GridPanelCenter.kt",
        "feature/gridpanel/src/main/java/hunoia/luno/domain/gridpanel/HabitCenter.kt",
        "feature/gridpanel/src/main/java/hunoia/luno/domain/gridpanel/TodoCenter.kt",
        "feature/gridpanel/src/main/java/hunoia/luno/domain/gridpanel/TrendCenter.kt",
        "feature/gridpanel/src/main/java/hunoia/luno/gridpanel/PanelResources.kt",
        "feature/search/src/main/java/hunoia/luno/domain/search/SearchCenter.kt",
        "feature/appcontrol/src/main/java/hunoia/luno/domain/appcontrol/AppControlCenter.kt",
    )

    @Test
    fun `feature ui only touches config through domain facades`() {
        val featureFiles = productionFiles.filter { it.repoPath().startsWith(featureRoot) }
        assertTrue("feature 生产源码为空，Konsist scope 解析失败", featureFiles.isNotEmpty())

        val offenders = featureFiles
            .filter { "ConfigProvider" in it.codeText() }
            .map { it.repoPath() }
            .filterNot { it in facadeFiles }

        assertTrue(
            "feature 代码绕过配置门面：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
