package hunoia.luno.actions.shortcut

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.actions.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.ListSpacing

/**
 * 动态快捷方式页。
 *
 * 系统只把动态快捷方式暴露给默认桌面，所以「采集全部」需要 Luno 临时持默认桌面权限；
 * 采集后全部入口标为已导入、可启动，并进动作选择页与快速搜索。启动复用
 * `app.launchShortcut` 分发，不依赖后续切桌面。
 */
@Composable
fun DynamicShortcutsScreen(
    onBack: () -> Unit,
    vm: DynamicShortcutViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    // LocalResources.current 是配置感知的 Compose 资源读取（lint: LocalContextGetResourceValueCall
    // 禁止用 LocalContext.current.getString——配置变化会读到过期值）。
    val resources = LocalResources.current

    // 从系统「默认桌面」页返回：权限可能变了，重新读一遍。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshPermission()
    }

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val text = message.count?.let { count ->
                resources.getString(message.resId, count)
            } ?: resources.getString(message.resId)
            showToast(text)
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.dynamic_shortcuts_title),
        onBack = onBack,
        contentSpacing = ListSpacing,
    ) {
        item(key = "home") {
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Home,
                    title = stringResource(R.string.dynamic_shortcuts_change_home),
                    subtitle = stringResource(R.string.dynamic_shortcuts_set_default_home_hint),
                    shape = segmentedShape(0, 2),
                    enabled = !state.busy,
                    onClick = vm::toggleDefaultHome,
                )
                LunoSettingsRow(
                    icon = Icons.Default.AppShortcut,
                    title = stringResource(R.string.dynamic_shortcuts_collect),
                    subtitle = stringResource(R.string.dynamic_shortcuts_collect_hint),
                    shape = segmentedShape(1, 2),
                    enabled = !state.busy && state.hasPermission,
                    onClick = vm::collectAll,
                )
            }
        }
    }
}
