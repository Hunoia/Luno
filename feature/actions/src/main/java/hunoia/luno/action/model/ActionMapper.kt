package hunoia.luno.action.model

/**
 * `StoredAction → Action` 的稳定入口。
 *
 * 具体映射按 `typeId` 注册在 [ActionMappingRegistry]；这里只负责安装内置映射并委托。
 * `StoredAction` 是唯一持久化形态，映射层不保留默认值之外的配置语义。
 */
object ActionMapper {

    fun fromStored(stored: StoredAction): Action {
        BuiltinActionDecoders.ensure()
        return ActionMappingRegistry.decode(stored.typeId, stored.params)
    }
}
