import luno.arch.ModuleRules
import org.gradle.api.artifacts.ProjectDependency

/**
 * 模块依赖门禁（构建期）。
 *
 * 1. 每个模块必须在 [ModuleRules.allowList] 登记，且实际依赖与登记**完全一致**；
 * 2. 模块集合必须与登记一致（新增模块不登记就失败）；
 * 3. 只有白名单模块能用 `api(project(...))`，其余必须 `implementation`。
 *
 * 在 `projectsEvaluated` 阶段校验：改 `build.gradle.kts` 立刻失败，`-x test` 绕不过。
 */

/** 只看真正有构建脚本的模块（`:core` / `:feature` 这类只是目录容器）。 */
fun org.gradle.api.Project.isRealModule(): Boolean =
    java.io.File(projectDir, "build.gradle.kts").isFile

fun org.gradle.api.Project.projectDeps(apiOnly: Boolean): Set<String> {
    val configs = configurations.filter { if (apiOnly) it.name.startsWith("api") else true }
    return configs
        .flatMap { cfg -> cfg.dependencies.filterIsInstance<ProjectDependency>() }
        .map { it.path }
        .toSet()
}

gradle.projectsEvaluated {
    val problems = mutableListOf<String>()
    val actual = rootProject.allprojects
        .filter { it != rootProject && it.isRealModule() }
        .map { it.path }
        .toSet()

    (actual - ModuleRules.knownModules).takeIf { it.isNotEmpty() }?.let {
        problems += "模块未在 ModuleRules 登记：${it.sorted().joinToString(", ")}"
    }
    (ModuleRules.knownModules - actual).takeIf { it.isNotEmpty() }?.let {
        problems += "ModuleRules 登记了不存在的模块：${it.sorted().joinToString(", ")}"
    }

    for (module in actual intersect ModuleRules.knownModules) {
        val p = rootProject.allprojects.first { it.path == module }
        val allowed = ModuleRules.allowList[module].orEmpty()
        // 配置容器里会出现指向自身的 ProjectDependency，忽略
        val declared = p.projectDeps(apiOnly = false) - p.path

        (declared - allowed).takeIf { it.isNotEmpty() }?.let {
            problems += "$module 新增了未评审依赖：${it.sorted().joinToString(", ")}"
        }
        (allowed - declared).takeIf { it.isNotEmpty() }?.let {
            problems += "$module 依赖已收缩，请同步 ModuleRules：${it.sorted().joinToString(", ")}"
        }
        if (module !in ModuleRules.apiProjectExporters) {
            p.projectDeps(apiOnly = true).takeIf { it.isNotEmpty() }?.let {
                problems += "$module 用 api(project(...)) 泄露依赖：${it.sorted().joinToString(", ")}" +
                    "（只有 ${ModuleRules.apiProjectExporters.joinToString()} 允许）"
            }
        }
    }

    if (problems.isNotEmpty()) {
        throw org.gradle.api.GradleException(
            buildString {
                appendLine("模块依赖门禁失败（规则：build-logic/src/main/kotlin/luno/arch/ModuleRules.kt）")
                problems.forEach { appendLine("  - $it") }
                append("规则变更需同步 ModuleRules 并过架构评审（docs/architecture.md §8）。")
            },
        )
    }
}
