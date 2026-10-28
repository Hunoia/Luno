package hunoia.luno.action.model

import kotlinx.serialization.json.JsonObject

/**
 * 一条 `typeId → Action` 映射规则。
 *
 * 映射只读参数，不做 IO、不查配置；失败抛 [UnknownActionTypeException] 或
 * [InvalidActionParameterException]，由执行口转成三态。
 */
fun interface ActionDecoder {
    fun decode(params: JsonObject): Action
}

/**
 * 动作映射注册表：`typeId` 唯一，只做聚合与查找。
 *
 * 这是 Action 扩展点「注册化」的落点：新增动作在功能模块里登记一条 [ActionDecoder]，
 * 不再修改 [ActionMapper] 的中心 `when`。重复注册在启动期直接失败。
 */
object ActionMappingRegistry {

    private val decoders = LinkedHashMap<String, ActionDecoder>()

    fun register(typeId: String, decoder: ActionDecoder) {
        require(typeId.isNotBlank()) { "action typeId must not be blank" }
        require(!decoders.containsKey(typeId)) { "duplicate action decoder: $typeId" }
        decoders[typeId] = decoder
    }

    fun decode(typeId: String, params: JsonObject): Action =
        decoders[typeId]?.decode(params) ?: throw UnknownActionTypeException(typeId)

    fun contains(typeId: String): Boolean = decoders.containsKey(typeId)

    /** 只读快照，供测试校验内置映射完整性。 */
    val registeredTypeIds: Set<String>
        get() = decoders.keys.toSet()
}
