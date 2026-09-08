package hunoia.luno.ui.settings.action

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.config.model.withMiniWindowSettings
import hunoia.luno.ui.component.ExpressiveCard
import hunoia.luno.ui.component.ExpressiveSwitchItem
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.input.MyTextSlider
import hunoia.luno.ui.theme.CardShape
import hunoia.luno.ui.theme.ItemPadding
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.Spacing12
import hunoia.luno.ui.theme.Spacing14
import hunoia.luno.ui.theme.Spacing16
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val actionSettings by ConfigProvider.actionSettings.collectAsStateWithLifecycle(initialValue = ActionSettings())
    val advancedSettings by ConfigProvider.advancedSettings.collectAsStateWithLifecycle(initialValue = AdvancedSettings())
    val gestureSettings by ConfigProvider.gestureSettings.collectAsStateWithLifecycle(initialValue = GestureSettings())
    val buttons by ConfigProvider.gestureButtons.collectAsStateWithLifecycle(initialValue = emptyList())
    val subGestureSettings by ConfigProvider.subGestureSettings.collectAsStateWithLifecycle(initialValue = SubGestureSettings())

    Scaffold(
        topBar = { TopBar(onBack = onBack, title = stringResource(R.string.action_settings)) }
    ) { padding ->
        MyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing12),
        ) {
            ExpressiveCard(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.hide_gesture_button),
                subtitle = stringResource(R.string.hide_gesture_button_delay_ms),
                onClick = {},
            ) {
                HideGestureButtonControls(
                    settings = actionSettings.hideGestureButton,
                    onChange = { next -> scope.launch { ConfigProvider.updateActionSettings { it.copy(hideGestureButton = next) } } },
                )
            }

            ExpressiveCard(
                icon = Icons.Default.Swipe,
                title = stringResource(R.string.horizontal_volume_scrub),
                subtitle = stringResource(R.string.horizontal_volume_scrub_hint),
                onClick = {},
            ) {
                VolumeScrubControls(
                    settings = actionSettings.volumeScrub,
                    onChange = { next -> scope.launch { ConfigProvider.updateActionSettings { it.copy(volumeScrub = next) } } },
                )
            }

            ExpressiveCard(
                icon = Icons.Default.Crop,
                title = stringResource(R.string.mini_window_position_short),
                subtitle = stringResource(R.string.mini_window_position_hint),
                onClick = {},
            ) {
                MiniWindowControls(
                    settings = advancedSettings.miniWindowSettings(),
                    onChange = { next -> scope.launch { ConfigProvider.updateAdvancedSettings { it.withMiniWindowSettings(next) } } },
                )
            }

            ExpressiveCard(
                icon = Icons.Default.TouchApp,
                title = stringResource(R.string.pointer_continuous_mode),
                subtitle = stringResource(R.string.pointer_continuous_mode_hint),
                onClick = {},
            ) {
                PointerContinuousModeControls(
                    enabled = gestureSettings.pointer.continuousMode,
                    onChange = { next -> scope.launch { ConfigProvider.updateGestureSettings { it.copy(pointer = it.pointer.copy(continuousMode = next)) } } },
                )
            }

            buttons.sortedBy { it.id }.forEachIndexed { index, button ->
                GestureButtonOverrideCard(
                    label = button.name.ifBlank { stringResource(R.string.gesture_button_name, index + 1) },
                    button = button,
                    globalActionSettings = actionSettings,
                    globalMiniWindow = advancedSettings.miniWindowSettings(),
                    globalPointerContinuousMode = gestureSettings.pointer.continuousMode,
                    onUpdate = { updated ->
                        scope.launch {
                            ConfigProvider.updateGestureButtons { list ->
                                list.map { if (it.id == button.id) updated else it }
                            }
                        }
                    },
                )
            }

            subGestureSettings.subGestures.sortedBy { it.name.ifBlank { it.id } }.forEach { subGesture ->
                SubGestureOverrideCard(
                    label = subGesture.name.ifBlank { stringResource(R.string.action_sub_gesture) },
                    subGesture = subGesture,
                    globalActionSettings = actionSettings,
                    globalMiniWindow = advancedSettings.miniWindowSettings(),
                    globalPointerContinuousMode = gestureSettings.pointer.continuousMode,
                    onUpdate = { updated ->
                        scope.launch {
                            ConfigProvider.updateSubGestureSettings { settings ->
                                settings.copy(
                                    subGestures = settings.subGestures.map { if (it.id == subGesture.id) updated else it }
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GestureButtonOverrideCard(
    label: String,
    button: GestureButton,
    globalActionSettings: ActionSettings,
    globalMiniWindow: MiniWindowSettings,
    globalPointerContinuousMode: Boolean,
    onUpdate: (GestureButton) -> Unit,
) {
    var expanded by remember(button.id) { mutableStateOf(false) }
    val dotColor = if (button.color == android.graphics.Color.TRANSPARENT) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(button.color)
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "arrow",
    )
    Card(
        onClick = { expanded = !expanded },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing16, vertical = Spacing14),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing12),
            ) {
                Box(
                    modifier = Modifier
                        .size(MarkColorSize)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.7f)),
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = Spacing16)
                        .padding(bottom = Spacing16),
                    verticalArrangement = Arrangement.spacedBy(ItemPadding),
                ) {
                    OverrideSection(
                        title = stringResource(R.string.action_hide_gesture_button),
                        enabled = button.actionSettingsOverride.hideGestureButton != null,
                        onEnabledChange = { enabled ->
                            button.updateOverride(onUpdate) {
                                copy(hideGestureButton = if (enabled) globalActionSettings.hideGestureButton else null)
                            }
                        },
                    ) {
                        HideGestureButtonControls(
                            settings = button.actionSettingsOverride.hideGestureButton ?: globalActionSettings.hideGestureButton,
                            onChange = { next -> button.updateOverride(onUpdate) { copy(hideGestureButton = next) } },
                        )
                    }
                    OverrideSection(
                        title = stringResource(R.string.action_volume_scrub),
                        enabled = button.actionSettingsOverride.volumeScrub != null,
                        onEnabledChange = { enabled ->
                            button.updateOverride(onUpdate) {
                                copy(volumeScrub = if (enabled) globalActionSettings.volumeScrub else null)
                            }
                        },
                    ) {
                        VolumeScrubControls(
                            settings = button.actionSettingsOverride.volumeScrub ?: globalActionSettings.volumeScrub,
                            onChange = { next -> button.updateOverride(onUpdate) { copy(volumeScrub = next) } },
                        )
                    }
                    OverrideSection(
                        title = stringResource(R.string.mini_window_position_short),
                        enabled = button.actionSettingsOverride.miniWindow != null,
                        onEnabledChange = { enabled ->
                            button.updateOverride(onUpdate) { copy(miniWindow = if (enabled) globalMiniWindow else null) }
                        },
                    ) {
                        MiniWindowControls(
                            settings = button.actionSettingsOverride.miniWindow ?: globalMiniWindow,
                            onChange = { next -> button.updateOverride(onUpdate) { copy(miniWindow = next) } },
                        )
                    }
                    OverrideSection(
                        title = stringResource(R.string.pointer_continuous_mode),
                        enabled = button.actionSettingsOverride.pointerContinuousMode != null,
                        onEnabledChange = { enabled ->
                            button.updateOverride(onUpdate) { copy(pointerContinuousMode = if (enabled) globalPointerContinuousMode else null) }
                        },
                    ) {
                        PointerContinuousModeControls(
                            enabled = button.actionSettingsOverride.pointerContinuousMode ?: globalPointerContinuousMode,
                            onChange = { next -> button.updateOverride(onUpdate) { copy(pointerContinuousMode = next) } },
                        )
                    }
                }
            }
        }
    }
}

private fun GestureButton.updateOverride(
    onUpdate: (GestureButton) -> Unit,
    transform: GestureButtonActionSettingsOverride.() -> GestureButtonActionSettingsOverride,
) {
    onUpdate(copy(actionSettingsOverride = actionSettingsOverride.transform()))
}

@Composable
private fun SubGestureOverrideCard(
    label: String,
    subGesture: SubGesture,
    globalActionSettings: ActionSettings,
    globalMiniWindow: MiniWindowSettings,
    globalPointerContinuousMode: Boolean,
    onUpdate: (SubGesture) -> Unit,
) {
    var expanded by remember(subGesture.id) { mutableStateOf(false) }
    val dotColor = if (subGesture.color == android.graphics.Color.TRANSPARENT) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(subGesture.color)
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "arrow",
    )
    Card(
        onClick = { expanded = !expanded },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing16, vertical = Spacing14),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing12),
            ) {
                Box(
                    modifier = Modifier
                        .size(MarkColorSize)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.7f)),
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = Spacing16)
                        .padding(bottom = Spacing16),
                    verticalArrangement = Arrangement.spacedBy(ItemPadding),
                ) {
                    OverrideSection(
                        title = stringResource(R.string.action_hide_gesture_button),
                        enabled = subGesture.actionSettingsOverride.hideGestureButton != null,
                        onEnabledChange = { enabled ->
                            subGesture.updateOverride(onUpdate) {
                                copy(hideGestureButton = if (enabled) globalActionSettings.hideGestureButton else null)
                            }
                        },
                    ) {
                        HideGestureButtonControls(
                            settings = subGesture.actionSettingsOverride.hideGestureButton ?: globalActionSettings.hideGestureButton,
                            onChange = { next -> subGesture.updateOverride(onUpdate) { copy(hideGestureButton = next) } },
                        )
                    }
                    OverrideSection(
                        title = stringResource(R.string.action_volume_scrub),
                        enabled = subGesture.actionSettingsOverride.volumeScrub != null,
                        onEnabledChange = { enabled ->
                            subGesture.updateOverride(onUpdate) {
                                copy(volumeScrub = if (enabled) globalActionSettings.volumeScrub else null)
                            }
                        },
                    ) {
                        VolumeScrubControls(
                            settings = subGesture.actionSettingsOverride.volumeScrub ?: globalActionSettings.volumeScrub,
                            onChange = { next -> subGesture.updateOverride(onUpdate) { copy(volumeScrub = next) } },
                        )
                    }
                    OverrideSection(
                        title = stringResource(R.string.mini_window_position_short),
                        enabled = subGesture.actionSettingsOverride.miniWindow != null,
                        onEnabledChange = { enabled ->
                            subGesture.updateOverride(onUpdate) { copy(miniWindow = if (enabled) globalMiniWindow else null) }
                        },
                    ) {
                        MiniWindowControls(
                            settings = subGesture.actionSettingsOverride.miniWindow ?: globalMiniWindow,
                            onChange = { next -> subGesture.updateOverride(onUpdate) { copy(miniWindow = next) } },
                        )
                    }
                    OverrideSection(
                        title = stringResource(R.string.pointer_continuous_mode),
                        enabled = subGesture.actionSettingsOverride.pointerContinuousMode != null,
                        onEnabledChange = { enabled ->
                            subGesture.updateOverride(onUpdate) { copy(pointerContinuousMode = if (enabled) globalPointerContinuousMode else null) }
                        },
                    ) {
                        PointerContinuousModeControls(
                            enabled = subGesture.actionSettingsOverride.pointerContinuousMode ?: globalPointerContinuousMode,
                            onChange = { next -> subGesture.updateOverride(onUpdate) { copy(pointerContinuousMode = next) } },
                        )
                    }
                }
            }
        }
    }
}

private fun SubGesture.updateOverride(
    onUpdate: (SubGesture) -> Unit,
    transform: GestureButtonActionSettingsOverride.() -> GestureButtonActionSettingsOverride,
) {
    onUpdate(copy(actionSettingsOverride = actionSettingsOverride.transform()))
}

@Composable
private fun OverrideSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ItemPadding)) {
        ExpressiveSwitchItem(
            title = title,
            subtitle = if (enabled) stringResource(R.string.custom_action_setting) else stringResource(R.string.follow_global_action_setting),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
        if (enabled) {
            content()
            TextButton(onClick = { onEnabledChange(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.restore_follow_global))
            }
        }
    }
}

@Composable
private fun HideGestureButtonControls(
    settings: ActionSettings.HideGestureButton,
    onChange: (ActionSettings.HideGestureButton) -> Unit,
) {
    var localDelay by remember(settings.delayMs) { mutableStateOf(settings.delayMs.toFloat()) }
    MyTextSlider(
        value = localDelay,
        onValueChange = { localDelay = it },
        onValueChangeFinished = { onChange(settings.copy(delayMs = localDelay.toLong())) },
        text = stringResource(R.string.hide_gesture_button_delay_ms),
        valueDisplay = stringResource(R.string.current_value_ms, localDelay.toLong()),
        valueRange = 0f..3000f,
    )
}

@Composable
private fun VolumeScrubControls(
    settings: ActionSettings.VolumeScrub,
    onChange: (ActionSettings.VolumeScrub) -> Unit,
) {
    ExpressiveSwitchItem(
        title = stringResource(R.string.horizontal_volume_scrub),
        subtitle = stringResource(R.string.horizontal_volume_scrub_hint),
        checked = settings.horizontalEnabled,
        onCheckedChange = { onChange(settings.copy(horizontalEnabled = it)) },
    )
    var localStep by remember(settings.stepThresholdDp) { mutableStateOf(settings.stepThresholdDp.toFloat()) }
    MyTextSlider(
        value = localStep,
        onValueChange = { localStep = it },
        onValueChangeFinished = { onChange(settings.copy(stepThresholdDp = localStep.roundToInt())) },
        text = stringResource(R.string.volume_scrub_sensitivity),
        valueDisplay = "${localStep.roundToInt()}dp",
        valueRange = 4f..64f,
    )
}

@Composable
private fun MiniWindowControls(
    settings: MiniWindowSettings,
    onChange: (MiniWindowSettings) -> Unit,
) {
    var horizontalBias by remember(settings.horizontalBias) { mutableStateOf(settings.horizontalBias) }
    var verticalBias by remember(settings.verticalBias) { mutableStateOf(settings.verticalBias) }
    var widthFraction by remember(settings.widthFraction) { mutableStateOf(settings.widthFraction) }
    var heightFraction by remember(settings.heightFraction) { mutableStateOf(settings.heightFraction) }
    ExpressiveSwitchItem(
        title = stringResource(R.string.custom_position_size),
        subtitle = stringResource(R.string.mini_window_position_hint),
        checked = settings.overrideBounds,
        onCheckedChange = { onChange(settings.copy(overrideBounds = it)) },
    )
    MyTextSlider(
        value = horizontalBias,
        onValueChange = { horizontalBias = it.coerceIn(-1f, 1f) },
        onValueChangeFinished = { onChange(settings.copy(horizontalBias = horizontalBias)) },
        text = stringResource(R.string.horizontal_offset),
        valueDisplay = "${(horizontalBias * 100).roundToInt()}%",
        valueRange = -1f..1f,
    )
    MyTextSlider(
        value = verticalBias,
        onValueChange = { verticalBias = it.coerceIn(-1f, 1f) },
        onValueChangeFinished = { onChange(settings.copy(verticalBias = verticalBias)) },
        text = stringResource(R.string.vertical_offset),
        valueDisplay = "${(verticalBias * 100).roundToInt()}%",
        valueRange = -1f..1f,
    )
    MyTextSlider(
        value = widthFraction,
        onValueChange = { widthFraction = it.coerceIn(0.2f, 1.5f) },
        onValueChangeFinished = { onChange(settings.copy(widthFraction = widthFraction)) },
        text = stringResource(R.string.width),
        valueDisplay = "${(widthFraction * 100).roundToInt()}%",
        valueRange = 0.2f..1.5f,
    )
    MyTextSlider(
        value = heightFraction,
        onValueChange = { heightFraction = it.coerceIn(0.2f, 1.5f) },
        onValueChangeFinished = { onChange(settings.copy(heightFraction = heightFraction)) },
        text = stringResource(R.string.height),
        valueDisplay = "${(heightFraction * 100).roundToInt()}%",
        valueRange = 0.2f..1.5f,
    )
}

@Composable
private fun PointerContinuousModeControls(enabled: Boolean, onChange: (Boolean) -> Unit) {
    ExpressiveSwitchItem(
        title = stringResource(R.string.pointer_continuous_mode),
        subtitle = stringResource(R.string.pointer_continuous_mode_hint),
        checked = enabled,
        onCheckedChange = onChange,
    )
    Spacer(Modifier.height(Spacing12))
}
