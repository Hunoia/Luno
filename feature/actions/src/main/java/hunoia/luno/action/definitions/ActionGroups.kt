package hunoia.luno.action.definitions

/**
 * 一个动作分组：定义与解码映射同文件自注册。
 *
 * 新增动作时只改对应分组（或新建分组并登记到 [BuiltinActionGroups]），不修改中心定义表。
 */
internal interface ActionGroup {
    fun definitions(): List<ActionDefinition>
    fun installDecoders()
}

/**
 * 内置动作分组清单：只负责安装顺序与幂等。
 *
 * 分组顺序不影响 UI（分类顺序由 [ActionDefinitions.categoryOrder] 显式声明），
 * 因此这里可以按领域自由增删。
 */
internal object BuiltinActionGroups {

    private val groups: List<ActionGroup> = listOf(
        AppActionGroup,
        IntentActionGroup,
        SystemActionGroup,
        AccessibilityActionGroup,
        AdvancedActionGroup,
        InternalActionGroup,
    )

    private var installed = false

    fun ensure() {
        if (installed) return
        installed = true
        groups.forEach { group ->
            ActionDefinitionRegistry.registerAll(group.definitions())
            group.installDecoders()
        }
    }
}
