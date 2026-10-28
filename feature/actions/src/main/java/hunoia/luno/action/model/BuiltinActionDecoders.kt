package hunoia.luno.action.model

import hunoia.luno.action.definitions.BuiltinActionGroups

/**
 * 内置动作映射的安装入口。
 *
 * 具体 `typeId → Action` 映射与动作定义同文件分组（`*ActionGroup.kt`），
 * 这里只保证分组安装一次；中心不再持有 50 条映射。
 */
internal object BuiltinActionDecoders {

    fun ensure() = BuiltinActionGroups.ensure()
}
