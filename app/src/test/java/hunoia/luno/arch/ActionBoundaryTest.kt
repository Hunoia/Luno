package hunoia.luno.arch

import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Action 硬边界门禁（architecture §1.3 / §1.5 第 3、6 条）。
 *
 * 两件事：
 * 1. `ActionRequest` 只允许在“触发侧产出意图”的少数文件里构造，防止 UI / 配置页把
 *    导航与配置写入塞进执行口（§1.3）。
 * 2. `StoredAction` 的持久化形状只能是 `{ typeId, params }`，防止有人给持久化动作加字段
 *    造成第二套形态（§1.5 第 6 条）。
 */
class ActionBoundaryTest {

    /** 允许构造 `ActionRequest(...)` 的文件（相对仓库根）。新增必须过架构评审。 */
    private val actionRequestSites = setOf(
        // 定义处
        "core/domain/src/main/kotlin/hunoia/luno/core/domain/ActionRequest.kt",
        // 手势触发侧：手势判定后产出请求
        "app/src/main/java/hunoia/luno/runtime/GestureCoordinator.kt",
        // Surface 已持有明确 StoredAction：直接产出请求
        "core/runtime/src/main/java/hunoia/luno/runtime/surface/SurfaceDispatch.kt",
        // 服务进程本地送达口：ActionDispatchPort → 本地 Dispatcher
        "app/src/main/java/hunoia/luno/runtime/SurfaceActionRuntime.kt",
        // 链接分发：外部入口映射到动作库条目
        "feature/link/src/main/java/hunoia/luno/link/LinkDispatcher.kt",
    )

    private val actionRequestCall = Regex("""\bActionRequest\s*\(""")

    @Test
    fun `ActionRequest 只在已评审的触发侧构造`() {
        assertTrue("生产源码为空，Konsist scope 解析失败", productionFiles.isNotEmpty())

        val offenders = productionFiles
            .filter { actionRequestCall.containsMatchIn(it.codeText()) }
            .map { it.repoPath() }
            .filterNot { it in actionRequestSites }

        assertTrue(
            "以下文件新增了 ActionRequest 构造点；配置写入 / 页面导航不得进执行口（architecture §1.3）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `白名单里的构造点仍然存在`() {
        // 防止门禁退化成“永远通过”：白名单文件改名 / 构造点消失时必须有人来改这里。
        val missing = actionRequestSites.filterNot { site ->
            productionFiles.any { file ->
                file.repoPath() == site && actionRequestCall.containsMatchIn(file.codeText())
            }
        }
        assertTrue(
            "白名单文件不存在或已无 ActionRequest 构造点，请同步本测试：${missing.joinToString()}",
            missing.isEmpty(),
        )
    }

    @Test
    fun `StoredAction 持久化形状只有 typeId 与 params`() {
        // encodeDefaults：空 params 也要出现，否则“字段是否还在”无从判断
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(StoredAction.of("test.sample"))
        val keys = Json.parseToJsonElement(encoded).jsonObject.keys

        assertEquals(
            "StoredAction 是唯一动作持久化形态，落盘只能有 typeId 与 params（architecture §1.5 第 6 条）",
            setOf("typeId", "params"),
            keys,
        )
    }
}
