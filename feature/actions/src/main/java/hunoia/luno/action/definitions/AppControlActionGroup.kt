package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.LockOpen
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.core.domain.Capability

/**
 * 应用冻结动作：一键冻结 / 解冻名单。
 *
 * 目标由「应用冻结」页维护的一键名单决定；解冻只作用于名单中当前 disabled 的包，
 * 不误伤系统里手动禁用的应用。执行器只调 capability:launch 的 PackageControl。
 */
internal object AppControlActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        ActionDefinition(
            "app.freezeOneKey", "冻结名单应用", ActionCategory.APP,
            Capability.Shizuku,
            icon = Icons.Default.AcUnit,
        ),
        ActionDefinition(
            "app.unfreezeOneKey", "解冻名单应用", ActionCategory.APP,
            Capability.Shizuku,
            icon = Icons.Default.LockOpen,
        ),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("app.freezeOneKey") { Action.FreezeOneKey }
        ActionMappingRegistry.register("app.unfreezeOneKey") { Action.UnfreezeOneKey }
    }
}
