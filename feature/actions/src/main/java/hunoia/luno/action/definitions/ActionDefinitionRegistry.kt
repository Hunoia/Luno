package hunoia.luno.action.definitions

/**
 * 动作定义注册表：`typeId` 唯一，只做聚合与查找。
 *
 * 动作定义按功能分组自注册（见 `ActionGroups.kt`）；新增动作时改对应分组文件，
 * 不再修改中心定义表。
 */
object ActionDefinitionRegistry {

    private val definitions = LinkedHashMap<String, ActionDefinition>()

    private var snapshot: List<ActionDefinition>? = null

    fun register(definition: ActionDefinition) {
        require(definition.typeId.isNotBlank()) { "action typeId must not be blank" }
        require(!definitions.containsKey(definition.typeId)) {
            "duplicate action definition: ${definition.typeId}"
        }
        definitions[definition.typeId] = definition
        snapshot = null
    }

    fun registerAll(items: List<ActionDefinition>) = items.forEach(::register)

    fun all(): List<ActionDefinition> =
        snapshot ?: definitions.values.toList().also { snapshot = it }

    fun byTypeId(typeId: String): ActionDefinition? = definitions[typeId]

    val registeredTypeIds: Set<String>
        get() = definitions.keys.toSet()
}
