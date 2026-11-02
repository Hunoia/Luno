package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewCarousel
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.core.domain.Capability

/** 无障碍能力类动作。 */
internal object AccessibilityActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        ActionDefinition("accessibility.back", "返回", ActionCategory.NAVIGATION, Capability.Accessibility,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            alternativeCapabilities = setOf(Capability.Shizuku),
        ),
        ActionDefinition("accessibility.home", "主页", ActionCategory.NAVIGATION, Capability.Accessibility,
            icon = Icons.Default.Home,
            alternativeCapabilities = setOf(Capability.Shizuku),
        ),
        ActionDefinition("accessibility.recents", "最近任务", ActionCategory.NAVIGATION, Capability.Accessibility,
            icon = Icons.Default.ViewCarousel,
            alternativeCapabilities = setOf(Capability.Shizuku),
        ),
        ActionDefinition("accessibility.tap", "点击", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.TouchApp,
            alternativeCapabilities = setOf(Capability.Shizuku),
        ),
        ActionDefinition("accessibility.scrollForward", "向下滚动", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.VerticalAlignBottom),
        ActionDefinition("accessibility.scrollBackward", "向上滚动", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.VerticalAlignTop),
        ActionDefinition("accessibility.scrollToTop", "滑动到顶部", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.VerticalAlignTop),
        ActionDefinition("accessibility.scrollToBottom", "滑动到底部", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.VerticalAlignBottom),
        ActionDefinition("accessibility.screenshot", "截屏", ActionCategory.UI, Capability.Accessibility,
            icon = Icons.Default.Screenshot),
        ActionDefinition("accessibility.screenPick", "识别屏幕内容", ActionCategory.GESTURE_TOOL, Capability.Accessibility,
            icon = Icons.Default.DocumentScanner),
        ActionDefinition("accessibility.splitScreen", "分屏", ActionCategory.UI, Capability.Accessibility,
            icon = Icons.Default.Splitscreen),
        ActionDefinition("accessibility.powerButton", "电源菜单", ActionCategory.POWER, Capability.Accessibility,
            icon = Icons.Default.PowerSettingsNew,
            alternativeCapabilities = setOf(Capability.Shizuku),
        ),
        ActionDefinition("accessibility.notificationPanel", "通知面板", ActionCategory.UI, Capability.Accessibility,
            icon = Icons.Default.Notifications,
            alternativeCapabilities = setOf(Capability.Shizuku),
        ),
        ActionDefinition("accessibility.quickPanel", "快捷面板", ActionCategory.UI, Capability.Accessibility,
            icon = Icons.Default.Dashboard,
            alternativeCapabilities = setOf(Capability.Shizuku),
        ),
        ActionDefinition("accessibility.lockScreen", "锁屏", ActionCategory.POWER, Capability.Accessibility,
            icon = Icons.Default.ScreenLockPortrait,
            alternativeCapabilities = setOf(Capability.Shizuku),
        ),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("accessibility.back") { Action.Back }
        ActionMappingRegistry.register("accessibility.home") { Action.Home }
        ActionMappingRegistry.register("accessibility.recents") { Action.Recents }
        ActionMappingRegistry.register("accessibility.tap") { Action.Tap }
        ActionMappingRegistry.register("accessibility.scrollForward") { Action.ScrollForward }
        ActionMappingRegistry.register("accessibility.scrollBackward") { Action.ScrollBackward }
        ActionMappingRegistry.register("accessibility.scrollToTop") { Action.ScrollToTop }
        ActionMappingRegistry.register("accessibility.scrollToBottom") { Action.ScrollToBottom }
        ActionMappingRegistry.register("accessibility.screenshot") { Action.Screenshot }
        ActionMappingRegistry.register("accessibility.screenPick") { Action.ScreenPick }
        // 旧配置兼容：独立识链接页已并入识屏幽灵卡（长按含 URL 的文本时出现「打开链接」）。
        ActionMappingRegistry.register("accessibility.screenLinks") { Action.ScreenPick }
        ActionMappingRegistry.register("accessibility.splitScreen") { Action.SplitScreen }
        ActionMappingRegistry.register("accessibility.powerButton") { Action.PowerButton }
        ActionMappingRegistry.register("accessibility.notificationPanel") { Action.NotificationPanel }
        ActionMappingRegistry.register("accessibility.quickPanel") { Action.QuickPanel }
        ActionMappingRegistry.register("accessibility.lockScreen") { Action.LockScreen }
    }
}
