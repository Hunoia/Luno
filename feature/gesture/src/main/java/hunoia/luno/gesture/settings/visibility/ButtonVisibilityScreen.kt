package hunoia.luno.gesture.settings.visibility

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.gesture.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.config.model.VisibilityScreen
import hunoia.luno.config.model.VisibilityTargetMode
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.gesture.settings.visibility.ButtonVisibilityText.summary
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/** 触钮显隐场景列表：顺序即优先级，最后命中的场景生效。 */
@Composable
fun ButtonVisibilityScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val initial by GestureConfig.initial.collectAsStateWithLifecycle(initialValue = InitialSettings())
    val actionSettings by TuneConfig.action.collectAsStateWithLifecycle(initialValue = ActionSettings())
    val advanced by TuneConfig.advanced.collectAsStateWithLifecycle(initialValue = AdvancedSettings())
    val scope = rememberCoroutineScope()
    val profiles = initial.buttonVisibility.profiles

    fun submit(next: List<VisibilityProfile>) {
        scope.launch {
            GestureConfig.updateInitial { current ->
                current.copy(
                    buttonVisibility = current.buttonVisibility.copy(profiles = next),
                )
            }
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.button_visibility),
        onBack = onBack,
        state = rememberLazyListState(),
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = { onEdit(NEW_VISIBILITY_PROFILE_ID) },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.button_visibility_new),
            )
        },
    ) {
        // 系统级显隐：应用列表与 Luno 后台（与下方按场景的触钮/悬浮球显隐区分）。
        item(key = "system_visibility") {
            LunoGroup(title = stringResource(R.string.button_visibility_system_group)) {
                LunoSwitchRow(
                    icon = Icons.Default.Android,
                    title = stringResource(R.string.apps_show_system),
                    subtitle = stringResource(R.string.apps_show_system_hint),
                    checked = actionSettings.showSystemAppsInAppList,
                    onCheckedChange = { enabled ->
                        scope.launch { TuneConfig.updateAction { it.copy(showSystemAppsInAppList = enabled) } }
                    },
                    shape = segmentedShape(0, 2),
                )
                LunoSwitchRow(
                    icon = Icons.Default.VisibilityOff,
                    title = stringResource(R.string.hide_luno_from_recents),
                    subtitle = stringResource(R.string.hide_luno_from_recents_hint),
                    checked = advanced.hideLunoFromRecents,
                    onCheckedChange = { hidden ->
                        scope.launch { TuneConfig.updateAdvanced { it.copy(hideLunoFromRecents = hidden) } }
                    },
                    shape = segmentedShape(1, 2),
                )
            }
        }
        item(key = "profiles") {
            if (profiles.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.button_visibility_empty_desc),
                    icon = Icons.TwoTone.Visibility,
                )
            } else {
                LunoGroup(title = stringResource(R.string.button_visibility_scene_group)) {
                    profiles.forEachIndexed { index, profile ->
                        LunoSwitchRow(
                            title = profile.title(),
                            subtitle = summary(profile),
                            checked = profile.enabled,
                            shape = segmentedShape(index, profiles.size),
                            onClick = { onEdit(profile.id) },
                            onCheckedChange = { enabled ->
                                submit(
                                    profiles.map { if (it.id == profile.id) it.copy(enabled = enabled) else it },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisibilityProfile.title(): String =
    name.ifBlank { summary(this) }

internal object ButtonVisibilityText {

    @Composable
    fun summary(profile: VisibilityProfile): String {
        val apps = when {
            profile.packageNames.isEmpty() -> stringResource(R.string.button_visibility_summary_all_apps)
            else -> stringResource(R.string.button_visibility_apps_count, profile.packageNames.size)
        }
        val screens = when {
            profile.screens.isEmpty() -> stringResource(R.string.button_visibility_summary_all_screens)
            else -> profile.screens.map { it.label() }.joinToString(" / ")
        }
        val action = stringResource(
            if (profile.visible) R.string.button_visibility_summary_show
            else R.string.button_visibility_summary_hide,
        )
        val target = ButtonVisibilityText.targetSummary(profile)
        return "$apps · $screens · $action · $target"
    }

    /** 作用对象摘要：设置列表与编辑页共用，避免两处拼装分叉。 */
    @Composable
    fun targetSummary(profile: VisibilityProfile): String = when (profile.mode) {
        VisibilityTargetMode.ALL -> stringResource(R.string.button_visibility_target_all)
        VisibilityTargetMode.ONLY -> stringResource(
            R.string.button_visibility_target_only,
        ) + " · " + stringResource(R.string.button_visibility_target_count, profile.targetIds.size)
        VisibilityTargetMode.EXCEPT -> stringResource(
            R.string.button_visibility_target_except,
        ) + " · " + stringResource(R.string.button_visibility_target_count, profile.targetIds.size)
    }

    @Composable
    fun VisibilityScreen.label(): String = when (this) {
        VisibilityScreen.LOCK_SCREEN -> stringResource(R.string.button_visibility_screen_lock)
        VisibilityScreen.LAUNCHER -> stringResource(R.string.button_visibility_screen_launcher)
        VisibilityScreen.LANDSCAPE -> stringResource(R.string.button_visibility_screen_landscape)
        VisibilityScreen.PORTRAIT -> stringResource(R.string.button_visibility_screen_portrait)
        VisibilityScreen.KEYBOARD_INPUT -> stringResource(R.string.button_visibility_screen_keyboard)
    }
}

const val NEW_VISIBILITY_PROFILE_ID = "new"
