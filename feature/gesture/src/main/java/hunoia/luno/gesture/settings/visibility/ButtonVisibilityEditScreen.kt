package hunoia.luno.gesture.settings.visibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.gesture.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.config.model.VisibilityScreen
import hunoia.luno.config.model.VisibilityTargetMode
import hunoia.luno.config.model.VisibilityTargets
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.AppPickerRequest
import hunoia.luno.ui.component.sheet.AppPickerSlot
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.gesture.settings.visibility.ButtonVisibilityText.label
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ButtonVisibilityEditScreen(
    profileId: String,
    onBack: () -> Unit,
    /** 应用选择器由 `:app` 注入（与动作库 / 链接设置同一插槽）。 */
    appPicker: AppPickerSlot? = null,
) {
    val initial by GestureConfig.initial.collectAsStateWithLifecycle(initialValue = InitialSettings())
    val buttons by GestureConfig.buttons.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val isNew = profileId == NEW_VISIBILITY_PROFILE_ID
    var draft by remember { mutableStateOf<VisibilityProfile?>(null) }
    var loaded by remember(profileId) { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showScreenPicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(profileId, initial.buttonVisibility.profiles, isNew) {
        if (!loaded) {
            val existing = initial.buttonVisibility.profiles.find { it.id == profileId }
            when {
                isNew -> {
                    draft = VisibilityProfile(id = UUID.randomUUID().toString())
                    loaded = true
                }
                existing != null -> {
                    draft = existing
                    loaded = true
                }
                // 配置还没加载完：继续等，别先用随机 id 建草稿（否则保存会新增副本、删除匹配不到）。
            }
        }
    }

    val current = draft
    if (current == null) {
        // 配置未加载完靠上面 LaunchedEffect 的「继续等」规避；真正落到这里说明场景已被删。
        LunoScreenLazy(
            title = stringResource(
                if (isNew) R.string.button_visibility_new else R.string.button_visibility_edit,
            ),
            onBack = onBack,
            state = rememberLazyListState(),
        ) {
            item(key = "missing") {
                EmptyState(
                    message = stringResource(R.string.button_visibility_missing),
                    icon = Icons.TwoTone.Visibility,
                )
            }
        }
        return
    }

    fun save() {
        val profile = current
        scope.launch {
            GestureConfig.updateInitial { settings ->
                val profiles = settings.buttonVisibility.profiles
                val index = profiles.indexOfFirst { it.id == profile.id }
                val next = if (index >= 0) {
                    profiles.toMutableList().also { it[index] = profile }
                } else {
                    profiles + profile
                }
                settings.copy(buttonVisibility = settings.buttonVisibility.copy(profiles = next))
            }
            onBack()
        }
    }

    fun delete() {
        val id = current.id
        scope.launch {
            GestureConfig.updateInitial { settings ->
                settings.copy(
                    buttonVisibility = settings.buttonVisibility.copy(
                        profiles = settings.buttonVisibility.profiles.filterNot { it.id == id },
                    ),
                )
            }
            onBack()
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.button_visibility_delete_title),
            text = stringResource(R.string.button_visibility_delete_text),
            destructive = true,
            onDismissRequest = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                delete()
            },
        )
    }

    LunoScreenLazy(
        title = stringResource(
            if (isNew) R.string.button_visibility_new else R.string.button_visibility_edit,
        ),
        onBack = onBack,
        state = rememberLazyListState(),
        actions = {
            if (!isNew) {
                LunoIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteConfirm = true },
                )
            }
            LunoIconButton(
                icon = Icons.Default.Check,
                contentDescription = stringResource(R.string.save),
                onClick = { save() },
            )
        },
        sheets = {
            if (showAppPicker) {
                val picker = appPicker
                if (picker == null) {
                    showAppPicker = false
                } else {
                    picker(
                        AppPickerRequest(
                            selectedPackageNames = current.packageNames.toList(),
                            selectedUserId = null,
                            onDismiss = { showAppPicker = false },
                            onConfirm = { selected ->
                                draft = current.copy(packageNames = selected.toSet())
                                showAppPicker = false
                            },
                            onSelect = null,
                        ),
                    )
                }
            }
            if (showScreenPicker) {
                LunoSheet(onDismissRequest = { showScreenPicker = false }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter)) {
                        Text(
                            text = stringResource(R.string.button_visibility_screens),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = PageGutter),
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = ContentBottom),
                            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                        ) {
                            val screens = VisibilityScreen.entries
                            items(screens) { screen ->
                                val index = screens.indexOf(screen)
                                LunoSelectableRow(
                                    title = screen.label(),
                                    selected = screen in current.screens,
                                    shape = segmentedShape(index, screens.size),
                                    onSelectedChange = { checked ->
                                        draft = current.copy(
                                            screens = if (checked) current.screens + screen
                                            else current.screens - screen,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (showTargetPicker) {
                TargetPickerSheet(
                    current = current,
                    buttons = buttons,
                    onDismissRequest = { showTargetPicker = false },
                    onModeChange = { draft = current.copy(mode = it) },
                    onTargetToggle = { id, checked ->
                        draft = current.copy(
                            targetIds = if (checked) current.targetIds + id
                            else current.targetIds - id,
                        )
                    },
                )
            }
        },
    ) {
        item(key = "name") {
            LunoGroup(title = stringResource(R.string.button_visibility_name)) {
                LunoInputRow(
                    label = stringResource(R.string.button_visibility_name),
                    value = current.name,
                    placeholder = stringResource(R.string.button_visibility_name_hint),
                    onValueChange = { draft = current.copy(name = it) },
                )
            }
        }
        item(key = "match") {
            LunoGroup(title = stringResource(R.string.button_visibility)) {
                LunoSettingsRow(
                    title = stringResource(R.string.button_visibility_apps),
                    subtitle = if (current.packageNames.isEmpty()) {
                        stringResource(R.string.button_visibility_any_app)
                    } else {
                        stringResource(R.string.button_visibility_apps_count, current.packageNames.size)
                    },
                    shape = segmentedShape(0, 2),
                    onClick = { showAppPicker = true },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.button_visibility_screens),
                    subtitle = if (current.screens.isEmpty()) {
                        stringResource(R.string.button_visibility_any_screen)
                    } else {
                        current.screens.map { it.label() }.joinToString(" / ")
                    },
                    shape = segmentedShape(1, 2),
                    onClick = { showScreenPicker = true },
                )
            }
        }
        item(key = "effect") {
            LunoGroup(title = stringResource(R.string.button_visibility_target)) {
                LunoSwitchRow(
                    title = stringResource(R.string.button_visibility_visible),
                    subtitle = stringResource(R.string.button_visibility_visible_desc),
                    checked = current.visible,
                    shape = segmentedShape(0, 2),
                    onCheckedChange = { draft = current.copy(visible = it) },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.button_visibility_target),
                    subtitle = ButtonVisibilityText.targetSummary(current),
                    shape = segmentedShape(1, 2),
                    onClick = { showTargetPicker = true },
                )
            }
        }
    }
}

@Composable
private fun TargetPickerSheet(
    current: VisibilityProfile,
    buttons: List<GestureButton>,
    onDismissRequest: () -> Unit,
    onModeChange: (VisibilityTargetMode) -> Unit,
    onTargetToggle: (String, Boolean) -> Unit,
) {
    LunoSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter)) {
            Text(
                text = stringResource(R.string.button_visibility_target),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = PageGutter),
            )
            val modes = listOf(
                VisibilityTargetMode.ALL to stringResource(R.string.button_visibility_target_all),
                VisibilityTargetMode.ONLY to stringResource(R.string.button_visibility_target_only),
                VisibilityTargetMode.EXCEPT to stringResource(R.string.button_visibility_target_except),
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = ContentBottom),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                items(modes) { (mode, label) ->
                    LunoSelectableRow(
                        title = label,
                        selected = current.mode == mode,
                        singleSelect = true,
                        onSelectedChange = { onModeChange(mode) },
                    )
                }
                if (current.mode != VisibilityTargetMode.ALL) {
                    item(key = "targets_title") {
                        Text(
                            text = stringResource(R.string.button_visibility_target),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = PageGutter),
                        )
                    }
                    item(key = "float_ball") {
                        LunoSelectableRow(
                            title = stringResource(R.string.button_visibility_target_float_ball),
                            selected = VisibilityTargets.FLOAT_BALL in current.targetIds,
                            onSelectedChange = { checked ->
                                onTargetToggle(VisibilityTargets.FLOAT_BALL, checked)
                            },
                        )
                    }
                    item(key = "notification_capsule") {
                        LunoSelectableRow(
                            title = stringResource(R.string.button_visibility_target_notification_capsule),
                            selected = VisibilityTargets.NOTIFICATION_CAPSULE in current.targetIds,
                            onSelectedChange = { checked ->
                                onTargetToggle(VisibilityTargets.NOTIFICATION_CAPSULE, checked)
                            },
                        )
                    }
                    item(key = "clipboard_ball") {
                        LunoSelectableRow(
                            title = stringResource(R.string.button_visibility_target_clipboard_ball),
                            selected = VisibilityTargets.CLIPBOARD_BALL in current.targetIds,
                            onSelectedChange = { checked ->
                                onTargetToggle(VisibilityTargets.CLIPBOARD_BALL, checked)
                            },
                        )
                    }
                    items(buttons) { button ->
                        LunoSelectableRow(
                            title = button.name.ifBlank { button.id },
                            selected = button.id in current.targetIds,
                            onSelectedChange = { checked -> onTargetToggle(button.id, checked) },
                        )
                    }
                }
            }
        }
    }
}
