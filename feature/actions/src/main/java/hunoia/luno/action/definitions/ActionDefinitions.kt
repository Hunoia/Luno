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

    /**
     * 选择页与动作库都**不列**的类型：占位型，或引用必须由动态具名行带入的类型。
     * 两处共用同一套排除口径，避免加新类型时只改一处。
     */
    private val dynamicEntryTypeIds: Set<String> = setOf(
        Action.NONE_TYPE_ID,
        "internal.subGesture",
        ACTION_PANEL_TYPE_ID,
        "app.launchShortcut",
        // 应用详情只服务快速搜索长按入口，不在库/选择页开放新建
        "app.details",
        // 网格面板通用空行：panelId 只能由选择页合成的具名行带入，
        // 库/选择页全禁（无参本就进不了库，这里为 picker 生效）。
        GRID_PANEL_TYPE_ID,
    )

    /** 库新建页不能建的类型：动态具名行（占位/引用）只能从它们自己的入口产生。 */
    private val libraryExcluded: Set<String> = dynamicEntryTypeIds

    fun libraryDefinitions(): List<ActionDefinition> = definitions.filterNot { it.typeId in libraryExcluded }

    /**
     * 触钮/子手势动作选择页要排除的类型。必须由调用方显式取用，不能依赖
     * [pickerDefinitions] 的默认参数：收到空集合时 `typeId !in emptySet()` 恒真，
     * 整张表都会变成可选，包括「启动快捷方式」和 `internal.subGesture`。
     */
    fun pickerExcludedTypeIds(): Set<String> = libraryExcluded

    /**
     * 选择页可列的动作 = 定义表 − [pickerExcludedTypeIds] − **有必填参数的类型**。
     *
     * 口径（不要拆开）：选择页只放「无参 / 参数有限（全可选）」的动作；**需要自由填写
     * 参数的只进动作库**（库新建页能填任意参数，建好再以库引用一个条目选进来）。
     * `app.launch`（必需 App）、`shell.custom`（必需命令）、`app.details` 就属于后者。
     * 面板引用（`internal.actionPanel` / `internal.gridPanel`）同样不在列表里：
     * 选择页按全局面板列表合成「一行一个具名面板」。
     */
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
