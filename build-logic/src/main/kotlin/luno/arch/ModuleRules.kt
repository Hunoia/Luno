package luno.arch

/**
 * 模块依赖的唯一事实源（Gradle 构建期强制，不再靠测试里的文本扫描）。
 *
 * 规则变更 = 修改本文件 + 过评审；`luno.arch` 插件在 configuration 阶段比对，
 * 不一致直接让构建失败（`-x test` 也绕不过）。
 */
object ModuleRules {

    /** 允许出现的模块。新增模块必须登记，否则构建失败。 */
    val knownModules: Set<String> = setOf(
        ":core:common", ":core:domain", ":core:bridge", ":core:data", ":core:runtime", ":core:facade",
        ":ui:design", ":capability:launch", ":capability:ocr",
        ":feature:execute", ":feature:actions",
        ":feature:link", ":feature:search", ":feature:quickaccess", ":feature:gesture",
        ":feature:floatball", ":feature:gridpanel", ":feature:annotate", ":feature:a11y",
        ":feature:clipboard",
        ":feature:picker",
        ":app",
    )

    /** 模块 → 允许直接依赖的模块（必须与之完全一致：多一条或少一条都失败）。 */
    val allowList: Map<String, Set<String>> = mapOf(
        ":core:common" to emptySet(),
        ":core:domain" to emptySet(),
        ":core:bridge" to setOf(":core:common", ":core:domain"),
        ":core:data" to setOf(":core:common", ":core:domain", ":core:bridge"),
        ":core:runtime" to setOf(":core:common", ":core:domain", ":core:bridge", ":core:data"),
        // 门面契约（UI 可见）：只放契约与 DTO，实现留在 :app
        ":core:facade" to setOf(":core:common", ":core:domain", ":core:data"),
        ":ui:design" to setOf(":core:common", ":core:domain", ":core:bridge", ":core:facade"),
        ":capability:launch" to setOf(":core:common", ":core:domain", ":core:data", ":core:bridge"),
        ":capability:ocr" to setOf(":core:common", ":core:domain"),
        ":feature:execute" to setOf(":core:common", ":core:domain", ":core:bridge"),
        // 偏差：动作定义/执行器与展示元数据仍同模块（ActionDefinition 完全二分挂账）
        ":feature:actions" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:execute", ":ui:design",
        ),
        ":feature:link" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:facade",
            ":core:runtime", ":ui:design", ":feature:actions",
        ),
        ":feature:search" to setOf(
            ":core:common", ":core:domain", ":core:data", ":core:bridge",
            ":ui:design", ":capability:launch",
        ),
        // 跨 feature 的聚合页面：只读别人，不被别人依赖
        ":feature:picker" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":core:facade", ":ui:design",
            ":feature:actions", ":feature:gridpanel", ":feature:quickaccess", ":feature:search",
            ":feature:gesture", ":feature:floatball",
        ),
        ":feature:quickaccess" to setOf(
            ":capability:launch",
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:search", ":ui:design", ":feature:actions",
        ),
        ":feature:gesture" to setOf(
            ":capability:ocr",
            ":core:common", ":core:domain", ":core:data", ":core:bridge",
            ":core:runtime", ":feature:actions", ":feature:link", ":ui:design",
        ),
        ":feature:floatball" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:gesture", ":feature:actions",
            ":feature:link", ":ui:design",
        ),
        ":feature:gridpanel" to setOf(
            ":capability:launch",
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:actions",
            ":feature:execute", ":feature:gesture", ":feature:quickaccess",
            ":feature:search", ":ui:design",
        ),
        ":feature:annotate" to setOf(":core:common", ":core:domain", ":core:bridge", ":ui:design"),
        // 剪贴板管理页：读写唯一入口在 :core:data，OCR 结果页经 :core:facade 注入
        ":feature:clipboard" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:facade", ":ui:design",
        ),
        ":feature:a11y" to setOf(":core:common", ":core:domain", ":core:bridge", ":ui:design"),
        ":app" to (knownModules - ":app"),
    )

    /** 允许用 `api(project(...))` 做契约性再导出的模块（其余一律 `implementation`）。 */
    val apiProjectExporters: Set<String> = setOf(
        // LunoScreen/LunoSheet 的公开参数含 HazeState
        ":ui:design",
        // 门面接口的签名里直接出现 domain / data 的 DTO，属于契约性再导出
        ":core:facade",
    )
}
