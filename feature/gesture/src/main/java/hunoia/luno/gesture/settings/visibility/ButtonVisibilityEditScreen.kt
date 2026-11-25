package hunoia.luno.gesture.settings.visibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.config.model.VisibilityScreen
import hunoia.luno.config.model.VisibilityTargetMode
import hunoia.luno.config.model.VisibilityTargets
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.gesture.R
import hunoia.luno.gesture.settings.global.globalZoneLabel
import hunoia.luno.ui.component.actionText
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.group
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
    /** 挑选触发动作：先落盘草稿再导航，调用方起动作选择页（visibilityProfile 槽）。 */
    onPickTriggerAction: (profileId: String, enter: Boolean) -> Unit = { _, _ -> },
) {
    val initial by GestureConfig.initial.collectAsStateWithLifecycle(initialValue = GestureConfig.latestInitial())
    val buttons by GestureConfig.buttons.collectAsStateWithLifecycle(initialValue = GestureConfig.latestButtons())
    val globalGesture by GestureConfig.globalGesture.collectAsStateWithLifecycle(
        initialValue = GestureConfig.latestGlobalGesture(),
    )
    val librarySettings by ActionLibrary.settings.collectAsStateWithLifecycle(
        initialValue = ActionLibrary.latestSettings(),
    )
    val scope = rememberCoroutineScope()
    val isNew = profileId == NEW_VISIBILITY_PROFILE_ID
    // 新建流程：添加触发动作前会把草稿落盘，返回后按这个真实 id 找回配置；
    // 路由参数仍是 "new"，不能拿它当查找键。
    // activeId / persisted 必须 saveable：跳动作选择页会销毁本页组合，返回时
    // remember 会重新生成 uuid、persisted 归零 → 起一份空白新草稿，保存后与
    // 先前落盘的那条并存（列表里多出重复场景）。saveable 后按原 uuid 从配置
    // 流找回草稿，写回与保存都落在同一条上。draft 不 saveable：落盘过的场景
    // 由下方 LaunchedEffect 按 activeId 从配置恢复，未落盘的编辑丢弃可接受。
    val activeId = rememberSaveable { if (isNew) UUID.randomUUID().toString() else profileId }
    var persisted by rememberSaveable { mutableStateOf(!isNew) }
    var draft by remember { mutableStateOf<VisibilityProfile?>(null) }
    var loaded by remember(profileId) { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showScreenPicker by remember { mutableStateOf(false) }
    var showEffectPicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showCooldownPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(activeId, initial.buttonVisibility.profiles, persisted) {
        if (loaded) return@LaunchedEffect
        if (!persisted) {
            // 未落盘的新草稿：直接以空场景起稿，保存 / 添加动作时才写配置。
            draft = VisibilityProfile(id = activeId)
            loaded = true
            return@LaunchedEffect
        }
        val existing = initial.buttonVisibility.profiles.find { it.id == activeId }
        if (existing != null) {
            draft = existing
            loaded = true
        }
        // persisted 且还没在配置里出现：配置流尚未就绪，继续等下一帧。
    }

    val current = draft
    if (current == null) {
        // 配置未加载完靠上面 LaunchedEffect 的「继续等」规避；真正落到这里说明场景已被删。
        hunoia.luno.ui.component.page.LunoPage(title = stringResource(
                if (isNew) R.string.button_visibility_new else R.string.button_visibility_edit,
            ),
onBack = onBack) {
    list(state = rememberLazyListState()) {
        lazy {

            item(key = "missing") {
                EmptyState(
                    message = stringResource(R.string.button_visibility_missing),
                    icon = Icons.TwoTone.Visibility,
                )
            }

        }
    }
}
        return
    }

    /** 落盘当前草稿后执行 [onDone]；添加触发动作前也走这里，保证选择页写回能找到场景。 */
    fun persistDraft(onDone: () -> Unit) {
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
            persisted = true
            onDone()
        }
    }

    fun save() {
        persistDraft { onBack() }
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

    hunoia.luno.ui.component.page.LunoPage(title = stringResource(
            if (isNew) R.string.button_visibility_new else R.string.button_visibility_edit,
        ),
onBack = onBack,
actions = {
            if (!isNew || persisted) {
                LunoIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteConfirm = true },
                )
            }
            LunoIconButton(
                icon = Icons.Default.Check,
                contentDescription = stringResource(hunoia.luno.design.R.string.save),
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
                            // 可见性规则是多选，需要底部确认按钮
                            confirmOnSelect = false,
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
                    Column(modifier = Modifier.fillMaxWidth()) {
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
            if (showEffectPicker) {
                LunoSheet(onDismissRequest = { showEffectPicker = false }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.button_visibility_visible),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = PageGutter),
                        )
                        // 三态：null = 本场景不改显隐（纯动作场景），true/false = 显示 / 隐藏。
                        val effects = listOf(
                            null to stringResource(R.string.button_visibility_effect_none),
                            true to stringResource(R.string.button_visibility_summary_show),
                            false to stringResource(R.string.button_visibility_summary_hide),
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = ContentBottom),
                            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                        ) {
                            items(effects) { (effect, label) ->
                                val index = effects.indexOfFirst { it.first == effect }
                                LunoSelectableRow(
                                    title = label,
                                    selected = current.visible == effect,
                                    singleSelect = true,
                                    shape = segmentedShape(index, effects.size),
                                    onSelectedChange = {
                                        draft = current.copy(visible = effect)
                                        showEffectPicker = false
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
                    zones = globalGesture.zones,
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
            if (showCooldownPicker) {
                LunoSheet(onDismissRequest = { showCooldownPicker = false }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.button_visibility_trigger_cooldown),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = PageGutter),
                        )
                        val options = listOf(0, 1, 3, 10, 30)
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = ContentBottom),
                            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                        ) {
                            items(options) { seconds ->
                                val index = options.indexOf(seconds)
                                LunoSelectableRow(
                                    title = cooldownLabel(seconds),
                                    selected = current.cooldownSeconds == seconds,
                                    singleSelect = true,
                                    shape = segmentedShape(index, options.size),
                                    onSelectedChange = {
                                        draft = current.copy(cooldownSeconds = seconds)
                                        showCooldownPicker = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }) {
    list(state = rememberLazyListState()) {
        lazy {

        item(key = "name") {
            group(title = stringResource(R.string.button_visibility_name)) {inputRow(
label = stringResource(R.string.button_visibility_name),
value = current.name,
placeholder = stringResource(R.string.button_visibility_name_hint),
onValueChange = { draft = current.copy(name = it) },

)
            }
        }
        item(key = "match") {
            group(title = stringResource(R.string.button_visibility)) {row(
title = stringResource(R.string.button_visibility_apps),
subtitle = if (current.packageNames.isEmpty()) {
                        stringResource(R.string.button_visibility_any_app)
                    } else {
                        stringResource(R.string.button_visibility_apps_count, current.packageNames.size)
                    },
onClick = { showAppPicker = true },

)
row(
title = stringResource(R.string.button_visibility_screens),
subtitle = if (current.screens.isEmpty()) {
                        stringResource(R.string.button_visibility_any_screen)
                    } else {
                        current.screens.map { it.label() }.joinToString(" / ")
                    },
onClick = { showScreenPicker = true },

)
            }
        }
        item(key = "effect") {
            group(title = stringResource(R.string.button_visibility_target)) {row(
title = stringResource(R.string.button_visibility_visible),
subtitle = current.visible.label(),
onClick = { showEffectPicker = true },

)
row(
title = stringResource(R.string.button_visibility_target),
subtitle = ButtonVisibilityText.targetSummary(current),
onClick = { showTargetPicker = true },

)
            }
        }
        item(key = "trigger_enter") {
            TriggerActionsGroup(
                title = stringResource(R.string.button_visibility_trigger_enter),
                actions = current.enterActions,
                libraryEntries = librarySettings.entries,
                onAdd = { persistDraft { onPickTriggerAction(current.id, true) } },
                onDelete = { index ->
                    draft = current.copy(
                        enterActions = current.enterActions.filterIndexed { i, _ -> i != index },
                    )
                },
            )
        }
        item(key = "trigger_exit") {
            TriggerActionsGroup(
                title = stringResource(R.string.button_visibility_trigger_exit),
                actions = current.exitActions,
                libraryEntries = librarySettings.entries,
                onAdd = { persistDraft { onPickTriggerAction(current.id, false) } },
                onDelete = { index ->
                    draft = current.copy(
                        exitActions = current.exitActions.filterIndexed { i, _ -> i != index },
                    )
                },
            )
        }
        item(key = "trigger_cooldown") {
            group(title = stringResource(R.string.button_visibility_trigger)) {row(
title = stringResource(R.string.button_visibility_trigger_cooldown),
subtitle = cooldownLabel(current.cooldownSeconds),
onClick = { showCooldownPicker = true },

)
            }
        }

        }
    }
}
}

/** 触发动作组：已配动作行（行尾删除）+ 添加动作行；列表只进不出换动作 = 删了重加。 */
@Composable
private fun TriggerActionsGroup(
    title: String,
    actions: List<StoredAction>,
    libraryEntries: List<ActionLibraryEntry>,
    onAdd: () -> Unit,
    onDelete: (Int) -> Unit,
) {
    LunoGroup(title = title) {
        actions.forEachIndexed { index, action ->
            LunoSettingsRow(
                title = actionText(action, emptyIfNone = false, actionLibraryEntries = libraryEntries),
                trailing = {
                    LunoIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { onDelete(index) },
                    )
                },
                shape = segmentedShape(index, actions.size + 1),
            )
        }
        LunoSettingsRow(
            icon = Icons.Default.Add,
            title = stringResource(R.string.button_visibility_trigger_add),
            shape = segmentedShape(actions.size, actions.size + 1),
            onClick = onAdd,
        )
    }
}

@Composable
private fun cooldownLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.button_visibility_trigger_cooldown_off)
    else -> stringResource(R.string.button_visibility_trigger_cooldown_seconds, seconds)
}

@Composable
private fun TargetPickerSheet(
    current: VisibilityProfile,
    buttons: List<GestureButton>,
    zones: List<GlobalGestureZone>,
    onDismissRequest: () -> Unit,
    onModeChange: (VisibilityTargetMode) -> Unit,
    onTargetToggle: (String, Boolean) -> Unit,
) {
    LunoSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    item(key = "result_capsule") {
                        LunoSelectableRow(
                            title = stringResource(R.string.button_visibility_target_result_capsule),
                            selected = VisibilityTargets.RESULT_CAPSULE in current.targetIds,
                            onSelectedChange = { checked ->
                                onTargetToggle(VisibilityTargets.RESULT_CAPSULE, checked)
                            },
                        )
                    }
                    item(key = "zones_title") {
                        Text(
                            text = stringResource(R.string.button_visibility_target_zones),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = PageGutter),
                        )
                    }
                    itemsIndexed(zones, key = { _, zone -> zone.id }) { index, zone ->
                        LunoSelectableRow(
                            title = globalZoneLabel(zone, index),
                            selected = zone.id in current.targetIds,
                            onSelectedChange = { checked ->
                                onTargetToggle(zone.id, checked)
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
