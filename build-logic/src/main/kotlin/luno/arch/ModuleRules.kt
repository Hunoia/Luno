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
        ":core:common", ":core:domain", ":core:bridge", ":core:data", ":core:runtime", ":core:effect", ":core:facade",
        ":ui:design", ":capability:launch", ":capability:ocr", ":capability:qr", ":capability:notify",
        ":capability:appcatalog",
        ":feature:execute", ":feature:actions",
        ":feature:link", ":feature:search", ":feature:quickaccess", ":feature:gesture",
        ":feature:floatball", ":feature:gridpanel", ":feature:annotate",
        ":feature:appcontrol",
        ":feature:clipboard",
        ":feature:widget",
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
        // 效果播放契约：纯 JVM，不依赖任何模块（Request / Handle / 枚举）
        ":core:effect" to emptySet(),
        ":core:facade" to setOf(":core:common", ":core:domain", ":core:data", ":core:effect"),
        ":ui:design" to setOf(":core:common", ":core:domain", ":core:bridge", ":core:facade"),
        ":capability:launch" to setOf(":core:common", ":core:domain", ":core:data", ":core:bridge", ":capability:appcatalog"),
        ":capability:appcatalog" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data", ":ui:design",
        ),
        ":capability:ocr" to setOf(":core:common", ":core:domain"),
        ":capability:qr" to setOf(":core:common", ":core:domain"),
        ":capability:notify" to setOf(":core:common", ":core:domain", ":core:bridge"),
        ":feature:execute" to setOf(":core:common", ":core:domain", ":core:bridge"),
        // 偏差：动作定义/执行器与展示元数据仍同模块（ActionDefinition 完全二分挂账）
        ":feature:actions" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:execute", ":ui:design", ":capability:launch",
        ),
        // 识别器的「通知」输出走系统 Notification API（LIVE 胶囊 / 标准通知），
        // 超级岛模式用 :capability:notify 的 FocusTemplate 发布器；呈现机制属于平台能力。
        ":feature:link" to setOf(
            ":capability:notify",
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:facade", ":core:runtime", ":ui:design", ":feature:actions",
        ),
        ":feature:search" to setOf(
            ":core:common", ":core:domain", ":core:data", ":core:bridge",
            ":ui:design", ":capability:launch", ":capability:appcatalog",
        ),
        // 跨 feature 的聚合页面：只读别人，不被别人依赖
        ":feature:picker" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":core:facade", ":ui:design",
            ":feature:actions", ":feature:gridpanel", ":feature:quickaccess", ":feature:search",
            ":feature:gesture", ":feature:floatball", ":capability:appcatalog",
        ),
        ":feature:quickaccess" to setOf(
            ":capability:launch",
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:search", ":ui:design", ":feature:actions",
            ":capability:appcatalog",
        ),
        ":feature:gesture" to setOf(
            ":capability:ocr",
            ":capability:qr",
            ":core:common", ":core:domain", ":core:data", ":core:bridge",
            ":core:runtime", ":feature:actions", ":ui:design",
        ),
        ":feature:floatball" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:gesture", ":feature:actions",
            ":feature:link", ":ui:design",
        ),
        ":feature:gridpanel" to setOf(
            ":capability:launch",
            ":capability:notify",
            ":capability:appcatalog",
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:runtime", ":feature:actions",
            ":feature:execute", ":feature:quickaccess",
            ":ui:design",
        ),
        ":feature:annotate" to setOf(":core:common", ":core:domain", ":core:bridge", ":core:data", ":ui:design"),
        // 剪贴板管理页：读写唯一入口在 :core:data，OCR 结果页经 :core:facade 注入
        ":feature:clipboard" to setOf(
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":core:facade", ":ui:design",
        ),
        // 应用冻结管理页：清单/禁用状态/写入点都在 capability:launch，不自建 PackageManager 扫描。
        ":feature:appcontrol" to setOf(
            ":capability:launch",
            ":capability:appcatalog",
            ":core:common", ":core:domain", ":core:bridge", ":core:data",
            ":ui:design",
        ),
        // 桌面小部件（Glance）：主进程运行，直读 MultiProcess DataStore；复用 gridpanel 域门面
        // 与跳转 extras（HabitExtras/TodoExtras），不触碰 app 层。
        ":feature:widget" to setOf(":core:common", ":core:domain", ":feature:gridpanel", ":ui:design"),
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
