package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewCarousel
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionMappingRegistry
import hunoia.luno.action.model.bool
import hunoia.luno.action.model.enumValue
import hunoia.luno.action.model.intOrNull
import hunoia.luno.action.model.long
import hunoia.luno.action.model.str
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.PARAM_X_DP
import hunoia.luno.core.domain.PARAM_Y_DP
import hunoia.luno.core.domain.USE_TRIGGER_POINT_KEY

/** 无障碍能力类动作。 */
internal object AccessibilityActionGroup : ActionGroup {

    override fun definitions(): List<ActionDefinition> = listOf(
        ActionDefinition("accessibility.back", "返回", ActionCategory.NAVIGATION, Capability.Accessibility,
            icon = Icons.AutoMirrored.Filled.ArrowBack),
        ActionDefinition("accessibility.home", "主页", ActionCategory.NAVIGATION, Capability.Accessibility,
            icon = Icons.Default.Home),
        ActionDefinition("accessibility.recents", "最近任务", ActionCategory.NAVIGATION, Capability.Accessibility,
            icon = Icons.Default.ViewCarousel),
        ActionDefinition("accessibility.tap", "点击", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.TouchApp),
        ActionDefinition("accessibility.longPress", "长按", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.Gesture),
        ActionDefinition("accessibility.doubleTap", "双击", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.TouchApp),
        ActionDefinition("accessibility.scroll", "滚动焦点元素", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.VerticalAlignTop),
        ActionDefinition(
            "accessibility.swipe", "滑动", ActionCategory.OPERATION, Capability.Accessibility,
            icon = Icons.Default.VerticalAlignTop,
        ),
        ActionDefinition("accessibility.screenshot", "截屏", ActionCategory.UI, Capability.Accessibility,
            icon = Icons.Default.Screenshot),
        ActionDefinition("accessibility.screenPick", "识别屏幕内容", ActionCategory.GESTURE_TOOL, Capability.Accessibility,
            icon = Icons.Default.DocumentScanner),
        ActionDefinition("accessibility.screenLinks", "识屏取链", ActionCategory.GESTURE_TOOL, Capability.Accessibility,
            icon = Icons.Default.Link),
        ActionDefinition("accessibility.splitScreen", "分屏", ActionCategory.UI, Capability.Accessibility,
            icon = Icons.Default.Splitscreen),
        ActionDefinition("accessibility.powerButton", "电源菜单", ActionCategory.POWER, Capability.Accessibility,
            icon = Icons.Default.PowerSettingsNew),
        ActionDefinition("accessibility.notificationPanel", "通知面板", ActionCategory.UI, Capability.Accessibility,
            icon = Icons.Default.Notifications),
        ActionDefinition("accessibility.quickPanel", "快捷面板", ActionCategory.UI, Capability.Accessibility,
            icon = Icons.Default.Dashboard),
        ActionDefinition("accessibility.lockScreen", "锁屏", ActionCategory.POWER, Capability.Accessibility,
            icon = Icons.Default.ScreenLockPortrait),
    )

    override fun installDecoders() {
        ActionMappingRegistry.register("accessibility.back") { Action.Back }
        ActionMappingRegistry.register("accessibility.home") { Action.Home }
        ActionMappingRegistry.register("accessibility.recents") { Action.Recents }
        ActionMappingRegistry.register("accessibility.tap") { p ->
            Action.Tap(
                p.intOrNull(PARAM_X_DP),
                p.intOrNull(PARAM_Y_DP),
                p.bool(USE_TRIGGER_POINT_KEY),
            )
        }
        ActionMappingRegistry.register("accessibility.longPress") { p ->
            Action.LongPress(
                p.intOrNull(PARAM_X_DP),
                p.intOrNull(PARAM_Y_DP),
                p.long("durationMs", 1000L),
                p.bool(USE_TRIGGER_POINT_KEY),
            )
        }
        ActionMappingRegistry.register("accessibility.doubleTap") { p ->
            Action.DoubleTap(
                p.intOrNull(PARAM_X_DP),
                p.intOrNull(PARAM_Y_DP),
                p.bool(USE_TRIGGER_POINT_KEY),
            )
        }
        ActionMappingRegistry.register("accessibility.scroll") { p ->
            Action.ScrollFocused(p.str("direction") != "BACKWARD")
        }
        ActionMappingRegistry.register("accessibility.swipe") { p -> Action.Swipe(p.enumValue("direction")) }
        ActionMappingRegistry.register("accessibility.screenshot") { Action.Screenshot }
        ActionMappingRegistry.register("accessibility.screenPick") { Action.ScreenPick }
        ActionMappingRegistry.register("accessibility.screenLinks") { Action.ScreenLinks }
        ActionMappingRegistry.register("accessibility.splitScreen") { Action.SplitScreen }
        ActionMappingRegistry.register("accessibility.powerButton") { Action.PowerButton }
        ActionMappingRegistry.register("accessibility.notificationPanel") { Action.NotificationPanel }
        ActionMappingRegistry.register("accessibility.quickPanel") { Action.QuickPanel }
        ActionMappingRegistry.register("accessibility.lockScreen") { Action.LockScreen }
    }
}
