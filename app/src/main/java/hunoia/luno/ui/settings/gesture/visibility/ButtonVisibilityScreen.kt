package hunoia.luno.ui.settings.gesture.visibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.config.model.VisibilityScreen
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.settings.gesture.visibility.ButtonVisibilityText.summary
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch

/** 触钮显隐场景列表：顺序即优先级，最后命中的场景生效。 */
@Composable
fun ButtonVisibilityScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val initial by GestureConfig.initial.collectAsStateWithLifecycle(initialValue = InitialSettings())
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
        floatingContent = {
            Box(modifier = Modifier.fillMaxSize()) {
                FloatingActionButton(
                    onClick = { onEdit(NEW_VISIBILITY_PROFILE_ID) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = PageGutter, bottom = CardInnerSpacing)
                        .size(64.dp),
                    shape = FloatingActionButtonDefaults.shape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.button_visibility_new),
                    )
                }
            }
        },
    ) {
        if (profiles.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    message = stringResource(R.string.button_visibility_empty_desc),
                    icon = Icons.TwoTone.Visibility,
                )
            }
        } else {
            itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
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
        val target = when {
            profile.target == hunoia.luno.action.model.ButtonTarget.ALL ->
                stringResource(R.string.button_visibility_target_all)
            profile.buttonIds.isEmpty() -> stringResource(R.string.button_visibility_target_all)
            else -> stringResource(R.string.button_visibility_target_count, profile.buttonIds.size)
        }
        return "$apps · $screens · $action · $target"
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
