package hunoia.luno.arch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 执行单口门禁（architecture §1.5 第 1 条）。
 *
 * 单口 = `ActionRequest → Dispatcher → Capability Gate → Executor`，不设旁路。结构上体现为：
 * 1. 执行内核（`action.execution` / `action.dispatcher`）只被内核自身、动作库实现与组装根引用；
 * 2. `ActionExecutor` 的实现只在 `:feature:actions`，不在 UI / core / 其他 feature；
 * 3. `ActionDispatcher` 只在已评审的装配点构造（`:feature:execute` 定义 + `:app` 两处进程级装配）。
 *
 * 其他模块要执行动作，只能用 `:core:domain` 的端口（`ActionDispatchPort` /
 * `SurfaceActionDispatch` / `AwaitableActionDispatch`），实现由 `:app` 注入。
 */
class ExecutionSinglePortTest {

    /** 除内核与组装根外，动作库需要实现 `ActionExecutor` 契约。 */
    private val kernelModules = setOf("app", "feature/execute", "feature/actions")

    /** 允许构造 `ActionDispatcher` 的装配点（相对仓库根）。 */
    private val dispatcherSites = setOf(
        "feature/execute/src/main/java/hunoia/luno/action/dispatcher/ActionDispatcher.kt",
        // 手势链路（:service 内的执行上下文，含无障碍执行器）
        "app/src/main/java/hunoia/luno/runtime/GestureCoordinator.kt",
        // Surface 链路（进程级单例，无宿主时能力闸返回需授权）
        "app/src/main/java/hunoia/luno/runtime/SurfaceActionRuntime.kt",
    )

    private val dispatcherCall = Regex("""\bActionDispatcher\s*\(""")

    @Test
    fun `执行内核只被允许的模块引用`() {
        val offenders = productionFiles
            .filterNot { it.moduleName in kernelModules }
            .filter { file ->
                file.importsName { name ->
                    name.startsWith("hunoia.luno.action.execution.") ||
                        name.startsWith("hunoia.luno.action.dispatcher.")
                }
            }
            .map { it.repoPath() }

        assertTrue(
            "以下文件绕过端口直接依赖执行内核（architecture §1.5 第 1 条）；" +
                "请改用 :core:domain 的 ActionDispatchPort / SurfaceActionDispatch：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `动作库不直接依赖分发口`() {
        val offenders = productionFiles
            .filter { it.moduleName == "feature/actions" }
            .filter { file -> file.importsName { it.startsWith("hunoia.luno.action.dispatcher.") } }
            .map { it.repoPath() }

        assertTrue(
            "动作库只应实现 ActionExecutor，不应持有分发口：\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `ActionExecutor 实现只在动作库模块`() {
        val offenders = productionFiles
            .filterNot { it.moduleName == "feature/actions" }
            .flatMap { file ->
                file.classes()
                    .filter { it.hasParentWithName("ActionExecutor") }
                    .map { "${file.repoPath()}:${it.name}" }
            }

        assertTrue(
            "ActionExecutor 实现应留在 :feature:actions（动作库），实际出现在：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `ActionDispatcher 只在已评审的装配点构造`() {
        val sites = productionFiles
            .filter { dispatcherCall.containsMatchIn(it.codeText()) }
            .map { it.repoPath() }
            .toSet()

        assertEquals(
            "ActionDispatcher 构造点与评审结论不一致；新增装配点必须先过架构评审（§1.5 第 1 条）",
            dispatcherSites,
            sites,
        )
    }

    @Test
    fun `装配点各自只构造一个 Dispatcher`() {
        val offenders = dispatcherSites
            .filter { it.startsWith("app/") }
            .filter { site ->
                val file = productionFiles.firstOrNull { it.repoPath() == site }
                file == null || dispatcherCall.findAll(file.codeText()).count() != 1
            }

        assertTrue(
            "装配点应只构造一个 Dispatcher（每次点击重建执行器会丢能力闸状态）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
