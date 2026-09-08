package hunoia.luno.ui.settings.gesture.button
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEach
import com.aaron.compose.ktx.onSingleClick
import hunoia.luno.R
import hunoia.luno.bridge.vibration.MaxCustomVibrationMs
import hunoia.luno.bridge.vibration.MinCustomVibrationMs
import hunoia.luno.bridge.vibration.VibrationEffects
import hunoia.luno.config.defaults.SettingsUiDefaults.getPredefinedVibrationEffectText
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.ExpressiveSwitchItem
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.input.MyTextSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureButtonVibrationContent(
    button: GestureButton,
    vm: GestureButtonSettingsVM
) {
    val scrollState = rememberScrollState()
    MyColumn(verticalArrangement = Arrangement.spacedBy(Spacing8)) {
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onSlideVibrateChange(it) },
            checked = button.slideVibrate,
            title = stringResource(R.string.vibration_slide)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onLongSlideVibrateChange(it) },
            checked = button.longSlideVibrate,
            title = stringResource(R.string.vibration_long_slide)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onTapVibrateChange(it) },
            checked = button.tapVibrate,
            title = stringResource(R.string.vibration_tap)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onLongPressVibrateChange(it) },
            checked = button.longPressVibrate,
            title = stringResource(R.string.vibration_long_press)
        )
        ExpressiveSwitchItem(
            onCheckedChange = { vm.onVibrateImmediatelyChange(it) },
            checked = button.vibrateImmediately,
            title = stringResource(R.string.vibrate_immediately),
            subtitle = stringResource(R.string.vibrate_immediately_hint)
        )
        VibrationEffectSelector(
            effect = button.vibrationEffect,
            onEffectChange = { vm.onVibrationEffectChange(it) }
        )
        MyTextSlider(
            enabled = button.vibrationEffect == VibrationEffects.None,
            value = button.customVibrationMs.toFloat(),
            onValueChange = { vm.onCustomVibrationMsChange(it) },
            text = stringResource(R.string.vibration_strength),
            valueDisplay = "${button.customVibrationMs}ms",
            valueRange = MinCustomVibrationMs.toFloat()..MaxCustomVibrationMs.toFloat()
        )
    }
}

@Composable
fun VibrationEffectSelector(
    effect: VibrationEffects,
    onEffectChange: (VibrationEffects) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinItemHeightNoSecondary)
            .onSingleClick { showDropdown = true }
            .padding(horizontal = ContentPaddingHorizontal, vertical = ContentPaddingVerticalWithSection),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemPadding)
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.predefined_style),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1
        )
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getPredefinedVibrationEffectText(effect),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.vibration_style)
                )
            }
            DropdownMenu(
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                listOf(
                    VibrationEffects.Tick,
                    VibrationEffects.Click,
                    VibrationEffects.HeavyClick,
                    VibrationEffects.None
                ).fastForEach { effectValue ->
                    key(effectValue) {
                        DropdownMenuItem(
                            onClick = {
                                onEffectChange(effectValue)
                                showDropdown = false
                            },
                            text = { Text(text = getPredefinedVibrationEffectText(effectValue)) }
                        )
                    }
                }
            }
        }
    }
}
