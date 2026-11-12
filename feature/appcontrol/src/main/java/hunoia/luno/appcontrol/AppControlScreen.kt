package hunoia.luno.appcontrol

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.quickaccess.icon.AppIconRequests
import hunoia.luno.quickaccess.query.AppAvailability
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.AppPickerSlot
import hunoia.luno.ui.component.sheet.AppPickerRequest
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter

/**
 * 应用冻结管理页：冻结 / 解冻、一键名单、批量操作与失败回执。
 *
 * 默认只列「已冻结 ∪ 一键名单」；搜索时扩到全机（系统包跟随全局「显示系统应用」开关）。
 * 系统 suspend 只读展示；自己与当前桌面禁止冻结（由 PackageControl 兜底）。
 */
@Composable
fun AppControlScreen(
    onBack: () -> Unit,
    appPicker: AppPickerSlot? = null,
    vm: AppControlVM = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { 40.dp.toPx().toInt() }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { showToast(it) }
    }

    val visible = remember(state.apps, state.query) {
        val query = state.query.trim()
        val filtered = if (query.isEmpty()) {
            state.apps.filter {
                it.availability != AppAvailability.ENABLED || it.inOneKey
            }
        } else {
            state.apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        filtered.sortedWith(
            compareBy<ManagedApp> {
                when (it.availability) {
                    AppAvailability.DISABLED -> 0
                    AppAvailability.SUSPENDED -> 1
                    AppAvailability.ENABLED -> 2
                }
            }.thenByDescending { it.inOneKey }.thenBy { it.label.lowercase() },
        )
    }

    LunoScreenLazy(
        title = stringResource(R.string.app_control_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            if (state.selectionMode) {
                PressIconButton(
                    onClick = { vm.selectAll(visible.map { it.packageName }) },
                    enabled = visible.isNotEmpty(),
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.app_control_select_all))
                }
                PressIconButton(
                    onClick = { vm.requestFreezeSelected() },
                    enabled = state.selected.isNotEmpty() && !state.busy,
                ) {
                    Icon(Icons.Default.AcUnit, contentDescription = stringResource(R.string.app_control_freeze_selected))
                }
                PressIconButton(
                    onClick = { vm.unfreezeSelected() },
                    enabled = state.selected.isNotEmpty() && !state.busy,
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.app_control_unfreeze_selected))
                }
                PressIconButton(onClick = { vm.toggleSelectionMode() }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.app_control_cancel_select))
                }
            } else {
                if (appPicker != null) {
                    PressIconButton(onClick = { showPicker = true }) {
                        Icon(
                            Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.app_control_one_key_add),
                        )
                    }
                }
                PressIconButton(onClick = { vm.toggleSelectionMode() }) {
                    Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.app_control_select))
                }
            }
        },
        dialogs = {
            if (state.pendingFreezeCount > 0) {
                ConfirmDialog(
                    title = stringResource(
                        R.string.app_control_freeze_confirm_title,
                        state.pendingFreezeCount,
                    ),
                    text = stringResource(R.string.app_control_freeze_confirm_desc),
                    destructive = true,
                    onDismissRequest = { vm.cancelPendingFreeze() },
                    onConfirm = { vm.confirmFreezeSelected() },
                )
            }
        },
    ) {
        item {
            AppSearchBar(
                query = state.query,
                onQueryChange = vm::setQuery,
                placeholder = stringResource(R.string.app_control_search_hint),
            )
        }
        item {
            LunoGroup {
                LunoSwitchRow(
                    title = stringResource(R.string.app_control_show_system),
                    subtitle = stringResource(R.string.app_control_show_system_hint),
                    checked = state.showSystemApps,
                    onCheckedChange = vm::setShowSystemApps,
                )
            }
        }
        item {
            Text(
                text = stringResource(
                    R.string.app_control_frozen_count,
                    state.frozenCount,
                    state.oneKeyCount,
                ),
                modifier = Modifier.padding(horizontal = PageGutter, vertical = ListSpacing / 4),
            )
        }
        if (state.failures.isNotEmpty()) {
            item {
                LunoGroup(title = stringResource(R.string.app_control_failures_title)) {
                    state.failures.forEachIndexed { index, (packageName, reason) ->
                        LunoSettingsRow(
                            title = packageName,
                            subtitle = reason,
                            shape = segmentedShape(index, state.failures.size + 1),
                        )
                    }
                    LunoSettingsRow(
                        title = stringResource(R.string.app_control_clear_failures),
                        shape = segmentedShape(state.failures.size, state.failures.size + 1),
                        onClick = { vm.clearFailures() },
                    )
                }
            }
        }
        if (state.loading) {
            item {
                Text(
                    text = stringResource(R.string.app_control_loading),
                    modifier = Modifier.padding(horizontal = PageGutter, vertical = ListSpacing),
                )
            }
        } else if (visible.isEmpty()) {
            item {
                EmptyState(
                    message = stringResource(R.string.app_control_empty),
                    modifier = Modifier.padding(vertical = ListSpacing),
                )
            }
        }
        items(visible, key = { it.packageName }) { app ->
            val stateText = statusText(app)
            if (state.selectionMode) {
                LunoSelectableRow(
                    title = app.label,
                    subtitle = stateText.ifEmpty { app.packageName },
                    selected = app.packageName in state.selected,
                    onSelectedChange = { vm.toggleSelected(app.packageName) },
                    iconModel = AppIconRequests.request(
                        context,
                        app.packageName,
                        iconSizePx,
                    ),
                )
            } else {
                LunoSettingsRow(
                    title = app.label,
                    subtitle = stateText.ifEmpty { app.packageName },
                    iconModel = AppIconRequests.request(
                        context,
                        app.packageName,
                        iconSizePx,
                    ),
                    onClick = { vm.toggleFreeze(app) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PressIconButton(onClick = { vm.toggleOneKey(app) }) {
                                Icon(
                                    modifier = Modifier.size(24.dp),
                                    imageVector = if (app.inOneKey) {
                                        Icons.Default.Star
                                    } else {
                                        Icons.Outlined.StarOutline
                                    },
                                    contentDescription = stringResource(
                                        if (app.inOneKey) {
                                            R.string.app_control_one_key_remove
                                        } else {
                                            R.string.app_control_one_key_add
                                        },
                                    ),
                                )
                            }
                            Switch(
                                checked = app.availability == AppAvailability.DISABLED,
                                enabled = !app.protected && app.availability != AppAvailability.SUSPENDED,
                                onCheckedChange = { checked ->
                                    if (checked != (app.availability == AppAvailability.DISABLED)) {
                                        vm.toggleFreeze(app)
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    if (showPicker && appPicker != null) {
        appPicker(
            AppPickerRequest(
                selectedPackageNames = state.apps.filter { it.inOneKey }.map { it.packageName },
                selectedUserId = null,
                confirmOnSelect = false,
                onDismiss = { showPicker = false },
                onConfirm = { packages ->
                    vm.setOneKeyList(packages)
                    showPicker = false
                },
                onSelect = null,
            ),
        )
    }
}

@Composable
private fun statusText(app: ManagedApp): String {
    val parts = buildList {
        if (app.inOneKey) add(stringResource(R.string.app_control_one_key))
        when (app.availability) {
            AppAvailability.DISABLED -> add(stringResource(R.string.app_control_state_frozen))
            AppAvailability.SUSPENDED -> add(stringResource(R.string.app_control_state_suspended))
            AppAvailability.ENABLED -> {
                if (app.protected) add(stringResource(R.string.app_control_state_protected))
            }
        }
    }
    return parts.joinToString(" · ")
}
