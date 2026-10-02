package hunoia.luno.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.config.model.ScreenType
import hunoia.luno.runtime.condition.formatTimeOfDay
import hunoia.luno.ui.component.AppPickerSheet
import hunoia.luno.ui.component.SegmentedSwitchRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt

data class ConditionTypeSpec(
    val type: String,
    val labelRes: Int,
    val event: Boolean,
)

val LEVEL_CONDITION_TYPES: List<ConditionTypeSpec> = listOf(
    ConditionTypeSpec(ConditionType.FOREGROUND_APP, R.string.condition_foreground_app, false),
    ConditionTypeSpec(ConditionType.SCREEN_STATE, R.string.condition_screen, false),
    ConditionTypeSpec(ConditionType.BATTERY_RANGE, R.string.condition_battery_range, false),
    ConditionTypeSpec(ConditionType.CHARGING, R.string.condition_charging, false),
    ConditionTypeSpec(ConditionType.TIME_RANGE, R.string.condition_time_range, false),
    ConditionTypeSpec(ConditionType.VOLUME_RANGE, R.string.condition_volume_range, false),
    ConditionTypeSpec(ConditionType.RINGER_SILENT, R.string.condition_ringer_silent, false),
    ConditionTypeSpec(ConditionType.NETWORK_TYPE, R.string.condition_network_type, false),
    ConditionTypeSpec(ConditionType.HEADPHONES, R.string.condition_headphones, false),
    ConditionTypeSpec(ConditionType.BLUETOOTH, R.string.condition_bluetooth, false),
    ConditionTypeSpec(ConditionType.AIRPLANE_MODE, R.string.condition_airplane_mode, false),
)

val EVENT_CONDITION_TYPES: List<ConditionTypeSpec> = listOf(
    ConditionTypeSpec(ConditionType.APP_CHANGED_TO, R.string.condition_app_changed, true),
    ConditionTypeSpec(ConditionType.SCREEN_EVENT, R.string.condition_screen_event, true),
    ConditionTypeSpec(ConditionType.CHARGING_CHANGED, R.string.condition_charging_changed, true),
    ConditionTypeSpec(ConditionType.VOLUME_CHANGED, R.string.condition_volume_changed, true),
)

val CONDITION_TYPES: List<ConditionTypeSpec> = LEVEL_CONDITION_TYPES + EVENT_CONDITION_TYPES

fun defaultConditionOf(type: String): ConditionItem = when (type) {
    ConditionType.SCREEN_STATE -> ConditionItem(type, screenType = ScreenType.LOCK_SCREEN)
    ConditionType.SCREEN_EVENT -> ConditionItem(type, screenEvent = ScreenEventType.SCREEN_ON)
    ConditionType.CHARGING -> ConditionItem(type, charging = true)
    ConditionType.CHARGING_CHANGED -> ConditionItem(type, charging = true)
    ConditionType.TIME_RANGE -> ConditionItem(type, startMinute = 8 * 60, endMinute = 18 * 60)
    ConditionType.VOLUME_RANGE -> ConditionItem(type, audioStream = AudioStream.MUSIC, volumeMin = 0, volumeMax = 100)
    ConditionType.VOLUME_CHANGED -> ConditionItem(type, audioStream = AudioStream.MUSIC)
    ConditionType.RINGER_SILENT -> ConditionItem(type, silent = true)
    ConditionType.NETWORK_TYPE -> ConditionItem(type, networkType = NetworkType.WIFI)
    ConditionType.HEADPHONES -> ConditionItem(type, plugged = true)
    ConditionType.BLUETOOTH -> ConditionItem(type, expectedBluetoothAdapterOn = true)
    ConditionType.AIRPLANE_MODE -> ConditionItem(type, airplaneMode = true)
    else -> ConditionItem(type)
}

fun conditionTypeLabel(context: Context, type: String): String = context.getString(
    CONDITION_TYPES.find { it.type == type }?.labelRes
        ?: if (type == ConditionType.GROUP) R.string.condition_logic_group
        else R.string.condition_logic_group,
)

fun screenTypeLabel(context: Context, type: ScreenType): String = context.getString(
    when (type) {
        ScreenType.LOCK_SCREEN -> R.string.lock_screen
        ScreenType.LAUNCHER -> R.string.launcher
        ScreenType.LANDSCAPE -> R.string.landscape
        ScreenType.PORTRAIT -> R.string.condition_portrait
        ScreenType.KEYBOARD_INPUT -> R.string.condition_keyboard_input
    },
)

private fun screenTypeOptions(context: Context): List<String> =
    ScreenType.entries.map { screenTypeLabel(context, it) }

internal fun audioStreamLabel(context: Context, stream: AudioStream): String = context.getString(
    when (stream) {
        AudioStream.MUSIC -> R.string.audio_stream_music
        AudioStream.RING -> R.string.audio_stream_ring
        AudioStream.NOTIFICATION -> R.string.audio_stream_notification
        AudioStream.ALARM -> R.string.audio_stream_alarm
        AudioStream.VOICE -> R.string.audio_stream_voice
        AudioStream.SYSTEM -> R.string.audio_stream_system
    },
)

internal fun networkTypeLabel(context: Context, type: NetworkType): String = context.getString(
    when (type) {
        NetworkType.WIFI -> R.string.network_wifi
        NetworkType.MOBILE -> R.string.network_mobile
        NetworkType.NONE -> R.string.network_none
        NetworkType.OTHER -> R.string.network_other
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionItemFields(item: ConditionItem, onChange: (ConditionItem) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        when (item.type) {
            ConditionType.FOREGROUND_APP,
            ConditionType.APP_CHANGED_TO -> AppConditionField(
                packageNames = item.packageNames,
                onChange = { onChange(item.copy(packageNames = it)) },
            )

            ConditionType.SCREEN_STATE -> FilterChipRow(
                options = screenTypeOptions(context),
                selectedIndex = ScreenType.entries.indexOf(item.screenType ?: ScreenType.LOCK_SCREEN),
                onChange = { i -> onChange(item.copy(screenType = ScreenType.entries.getOrNull(i))) },
            )

            ConditionType.SCREEN_EVENT -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.screen_event_on),
                    stringResource(R.string.screen_event_off),
                ),
                selectedIndex = if (item.screenEvent == ScreenEventType.SCREEN_ON) 0 else 1,
                onChange = { i ->
                    onChange(item.copy(screenEvent = if (i == 0) ScreenEventType.SCREEN_ON else ScreenEventType.SCREEN_OFF))
                },
            )

            ConditionType.BATTERY_RANGE -> PercentRangeField(
                min = item.batteryLevelMin,
                max = item.batteryLevelMax,
                onChange = { lo, hi -> onChange(item.copy(batteryLevelMin = lo, batteryLevelMax = hi)) },
            )

            ConditionType.VOLUME_RANGE -> {
                Text(stringResource(R.string.volume_stream), style = MaterialTheme.typography.labelMedium)
                FilterChipRow(
                    options = AudioStream.entries.map { audioStreamLabel(context, it) },
                    selectedIndex = AudioStream.entries.indexOf(item.audioStream ?: AudioStream.MUSIC),
                    onChange = { i -> onChange(item.copy(audioStream = AudioStream.entries.getOrNull(i))) },
                )
                PercentRangeField(
                    min = item.volumeMin,
                    max = item.volumeMax,
                    onChange = { lo, hi -> onChange(item.copy(volumeMin = lo, volumeMax = hi)) },
                )
            }

            ConditionType.VOLUME_CHANGED -> {
                Text(stringResource(R.string.volume_stream), style = MaterialTheme.typography.labelMedium)
                FilterChipRow(
                    options = AudioStream.entries.map { audioStreamLabel(context, it) },
                    selectedIndex = AudioStream.entries.indexOf(item.audioStream ?: AudioStream.MUSIC),
                    onChange = { i -> onChange(item.copy(audioStream = AudioStream.entries.getOrNull(i))) },
                )
                TriStateLabelRow(
                    labels = listOf(
                        stringResource(R.string.condition_unlimited),
                        stringResource(R.string.volume_up),
                        stringResource(R.string.volume_down),
                    ),
                    selectedIndex = when (item.volumeDirection) {
                        null -> 0
                        VolumeDirection.UP -> 1
                        VolumeDirection.DOWN -> 2
                        else -> 0
                    },
                    onChange = { i ->
                        onChange(item.copy(volumeDirection = when (i) {
                            0 -> null
                            1 -> VolumeDirection.UP
                            else -> VolumeDirection.DOWN
                        }))
                    },
                )
            }

            ConditionType.CHARGING -> SegmentedSwitchRow(
                title = stringResource(R.string.condition_charging),
                checked = item.charging == true,
                onCheckedChange = { onChange(item.copy(charging = it)) },
            )

            ConditionType.CHARGING_CHANGED -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.charging_started),
                    stringResource(R.string.charging_stopped),
                ),
                selectedIndex = if (item.charging == true) 0 else 1,
                onChange = { i -> onChange(item.copy(charging = i == 1)) },
            )

            ConditionType.RINGER_SILENT -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.condition_silent),
                    stringResource(R.string.condition_ringing),
                ),
                selectedIndex = if (item.silent == true) 0 else 1,
                onChange = { i -> onChange(item.copy(silent = i == 0)) },
            )

            ConditionType.NETWORK_TYPE -> TriStateLabelRow(
                labels = NetworkType.entries.map { networkTypeLabel(context, it) },
                selectedIndex = NetworkType.entries.indexOf(item.networkType ?: NetworkType.WIFI),
                onChange = { i -> onChange(item.copy(networkType = NetworkType.entries.getOrNull(i))) },
            )

            ConditionType.HEADPHONES -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.headphones_plugged),
                    stringResource(R.string.headphones_unplugged),
                ),
                selectedIndex = if (item.plugged == true) 0 else 1,
                onChange = { i -> onChange(item.copy(plugged = i == 0)) },
            )

            ConditionType.BLUETOOTH -> {
                Text(stringResource(R.string.bluetooth_adapter), style = MaterialTheme.typography.labelMedium)
                TriStateLabelRow(
                    labels = listOf(
                        stringResource(R.string.condition_unlimited),
                        stringResource(R.string.state_on),
                        stringResource(R.string.state_off),
                    ),
                    selectedIndex = when (item.expectedBluetoothAdapterOn) {
                        null -> 0
                        true -> 1
                        false -> 2
                    },
                    onChange = { i ->
                        onChange(item.copy(expectedBluetoothAdapterOn = when (i) { 0 -> null; 1 -> true; else -> false }))
                    },
                )
                Text(stringResource(R.string.bluetooth_audio), style = MaterialTheme.typography.labelMedium)
                TriStateLabelRow(
                    labels = listOf(
                        stringResource(R.string.condition_unlimited),
                        stringResource(R.string.state_on),
                        stringResource(R.string.state_off),
                    ),
                    selectedIndex = when (item.expectedBluetoothAudioConnected) {
                        null -> 0
                        true -> 1
                        false -> 2
                    },
                    onChange = { i ->
                        onChange(item.copy(expectedBluetoothAudioConnected = when (i) { 0 -> null; 1 -> true; else -> false }))
                    },
                )
            }

            ConditionType.AIRPLANE_MODE -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.state_on),
                    stringResource(R.string.state_off),
                ),
                selectedIndex = if (item.airplaneMode == true) 0 else 1,
                onChange = { i -> onChange(item.copy(airplaneMode = i == 0)) },
            )

            ConditionType.TIME_RANGE -> TimeRangeField(
                startMinute = item.startMinute ?: 8 * 60,
                endMinute = item.endMinute ?: 18 * 60,
                onChange = { lo, hi -> onChange(item.copy(startMinute = lo, endMinute = hi)) },
            )

            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(
    options: List<String>,
    selectedIndex: Int,
    onChange: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PageGutter)) {
        options.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onChange(index) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriStateLabelRow(
    labels: List<String>,
    selectedIndex: Int,
    onChange: (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onChange(index) },
                shape = SegmentedButtonDefaults.itemShape(count = labels.size, index = index),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PercentRangeField(
    min: Int?,
    max: Int?,
    onChange: (Int, Int) -> Unit,
) {
    var sliderValue by remember(min, max) {
        mutableStateOf((min ?: 0).toFloat()..(max ?: 100).toFloat())
    }
    RangeSlider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = {
            onChange(
                sliderValue.start.roundToInt().coerceIn(0, 100),
                sliderValue.endInclusive.roundToInt().coerceIn(0, 100),
            )
        },
        valueRange = 0f..100f,
    )
    Text(
        text = "${sliderValue.start.roundToInt()}% - ${sliderValue.endInclusive.roundToInt()}%",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AppConditionField(
    packageNames: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Text(
        text = if (packageNames.isEmpty()) {
            stringResource(R.string.condition_apps_empty)
        } else {
            packageNames.joinToString(", ")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    FilledTonalButton(onClick = { showPicker = true }) {
        Text(stringResource(R.string.condition_app_select_title))
    }
    if (showPicker) {
        AppPickerSheet(
            onDismissRequest = { showPicker = false },
            selectedPackageNames = packageNames,
            onConfirm = { onChange(it) },
        )
    }
}

@Composable
private fun TimeRangeField(
    startMinute: Int,
    endMinute: Int,
    onChange: (Int, Int) -> Unit,
) {
    var picking by remember { mutableStateOf<Int?>(null) }
    val nextDaySuffix = if (startMinute > endMinute) {
        " (${stringResource(R.string.condition_next_day)})"
    } else {
        ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        OutlinedButton(onClick = { picking = 0 }) {
            Text(formatTimeOfDay(startMinute))
        }
        Text("-")
        OutlinedButton(onClick = { picking = 1 }) {
            Text(formatTimeOfDay(endMinute) + nextDaySuffix)
        }
    }

    when (picking) {
        0 -> TimePickerDialog(
            title = stringResource(R.string.condition_time_start),
            initialMinute = startMinute,
            onDismiss = { picking = null },
            onConfirm = { onChange(it, endMinute) },
        )

        1 -> TimePickerDialog(
            title = stringResource(R.string.condition_time_end),
            initialMinute = endMinute,
            onDismiss = { picking = null },
            onConfirm = { onChange(startMinute, it) },
        )

        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(timeState.hour * 60 + timeState.minute)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
