package hunoia.luno.ui.settings.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
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
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 设置 tab：功能 / 动作 / 系统 / 外观。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSettingsTab(
    topPadding: Dp,
    onNavToActionPanels: () -> Unit = {},
    onNavToSearchEngines: () -> Unit = {},
    onNavToFloatBallSettings: () -> Unit = {},
    onNavToLinkDispatch: () -> Unit = {},
    onNavToButtonVisibility: () -> Unit = {},
    onNavToMiniWindowSettings: () -> Unit = {},
    onNavToPointerSettings: () -> Unit = {},
    onNavToScreenPickSettings: () -> Unit = {},
    onNavToPrivilegedPermissions: () -> Unit = {},
    onNavToAccessibilityManager: () -> Unit = {},
    onNavToPersistentNotificationSettings: () -> Unit = {},
    onNavToNotificationSettings: () -> Unit = {},
    onNavToStatusBarIconSettings: () -> Unit = {},
) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
            item(key = "appearance") {
                LunoGroup(title = stringResource(R.string.action_settings_appearance_group)) {
                    ChoiceRow(
                        title = stringResource(R.string.action_settings_list_style),
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
            item(key = "features") {
                LunoGroup(title = stringResource(R.string.action_settings_feature_group)) {
                    LunoSettingsRow(
                        icon = Icons.Default.RadioButtonChecked,
                        title = stringResource(R.string.action_panel_list_group),
                        subtitle = stringResource(
                            R.string.action_panel_count,
                            actionSettings.actionPanels.panels.size + actionSettings.actionGridPanels.panels.size,
                        ),
                        shape = segmentedShape(0, 4),
                        onClick = onNavToActionPanels,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Search,
                        title = stringResource(hunoia.luno.quickaccess.R.string.search_engine_settings),
                        shape = segmentedShape(1, 4),
                        onClick = onNavToSearchEngines,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Adjust,
                        title = stringResource(hunoia.luno.floatball.R.string.float_ball),
                        shape = segmentedShape(2, 4),
                        onClick = onNavToFloatBallSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Link,
                        title = stringResource(hunoia.luno.link.R.string.link_dispatch),
                        shape = segmentedShape(3, 4),
                        onClick = onNavToLinkDispatch,
                    )
                }
            }
            item(key = "actions") {
                LunoGroup(title = stringResource(R.string.action_settings_action_group)) {
                    LunoSettingsRow(
                        icon = Icons.Default.TouchApp,
                        title = stringResource(R.string.pointer),
                        subtitle = stringResource(R.string.pointer_hint),
                        shape = segmentedShape(0, 4),
                        onClick = onNavToPointerSettings,
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
                        shape = segmentedShape(1, 4),
                        onClick = onNavToScreenPickSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Crop,
                        title = stringResource(R.string.action_settings_mini_window_position),
                        shape = segmentedShape(2, 4),
                        onClick = onNavToMiniWindowSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Visibility,
                        title = stringResource(hunoia.luno.gesture.R.string.button_visibility),
                        shape = segmentedShape(3, 4),
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
                        shape = segmentedShape(0, 6),
                        onClick = ::openFontScale,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.NotificationsOff,
                        title = stringResource(R.string.persistent_notification_settings_entry),
                        subtitle = stringResource(R.string.persistent_notification_settings_entry_hint),
                        shape = segmentedShape(1, 6),
                        onClick = onNavToPersistentNotificationSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.VisibilityOff,
                        title = stringResource(R.string.status_bar_icons_entry),
                        subtitle = stringResource(R.string.status_bar_icons_entry_hint),
                        shape = segmentedShape(2, 6),
                        onClick = onNavToStatusBarIconSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.Accessibility,
                        title = stringResource(R.string.a11y_settings_entry),
                        subtitle = stringResource(R.string.a11y_settings_entry_hint),
                        shape = segmentedShape(3, 6),
                        onClick = onNavToAccessibilityManager,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.NotificationsActive,
                        title = stringResource(R.string.notification_display_settings_entry),
                        subtitle = stringResource(R.string.notification_display_settings_entry_hint),
                        shape = segmentedShape(4, 6),
                        onClick = onNavToNotificationSettings,
                    )
                    LunoSettingsRow(
                        icon = Icons.Default.VerifiedUser,
                        title = stringResource(R.string.perm_page_title),
                        shape = segmentedShape(5, 6),
                        onClick = onNavToPrivilegedPermissions,
                    )
                }
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
