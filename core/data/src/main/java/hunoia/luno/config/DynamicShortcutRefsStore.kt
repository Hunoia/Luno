package hunoia.luno.config

import hunoia.luno.core.domain.DynamicShortcutRef
import hunoia.luno.core.domain.DynamicShortcutRefs

/**
 * 动态快捷方式导入记录的读取门面（`:core:data` 内唯一的配置读写点）。
 *
 * feature 侧（动作设置页、选择器、搜索索引）只经这里拿快照 / 写入，
 * 不直接引用 `ConfigProvider`；记录本身是设备本地系统授权，不进备份。
 */
object DynamicShortcutRefsStore {

    suspend fun snapshot(): DynamicShortcutRefs = ConfigProvider.getDynamicShortcutRefs()

    /** 已确认可启动的入口（选择器 / 搜索合并用）。 */
    suspend fun imported(): List<DynamicShortcutRef> = snapshot().imported()

    suspend fun update(
        transform: suspend (DynamicShortcutRefs) -> DynamicShortcutRefs,
    ) {
        ConfigProvider.updateDynamicShortcutRefs(transform)
    }
}
