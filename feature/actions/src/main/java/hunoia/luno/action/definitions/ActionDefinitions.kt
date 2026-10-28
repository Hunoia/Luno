package hunoia.luno.action.definitions

import hunoia.luno.action.model.Action
import hunoia.luno.core.domain.ACTION_PANEL_TYPE_ID
import hunoia.luno.core.domain.GRID_PANEL_TYPE_ID

/**
 * 动作定义目录：只负责查询、过滤与分类顺序。
 *
 * 具体定义按功能分组放在 `*ActionGroup.kt`（定义 + 解码映射同文件），
 * 这里不再持有 50 条中心定义。新增动作 = 改一个分组文件。
 */
object ActionDefinitions {

    private fun ensureInstalled() = BuiltinActionGroups.ensure()

    val definitions: List<ActionDefinition>
        get() {
            ensureInstalled()
            return ActionDefinitionRegistry.all()
        }

    fun byTypeId(typeId: String): ActionDefinition? {
        ensureInstalled()
        return ActionDefinitionRegistry.byTypeId(typeId)
    }

    /** 不可在动作库中新建的类型：占位型，或必须引用具体实体的类型 */
    private val libraryExcluded: Set<String> = setOf(
        Action.NONE_TYPE_ID,
        "internal.subGesture",
        "internal.runRule",
        ACTION_PANEL_TYPE_ID,
        "app.launchShortcut",
        // 应用详情只服务快速搜索长按入口，不在库/选择页开放新建
        "app.details",
        // 网格面板通用空行：panelId 只能由选择页合成的具名行带入，
        // 库/选择页/自动化全禁（无参本就进不了库，这里为 picker/automation 生效）。
        GRID_PANEL_TYPE_ID,
    )

    /** 不可作为自动化规则动作的类型：依赖运行时触点，或在自动化上下文中空跑 */
    val automationExcluded: Set<String> = libraryExcluded + setOf(
        "accessibility.tap",
        "accessibility.longPress",
    )

    fun libraryDefinitions(): List<ActionDefinition> = definitions.filterNot { it.typeId in libraryExcluded }

    fun automationExcludedTypeIds(): Set<String> = automationExcluded

    /**
     * 触钮/子手势动作选择页要排除的类型。必须由调用方显式取用，不能依赖
     * [pickerDefinitions] 的默认参数：收到空集合时 `typeId !in emptySet()` 恒真，
     * 整张表都会变成可选，包括「启动快捷方式」和 `internal.subGesture`。
     */
    fun pickerExcludedTypeIds(): Set<String> = libraryExcluded

    fun pickerDefinitions(excluded: Set<String> = libraryExcluded): List<ActionDefinition> =
        definitions.filter { it.typeId !in excluded && it.parameters.none { p -> p.required } }

    /** 显式分组顺序：与定义表声明顺序解耦，动作库与选择页共用。 */
    private val categoryOrder: Map<ActionCategory, Int> = listOf(
        ActionCategory.NONE,
        ActionCategory.APP,
        ActionCategory.LINK_SHARE,
        ActionCategory.SYSTEM,
        ActionCategory.MEDIA,
        ActionCategory.CLIPBOARD,
        ActionCategory.NAVIGATION,
        ActionCategory.OPERATION,
        ActionCategory.UI,
        ActionCategory.POWER,
        ActionCategory.VISIBILITY,
        ActionCategory.ENTRY,
        ActionCategory.GESTURE_TOOL,
        ActionCategory.ADVANCED,
        ActionCategory.ACTION_PANEL,
        ActionCategory.GRID_PANEL,
        ActionCategory.SUB_GESTURE,
    ).withIndex().associate { (i, c) -> c to i }

    fun categoryOrder(category: ActionCategory): Int = categoryOrder[category] ?: Int.MAX_VALUE
}
