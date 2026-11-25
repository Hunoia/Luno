package hunoia.luno.actions.shortcut

import android.graphics.BitmapFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.actions.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.ListSpacing

/**
 * 动态快捷方式页。
 *
 * 系统只把动态快捷方式暴露给默认桌面，所以「采集全部」需要 Luno 临时持默认桌面权限；
 * 采集后全部入口标为已导入、可启动，并进动作选择页与快速搜索。启动复用
 * `app.launchShortcut` 分发，不依赖后续切桌面。
 *
 * Shizuku 就绪时「自动切换采集」一键完成切换/切回与导航还原；会话期间应用内
 * 「添加到桌面」由 pin 宿主静默入库（含别人桌面已固定的入口——本机 ShortcutService
 * 按 launcher 隔离 pinned 集合，accept 是把它们收进视野的唯一合规通道）。
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

    // 从系统「默认桌面」页 / HOME 回跳返回：权限与会话态可能都变了，重新读一遍。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshPermission()
    }

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val text = when {
                message.text != null -> message.text
                message.count != null -> resources.getString(message.resId, message.count)
                else -> resources.getString(message.resId)
            }
            showToast(text)
        }
    }

    hunoia.luno.ui.component.page.LunoPage(title = stringResource(R.string.dynamic_shortcuts_title),
onBack = onBack) {
    list(contentSpacing = ListSpacing) {
        lazy {

        item(key = "home") {
            // 会话进行中多一行「恢复原桌面」，分段圆角按实际行数走。
            val rowCount = if (state.sessionActive) 4 else 3
            LunoGroup {
                if (state.sessionActive) {
                    LunoSettingsRow(
                        icon = Icons.Default.Undo,
                        title = stringResource(R.string.dynamic_shortcuts_restore),
                        subtitle = stringResource(R.string.dynamic_shortcuts_restore_hint),
                        shape = segmentedShape(0, rowCount),
                        enabled = !state.busy,
                        onClick = vm::endAutoSession,
                    )
                }
                LunoSettingsRow(
                    icon = Icons.Default.SwapHoriz,
                    title = stringResource(R.string.dynamic_shortcuts_auto_switch),
                    subtitle = stringResource(R.string.dynamic_shortcuts_auto_switch_hint),
                    shape = segmentedShape(if (state.sessionActive) 1 else 0, rowCount),
                    enabled = !state.busy && state.shizukuReady && !state.sessionActive,
                    onClick = vm::beginAutoSession,
                )
                LunoSettingsRow(
                    icon = Icons.Default.AppShortcut,
                    title = stringResource(R.string.dynamic_shortcuts_collect),
                    subtitle = stringResource(R.string.dynamic_shortcuts_collect_hint),
                    shape = segmentedShape(if (state.sessionActive) 2 else 1, rowCount),
                    enabled = !state.busy && state.hasPermission,
                    onClick = vm::collectAll,
                )
                LunoSettingsRow(
                    icon = Icons.Default.Home,
                    title = stringResource(R.string.dynamic_shortcuts_change_home),
                    subtitle = stringResource(R.string.dynamic_shortcuts_set_default_home_hint),
                    shape = segmentedShape(if (state.sessionActive) 3 else 2, rowCount),
                    enabled = !state.busy,
                    onClick = vm::toggleDefaultHome,
                )
            }
        }

        if (state.imported.isNotEmpty()) {
            item(key = "imported") {
                // 组首说明行 + 每条导入：点行启动，行尾直接删（只移除 Luno 的记录）。
                val rows = state.imported.size + 1
                LunoGroup {
                    LunoSettingsRow(
                        title = stringResource(R.string.dynamic_shortcuts_imported_title),
                        subtitle = stringResource(R.string.dynamic_shortcuts_imported_hint),
                        shape = segmentedShape(0, rows),
                    )
                    state.imported.forEachIndexed { index, ref ->
                        val iconBitmap = remember(ref.iconPath) {
                            ref.iconPath?.let { path ->
                                runCatching {
                                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                                }.getOrNull()
                            }
                        }
                        LunoSettingsRow(
                            title = ref.label,
                            subtitle = ref.appLabel,
                            icon = if (iconBitmap == null) Icons.Default.AppShortcut else null,
                            iconBitmap = iconBitmap,
                            shape = segmentedShape(index + 1, rows),
                            enabled = !state.busy,
                            onClick = { vm.launchImported(ref) },
                            trailing = {
                                LunoIconButton(
                                    icon = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.dynamic_shortcuts_remove),
                                    onClick = { vm.removeImported(ref) },
                                    enabled = !state.busy,
                                )
                            },
                        )
                    }
                }
            }
        }

        }
    }
}
}
