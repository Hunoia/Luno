package hunoia.luno.ui.settings.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.ListStyle
import hunoia.luno.ui.component.row.ChoiceRow
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.facade.PrivilegedPermission
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 设置 tab：面板 / 动作 / 其他。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSettingsTab(
    topPadding: Dp,
    onNavToActionPanels: () -> Unit = {},
    onNavToGridPanels: () -> Unit = {},
    onNavToButtonVisibility: () -> Unit = {},
    onNavToRecentAppsBlocklist: () -> Unit = {},
    onNavToMiniWindowSettings: () -> Unit = {},
    onNavToScreenPickSettings: () -> Unit = {},
    onNavToActionSelect: () -> Unit = {},
    onNavToPrivilegedPermissions: () -> Unit = {},
    onNavToAccessibilityManager: () -> Unit = {},
    onNavToPersistentNotificationSettings: () -> Unit = {},
    onNavToStatusBarIconSettings: () -> Unit = {},
    onNavToLogSettings: () -> Unit = {},
    onNavToClipboard: () -> Unit = {},
    onNavToHabits: () -> Unit = {},
    onNavToTodos: () -> Unit = {},
) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPointer by remember { mutableStateOf(false) }
    var showFontScale by remember { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(facades.system.fontScale(context)) }
    var canWriteSystemSettings by remember {
        mutableStateOf(facades.system.canWriteSystemSettings(context))
    }
    var fontScalePermissionPending by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    val actionSettings by TuneConfig.action.collectAsStateWithLifecycle(initialValue = ActionSettings())
    val gestureSettings by TuneConfig.gesture.collectAsStateWithLifecycle(initialValue = GestureSettings())
    val advancedSettings by TuneConfig.advanced.collectAsStateWithLifecycle(initialValue = AdvancedSettings())
    val initialSettings by GestureConfig.initial.collectAsStateWithLifecycle(initialValue = InitialSettings())

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeTick) {
        fontScale = facades.system.fontScale(context)
        canWriteSystemSettings = facades.system.canWriteSystemSettings(context)
        if (fontScalePermissionPending) {
            fontScalePermissionPending = false
            if (canWriteSystemSettings) showFontScale = true
        }
    }

    fun openFontScale() {
        if (canWriteSystemSettings) {
            showFontScale = true
            return
        }
        fontScalePermissionPending = true
        val intent = facades.system.privilegedPermissionIntent(
            context,
            PrivilegedPermission.WRITE_SYSTEM_SETTINGS,
        ) ?: run {
            fontScalePermissionPending = false
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { fontScalePermissionPending = false }
    }

    fun commitFontScale(value: Float): Boolean {
        val ok = facades.system.setFontScale(context, value)
        if (ok) {
            fontScale = facades.system.fontScale(context)
        } else {
            showToast(R.string.font_scale_write_failed)
        }
        return ok
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(
                start = PageGutter,
                top = topPadding,
                end = PageGutter,
                bottom = FloatingContentBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            item(key = "panels") {
                LunoGroup(title = stringResource(R.string.action_settings_panel_group)) {
                    LunoSettingsRow(
                        icon = Icons.Default.RadioButtonChecked,
                        title = stringResource(R.string.action_panels),
                        subtitle = stringResource(R.string.action_panel_count, actionSettings.actionPanels.panels.size),
                        shape = segmentedShape(0, 3),
                        onClick = onNavToActionPanels,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.GridView,
                        title = stringResource(R.string.grid_panels),
                        subtitle = stringResource(R.string.action_panel_count, actionSettings.actionGridPanels.panels.size),
                        shape = segmentedShape(1, 3),
                        onClick = onNavToGridPanels,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.ContentPaste,
                        title = stringResource(R.string.clipboard_settings_entry),
                        subtitle = stringResource(R.string.clipboard_settings_entry_hint),
                        shape = segmentedShape(2, 3),
                        onClick = onNavToClipboard,
                    )
                }
            }
            item(key = "habits") {
                LunoGroup(title = stringResource(hunoia.luno.gridpanel.R.string.action_settings_habit_group)) {
                    LunoSettingsRow(
                        icon = Icons.Default.CheckCircle,
                        title = stringResource(hunoia.luno.gridpanel.R.string.habit_list_title),
                        shape = segmentedShape(0, 2),
                        onClick = onNavToHabits,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Checklist,
                        title = stringResource(hunoia.luno.gridpanel.R.string.todo_list_title),
                        shape = segmentedShape(1, 2),
                        onClick = onNavToTodos,
                    )
                }
            }
            item(key = "actions") {
                LunoGroup(title = stringResource(R.string.action_settings_action_group)) {
                    LunoSettingsRow(
                        icon = Icons.Default.TouchApp,
                        title = stringResource(R.string.pointer),
                        subtitle = stringResource(R.string.pointer_hint),
                        shape = segmentedShape(0, 5),
                        onClick = { showPointer = true },
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.DocumentScanner,
                        title = stringResource(R.string.screen_pick_settings_title),
                        subtitle = stringResource(
                            if (initialSettings.screenPickGesture.enabled) {
                                R.string.screen_pick_settings_enabled
                            } else {
                                R.string.screen_pick_settings_disabled
                            },
                        ),
                        shape = segmentedShape(1, 5),
                        onClick = onNavToScreenPickSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Crop,
                        title = stringResource(R.string.action_settings_mini_window_position),
                        shape = segmentedShape(2, 5),
                        onClick = onNavToMiniWindowSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.recent_apps_blocklist),
                        subtitle = if (actionSettings.recentAppsBlocklist.isEmpty()) {
                            ""
                        } else {
                            stringResource(R.string.action_param_multi_selected, actionSettings.recentAppsBlocklist.size)
                        },
                        shape = segmentedShape(3, 5),
                        onClick = onNavToRecentAppsBlocklist,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Visibility,
                        title = stringResource(hunoia.luno.gesture.R.string.button_visibility),
                        shape = segmentedShape(4, 5),
                        onClick = onNavToButtonVisibility,
                    )
                }
            }
            item(key = "system") {
                LunoGroup(title = stringResource(R.string.action_settings_system_group)) {
                    LunoSettingsRow(
                        icon = Icons.Default.TextFields,
                        title = stringResource(R.string.font_scale_entry),
                        subtitle = if (canWriteSystemSettings) {
                            stringResource(
                                R.string.font_scale_entry_summary,
                                (fontScale * 100f).roundToInt(),
                            )
                        } else {
                            stringResource(R.string.font_scale_need_permission)
                        },
                        shape = segmentedShape(0, 4),
                        onClick = ::openFontScale,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.NotificationsOff,
                        title = stringResource(R.string.persistent_notification_settings_entry),
                        subtitle = stringResource(R.string.persistent_notification_settings_entry_hint),
                        shape = segmentedShape(1, 4),
                        onClick = onNavToPersistentNotificationSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.VisibilityOff,
                        title = stringResource(R.string.status_bar_icons_entry),
                        subtitle = stringResource(R.string.status_bar_icons_entry_hint),
                        shape = segmentedShape(2, 4),
                        onClick = onNavToStatusBarIconSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Accessibility,
                        title = stringResource(R.string.a11y_settings_entry),
                        subtitle = stringResource(R.string.a11y_settings_entry_hint),
                        shape = segmentedShape(3, 4),
                        onClick = onNavToAccessibilityManager,
                    )
                }
            }
            item(key = "appearance") {
                LunoGroup(title = stringResource(R.string.action_settings_appearance_group)) {
                    ChoiceRow(
                        title = stringResource(R.string.action_settings_list_style),
                        subtitle = stringResource(R.string.action_settings_list_style_hint),
                        options = listOf(
                            stringResource(R.string.list_style_segmented),
                            stringResource(R.string.list_style_card_divided),
                        ),
                        selectedIndex = advancedSettings.listStyle.ordinal,
                        onSelect = { index ->
                            scope.launch {
                                TuneConfig.updateAdvanced {
                                    it.copy(listStyle = ListStyle.entries[index])
                                }
                            }
                        },
                        shape = segmentedShape(0, 1),
                    )
                }
            }
            item(key = "permissions") {
                LunoGroup(title = stringResource(R.string.action_settings_permissions_group)) {
                    LunoSettingsRow(
                        icon = Icons.Default.Link,
                        title = stringResource(hunoia.luno.actions.R.string.action_deep_links),
                        shape = segmentedShape(0, 3),
                        onClick = onNavToActionSelect,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.VerifiedUser,
                        title = stringResource(R.string.perm_page_title),
                        shape = segmentedShape(1, 3),
                        onClick = onNavToPrivilegedPermissions,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.log_settings_title),
                        subtitle = stringResource(R.string.log_settings_entry_hint),
                        shape = segmentedShape(2, 3),
                        onClick = onNavToLogSettings,
                    )
                }
            }
        }

        if (showPointer) {
            LunoSheet(onDismissRequest = { showPointer = false }, showDragHandle = true) {
                PointerControls(
                    pointer = gestureSettings.pointer,
                    onCommit = { next ->
                        scope.launch { TuneConfig.updateGesture { it.copy(pointer = next) } }
                    },
                )
            }
        }
        if (showFontScale) {
            LunoSheet(onDismissRequest = { showFontScale = false }, showDragHandle = true) {
                FontScaleControls(
                    current = fontScale,
                    onCommit = ::commitFontScale,
                )
            }
        }
    }
}

/** 指针手感/外观滑杆组：拖动改草稿，松手统一落盘。 */
@Composable
private fun PointerControls(
    pointer: GestureSettings.Pointer,
    onCommit: (GestureSettings.Pointer) -> Unit,
) {
    var draft by remember(pointer) { mutableStateOf(pointer) }
    // 不用 LunoColumn：它带的 verticalScroll 会和 ModalBottomSheet 的下滑关闭抢手势，
    // 拖拽时 sheet 会上下抖。这里内容固定、不需要滚动，用普通 Column 把拖动交给 sheet。
    Column(
        modifier = Modifier
            .padding(PageGutter)
            .padding(bottom = ContentBottom),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        LunoGroup(title = stringResource(R.string.pointer_group_motion)) {
            LunoTextSlider(
                value = draft.sensitivityX,
                onValueChange = { draft = draft.copy(sensitivityX = it) },
                onValueChangeFinished = { onCommit(draft) },
                text = stringResource(id = R.string.pointer_sensitivity_x_plain),
                valueDisplay = { stringResource(id = R.string.pointer_sensitivity_x, it) },
                valueRange = 0.5f..4f,
                shape = segmentedShape(0, 3),
            )
            LunoTextSlider(
                value = draft.sensitivityY,
                onValueChange = { draft = draft.copy(sensitivityY = it) },
                onValueChangeFinished = { onCommit(draft) },
                text = stringResource(id = R.string.pointer_sensitivity_y_plain),
                valueDisplay = { stringResource(id = R.string.pointer_sensitivity_y, it) },
                valueRange = 0.5f..4f,
                shape = segmentedShape(1, 3),
            )
            LunoTextSlider(
                value = draft.acceleration,
                onValueChange = { draft = draft.copy(acceleration = it) },
                onValueChangeFinished = { onCommit(draft) },
                text = stringResource(id = R.string.pointer_acceleration_plain),
                valueDisplay = { stringResource(id = R.string.pointer_acceleration, it) },
                valueRange = 0f..2f,
                shape = segmentedShape(2, 3),
            )
        }
        LunoGroup(title = stringResource(R.string.pointer_group_cursor)) {
            // 光标大小是 Int：本地浮点过渡，松手取整提交。
            var localCursorSize by remember(pointer.cursorSizeDp) { mutableStateOf(pointer.cursorSizeDp.toFloat()) }
            LunoTextSlider(
                value = localCursorSize,
                onValueChange = { localCursorSize = it },
                onValueChangeFinished = {
                    draft = draft.copy(cursorSizeDp = localCursorSize.roundToInt())
                    onCommit(draft)
                },
                text = stringResource(id = R.string.pointer_cursor_size_plain),
                valueDisplay = { stringResource(id = R.string.pointer_cursor_size, it.roundToInt()) },
                valueRange = 12f..64f,
                shape = segmentedShape(0, 2),
            )
            LunoTextSlider(
                value = draft.cursorAlpha,
                onValueChange = { draft = draft.copy(cursorAlpha = it) },
                onValueChangeFinished = { onCommit(draft) },
                text = stringResource(id = R.string.pointer_cursor_alpha_plain),
                valueDisplay = { stringResource(id = R.string.pointer_cursor_alpha, (it * 100).roundToInt()) },
                valueRange = 0.2f..1f,
                shape = segmentedShape(1, 2),
            )
        }
    }
}
