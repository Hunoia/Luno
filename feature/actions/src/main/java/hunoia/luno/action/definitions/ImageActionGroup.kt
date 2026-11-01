package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Image
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.core.domain.Capability

/**
 * 图片 / 屏幕标注动作组（计划 2026-11-04-annotate）。
 *
 * 两个动作共用 `:feature:annotate` 的引擎，只是宿主不同：
 * 屏幕标注挂无障碍浮层（需要 a11y 截屏回退），图片编辑走透明 Activity。
 */
internal object ImageActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        ActionDefinition(
            "screen.annotate",
            "屏幕标注",
            ActionCategory.UI,
            Capability.Accessibility,
            icon = Icons.Default.Brush,
        ),
        ActionDefinition(
            "image.edit",
            "图片标注",
            ActionCategory.MEDIA,
            Capability.None,
            icon = Icons.Default.Image,
        ),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("screen.annotate") { Action.ScreenAnnotate }
        ActionMappingRegistry.register("image.edit") { Action.ImageEdit }
    }
}
