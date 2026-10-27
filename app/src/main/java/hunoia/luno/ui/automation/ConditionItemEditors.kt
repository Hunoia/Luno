package hunoia.luno.ui.automation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.VolumeDirection
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.ConnectionDirection
import hunoia.luno.config.model.KeyboardEventType
import hunoia.luno.config.model.NotificationMatchMode
import hunoia.luno.config.model.PackageChangeAction
import hunoia.luno.config.model.PlaybackDirection
import hunoia.luno.config.model.RingerMode
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.config.model.ScreenType
import hunoia.luno.domain.automation.formatTimeOfDay
import hunoia.luno.service.DispatchClient
import hunoia.luno.ui.component.sheet.AppPickerSheet
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.sheet.CustomDialog
data class ConditionTypeSpec(
    val type: String,
    val labelRes: Int,
)
val LEVEL_CONDITION_TYPES: List<ConditionTypeSpec> = listOf(
    ConditionTypeSpec(ConditionType.FOREGROUND_APP, R.string.condition_foreground_app),
    ConditionTypeSpec(ConditionType.SCREEN_STATE, R.string.condition_screen),
    ConditionTypeSpec(ConditionType.BATTERY_RANGE, R.string.condition_battery_range),
    ConditionTypeSpec(ConditionType.CHARGING, R.string.condition_charging),
    ConditionTypeSpec(ConditionType.TIME_RANGE, R.string.condition_time_range),
    ConditionTypeSpec(ConditionType.VOLUME_RANGE, R.string.condition_volume_range),
    ConditionTypeSpec(ConditionType.RINGER_SILENT, R.string.condition_ringer_silent),
    ConditionTypeSpec(ConditionType.MUSIC_PLAYING, R.string.condition_music_playing),
    ConditionTypeSpec(ConditionType.NETWORK_TYPE, R.string.condition_network_type),
    ConditionTypeSpec(ConditionType.WIFI_SSID, R.string.condition_wifi_ssid),
    ConditionTypeSpec(ConditionType.HEADPHONES, R.string.condition_headphones),
    ConditionTypeSpec(ConditionType.BLUETOOTH, R.string.condition_bluetooth),
    ConditionTypeSpec(ConditionType.AIRPLANE_MODE, R.string.condition_airplane_mode),
    ConditionTypeSpec(ConditionType.TORCH_ON, R.string.condition_torch_on),
    ConditionTypeSpec(ConditionType.DND_ON, R.string.condition_dnd_on),
    ConditionTypeSpec(ConditionType.NOTIFICATION_EXISTS, R.string.condition_notification_exists),
    ConditionTypeSpec(ConditionType.VAR_COMPARE, R.string.condition_var_compare),
)
val EVENT_CONDITION_TYPES: List<ConditionTypeSpec> = listOf(
    ConditionTypeSpec(ConditionType.APP_CHANGED_TO, R.string.condition_app_changed),
    ConditionTypeSpec(ConditionType.SCREEN_EVENT, R.string.condition_screen_event),
    ConditionTypeSpec(ConditionType.KEYBOARD_EVENT, R.string.condition_keyboard_event),
    ConditionTypeSpec(ConditionType.CHARGING_CHANGED, R.string.condition_charging_changed),
    ConditionTypeSpec(ConditionType.VOLUME_CHANGED, R.string.condition_volume_changed),
    ConditionTypeSpec(ConditionType.MUSIC_CHANGED, R.string.condition_music_changed),
    ConditionTypeSpec(ConditionType.NOTIFICATION_POSTED, R.string.condition_notification_posted),
    ConditionTypeSpec(ConditionType.NOTIFICATION_REMOVED, R.string.condition_notification_removed),
    ConditionTypeSpec(ConditionType.APP_PACKAGE_CHANGED, R.string.condition_package_changed),
    ConditionTypeSpec(ConditionType.INTERVAL, R.string.condition_interval),
    ConditionTypeSpec(ConditionType.SHARE_RECEIVED, R.string.condition_share_received),
    ConditionTypeSpec(ConditionType.CLIPBOARD_CHANGED, R.string.condition_clipboard_changed),
    ConditionTypeSpec(ConditionType.BLUETOOTH_CHANGED, R.string.condition_bluetooth_changed),
    ConditionTypeSpec(ConditionType.HEADPHONES_CHANGED, R.string.condition_headphones_changed),
    ConditionTypeSpec(ConditionType.SCHEDULED_TIME, R.string.condition_scheduled_time),
    ConditionTypeSpec(ConditionType.GESTURE_PERFORMED, R.string.condition_gesture_performed),
    ConditionTypeSpec(ConditionType.TORCH_CHANGED, R.string.condition_torch_changed),
    ConditionTypeSpec(ConditionType.DND_CHANGED, R.string.condition_dnd_changed),
)
/**
 * 手动触发：独立分组（既非状态也非事件），只供手势/runRule 调用，引擎不订阅。
 * 取反无意义，编辑器不给取反开关，求值层也无视取反。
 */
val MANUAL_CONDITION_TYPES: List<ConditionTypeSpec> = listOf(
    ConditionTypeSpec(ConditionType.MANUAL, R.string.condition_manual),
)
val CONDITION_TYPES: List<ConditionTypeSpec> =
    LEVEL_CONDITION_TYPES + EVENT_CONDITION_TYPES + MANUAL_CONDITION_TYPES

/**
 * 状态↔变化成对表（键=状态类型，值=变化类型）：条件编辑 sheet 顶部切换用。
 * 选择器的"变化事件"组隐藏这些变化条目（由状态条目内切换进入），
 * 标签查找仍用全量 [CONDITION_TYPES]，旧规则正常显示编辑。
 * 蓝牙不并：状态是"适配器+音频"两个期望值，变化只有音频连接方向，合会丢字段。
 */
val PAIRED_CONDITION_TYPES: Map<String, String> = mapOf(
    ConditionType.CHARGING to ConditionType.CHARGING_CHANGED,
    ConditionType.HEADPHONES to ConditionType.HEADPHONES_CHANGED,
    ConditionType.MUSIC_PLAYING to ConditionType.MUSIC_CHANGED,
    ConditionType.TORCH_ON to ConditionType.TORCH_CHANGED,
    ConditionType.DND_ON to ConditionType.DND_CHANGED,
    ConditionType.FOREGROUND_APP to ConditionType.APP_CHANGED_TO,
)

/** 变化类型 → 状态类型（反查，切换与 sheet 判定用）。 */
val PAIRED_STATE_TYPES: Map<String, String> =
    PAIRED_CONDITION_TYPES.entries.associate { (state, change) -> change to state }

/** 选择页事件列表：成对"变化"条目从这里隐藏；标签查找仍用全量 [CONDITION_TYPES]。 */
val EVENT_PICKER_TYPES: List<ConditionTypeSpec> =
    EVENT_CONDITION_TYPES.filterNot { it.type in PAIRED_CONDITION_TYPES.values }

/**
 * 状态↔变化切换：换 ConditionType 并按对映射字段（变体字段保留，便于来回切）。
 * 映射口径：值↔方向按"同一语义"对齐（true/正→开始，false/null→停止）；
 * 变化侧"不限"切回状态时取该域的默认正值（true/已插入/STARTED 对应态）。
 */
internal fun ConditionItem.switchConditionMode(toChange: Boolean): ConditionItem {
    val target = (if (toChange) PAIRED_CONDITION_TYPES else PAIRED_STATE_TYPES)[type]
        ?: return this
    return when (target) {
        ConditionType.CHARGING_CHANGED -> copy(type = target, charging = charging == true)
        ConditionType.HEADPHONES_CHANGED -> copy(
            type = target,
            connectionDirection = if (plugged == true) ConnectionDirection.CONNECTED
            else ConnectionDirection.DISCONNECTED,
        )
        ConditionType.MUSIC_CHANGED -> copy(
            type = target,
            playbackDirection = if (musicPlaying == true) PlaybackDirection.STARTED
            else PlaybackDirection.STOPPED,
        )
        ConditionType.TORCH_CHANGED -> copy(type = target, torchChangedTo = torchOn == true)
        ConditionType.DND_CHANGED -> copy(type = target, dndChangedTo = dndOn == true)
        ConditionType.APP_CHANGED_TO -> copy(type = target)

        ConditionType.CHARGING -> copy(type = target, charging = charging ?: true)
        ConditionType.HEADPHONES -> copy(
            type = target,
            plugged = connectionDirection != ConnectionDirection.DISCONNECTED,
        )
        ConditionType.MUSIC_PLAYING -> copy(
            type = target,
            musicPlaying = playbackDirection != PlaybackDirection.STOPPED,
        )
        ConditionType.TORCH_ON -> copy(type = target, torchOn = torchChangedTo ?: true)
        ConditionType.DND_ON -> copy(type = target, dndOn = dndChangedTo ?: true)
        ConditionType.FOREGROUND_APP -> copy(type = target)
        else -> copy(type = target)
    }
}

/** 状态/变化模式切换行（成对条件专用）。 */
@Composable
internal fun ConditionModeToggle(
    isChange: Boolean,
    onModeChange: (Boolean) -> Unit,
) {
    TriStateLabelRow(
        labels = listOf(
            stringResource(R.string.condition_mode_state),
            stringResource(R.string.condition_mode_change),
        ),
        selectedIndex = if (isChange) 1 else 0,
        onIntChange = { i -> onModeChange(i == 1) },
    )
}
fun defaultConditionOf(type: String): ConditionItem = when (type) {
    ConditionType.SCREEN_STATE -> ConditionItem(type, screenType = ScreenType.LOCK_SCREEN)
    ConditionType.SCREEN_EVENT -> ConditionItem(type, screenEvent = ScreenEventType.SCREEN_ON)
    ConditionType.CHARGING -> ConditionItem(type, charging = true)
    ConditionType.CHARGING_CHANGED -> ConditionItem(type, charging = true)
    ConditionType.TIME_RANGE -> ConditionItem(type, startMinute = 8 * 60, endMinute = 18 * 60)
    ConditionType.VOLUME_RANGE -> ConditionItem(type, audioStream = AudioStream.MUSIC, volumeMin = 0, volumeMax = 100)
    ConditionType.VOLUME_CHANGED -> ConditionItem(type, audioStream = AudioStream.MUSIC)
    ConditionType.RINGER_SILENT -> ConditionItem(type, ringerMode = RingerMode.SILENT)
    ConditionType.NETWORK_TYPE -> ConditionItem(type, networkType = NetworkType.WIFI)
    ConditionType.HEADPHONES -> ConditionItem(type, plugged = true)
    ConditionType.BLUETOOTH -> ConditionItem(type, expectedBluetoothAdapterOn = true)
    ConditionType.AIRPLANE_MODE -> ConditionItem(type, airplaneMode = true)
    ConditionType.NOTIFICATION_POSTED -> ConditionItem(type)
    ConditionType.NOTIFICATION_REMOVED -> ConditionItem(type)
    ConditionType.NOTIFICATION_EXISTS -> ConditionItem(type, notifyExists = true)
    ConditionType.APP_PACKAGE_CHANGED -> ConditionItem(type)
    ConditionType.INTERVAL -> ConditionItem(type, intervalMinutes = 5)
    ConditionType.SHARE_RECEIVED -> ConditionItem(type)
    ConditionType.CLIPBOARD_CHANGED -> ConditionItem(type)
    ConditionType.WIFI_SSID -> ConditionItem(type)
    ConditionType.BLUETOOTH_CHANGED -> ConditionItem(type, connectionDirection = ConnectionDirection.CONNECTED)
    ConditionType.HEADPHONES_CHANGED -> ConditionItem(type, connectionDirection = ConnectionDirection.CONNECTED)
    ConditionType.SCHEDULED_TIME -> ConditionItem(type, scheduledMinutes = listOf(8 * 60))
    ConditionType.GESTURE_PERFORMED -> ConditionItem(type)
    ConditionType.KEYBOARD_EVENT -> ConditionItem(type, keyboardEvent = KeyboardEventType.SHOWN)
    ConditionType.MUSIC_PLAYING -> ConditionItem(type, musicPlaying = true)
    ConditionType.MUSIC_CHANGED -> ConditionItem(type, playbackDirection = PlaybackDirection.STARTED)
    ConditionType.TORCH_ON -> ConditionItem(type, torchOn = true)
    ConditionType.TORCH_CHANGED -> ConditionItem(type, torchChangedTo = true)
    ConditionType.DND_ON -> ConditionItem(type, dndOn = true)
    ConditionType.DND_CHANGED -> ConditionItem(type, dndChangedTo = true)
    ConditionType.VAR_COMPARE -> ConditionItem(
        type,
        compareOp = hunoia.luno.config.model.VariableCompareOp.EQUALS,
    )
    ConditionType.GROUP -> ConditionItem(type, group = WhenGroup())
    else -> ConditionItem(type)
}
fun variableCompareOpLabel(context: Context, op: String): String = context.getString(
    when (op) {
        hunoia.luno.config.model.VariableCompareOp.EQUALS -> R.string.condition_var_op_eq
        hunoia.luno.config.model.VariableCompareOp.NOT_EQUALS -> R.string.condition_var_op_neq
        hunoia.luno.config.model.VariableCompareOp.GREATER -> R.string.condition_var_op_gt
        hunoia.luno.config.model.VariableCompareOp.GREATER_OR_EQUALS -> R.string.condition_var_op_gte
        hunoia.luno.config.model.VariableCompareOp.LESS -> R.string.condition_var_op_lt
        hunoia.luno.config.model.VariableCompareOp.LESS_OR_EQUALS -> R.string.condition_var_op_lte
        hunoia.luno.config.model.VariableCompareOp.CONTAINS -> R.string.condition_var_op_contains
        hunoia.luno.config.model.VariableCompareOp.STARTS_WITH -> R.string.condition_var_op_starts
        hunoia.luno.config.model.VariableCompareOp.ENDS_WITH -> R.string.condition_var_op_ends
        hunoia.luno.config.model.VariableCompareOp.IS_EMPTY -> R.string.condition_var_op_empty
        else -> R.string.condition_var_op_not_empty
    },
)

fun conditionTypeLabel(context: Context, type: String): String = context.getString(
    CONDITION_TYPES.find { it.type == type }?.labelRes ?: R.string.condition_logic_group,
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
fun ConditionItemFields(item: ConditionItem, onConditionChange: (ConditionItem) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        when (item.type) {
            ConditionType.FOREGROUND_APP,
            ConditionType.APP_CHANGED_TO -> AppConditionField(
                packageNames = item.packageNames,
                onPackagesChange = { onConditionChange(item.copy(packageNames = it)) },
            )
            ConditionType.SCREEN_STATE -> FilterChipRow(
                options = screenTypeOptions(context),
                selectedIndex = ScreenType.entries.indexOf(item.screenType ?: ScreenType.LOCK_SCREEN),
                onIntChange = { i -> onConditionChange(item.copy(screenType = ScreenType.entries.getOrNull(i))) },
            )
            ConditionType.SCREEN_EVENT -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.screen_event_on),
                    stringResource(R.string.screen_event_off),
                    stringResource(R.string.screen_event_unlock),
                ),
                selectedIndex = when (item.screenEvent) {
                    ScreenEventType.SCREEN_OFF -> 1
                    ScreenEventType.USER_UNLOCK -> 2
                    else -> 0
                },
                onIntChange = { i ->
                    onConditionChange(item.copy(screenEvent = when (i) {
                        1 -> ScreenEventType.SCREEN_OFF
                        2 -> ScreenEventType.USER_UNLOCK
                        else -> ScreenEventType.SCREEN_ON
                    }))
                },
            )
            ConditionType.KEYBOARD_EVENT -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.keyboard_shown),
                    stringResource(R.string.keyboard_hidden),
                ),
                selectedIndex = if (item.keyboardEvent == KeyboardEventType.HIDDEN) 1 else 0,
                onIntChange = { i ->
                    onConditionChange(item.copy(
                        keyboardEvent = if (i == 0) KeyboardEventType.SHOWN else KeyboardEventType.HIDDEN,
                    ))
                },
            )
            ConditionType.BATTERY_RANGE -> PercentRangeField(
                min = item.batteryLevelMin,
                max = item.batteryLevelMax,
                onRangeChange = { lo, hi -> onConditionChange(item.copy(batteryLevelMin = lo, batteryLevelMax = hi)) },
            )
            ConditionType.VOLUME_RANGE -> {
                Text(stringResource(R.string.volume_stream), style = MaterialTheme.typography.labelMedium)
                FilterChipRow(
                    options = AudioStream.entries.map { audioStreamLabel(context, it) },
                    selectedIndex = AudioStream.entries.indexOf(item.audioStream ?: AudioStream.MUSIC),
                    onIntChange = { i -> onConditionChange(item.copy(audioStream = AudioStream.entries.getOrNull(i))) },
                )
                PercentRangeField(
                    min = item.volumeMin,
                    max = item.volumeMax,
                    onRangeChange = { lo, hi -> onConditionChange(item.copy(volumeMin = lo, volumeMax = hi)) },
                )
            }
            ConditionType.MUSIC_PLAYING -> LunoSwitchRow(
                title = stringResource(R.string.condition_music_playing),
                checked = item.musicPlaying == true,
                onCheckedChange = { onConditionChange(item.copy(musicPlaying = it)) },
            )
            ConditionType.VOLUME_CHANGED -> {
                Text(stringResource(R.string.volume_stream), style = MaterialTheme.typography.labelMedium)
                FilterChipRow(
                    options = AudioStream.entries.map { audioStreamLabel(context, it) },
                    selectedIndex = AudioStream.entries.indexOf(item.audioStream ?: AudioStream.MUSIC),
                    onIntChange = { i -> onConditionChange(item.copy(audioStream = AudioStream.entries.getOrNull(i))) },
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
                    },
                    onIntChange = { i ->
                        onConditionChange(item.copy(volumeDirection = when (i) {
                            0 -> null
                            1 -> VolumeDirection.UP
                            else -> VolumeDirection.DOWN
                        }))
                    },
                )
            }
            ConditionType.MUSIC_CHANGED -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.condition_unlimited),
                    stringResource(R.string.music_started),
                    stringResource(R.string.music_stopped),
                ),
                selectedIndex = when (item.playbackDirection) {
                    null -> 0
                    PlaybackDirection.STARTED -> 1
                    PlaybackDirection.STOPPED -> 2
                },
                onIntChange = { i ->
                    onConditionChange(item.copy(playbackDirection = when (i) {
                        1 -> PlaybackDirection.STARTED
                        2 -> PlaybackDirection.STOPPED
                        else -> null
                    }))
                },
            )
            ConditionType.CHARGING -> LunoSwitchRow(
                title = stringResource(R.string.condition_charging),
                checked = item.charging == true,
                onCheckedChange = { onConditionChange(item.copy(charging = it)) },
            )
            ConditionType.CHARGING_CHANGED -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.charging_started),
                    stringResource(R.string.charging_stopped),
                ),
                selectedIndex = if (item.charging == true) 0 else 1,
                onIntChange = { i -> onConditionChange(item.copy(charging = i == 1)) },
            )
            ConditionType.RINGER_SILENT -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.condition_silent),
                    stringResource(R.string.condition_ringer_vibrate),
                    stringResource(R.string.condition_ringing),
                ),
                selectedIndex = when (item.ringerMode ?: RingerMode.NORMAL) {
                    RingerMode.SILENT -> 0
                    RingerMode.VIBRATE -> 1
                    RingerMode.NORMAL -> 2
                },
                onIntChange = { i ->
                    onConditionChange(item.copy(
                        ringerMode = when (i) {
                            0 -> RingerMode.SILENT
                            1 -> RingerMode.VIBRATE
                            else -> RingerMode.NORMAL
                        },
                    ))
                },
            )
            ConditionType.NETWORK_TYPE -> TriStateLabelRow(
                labels = NetworkType.entries.map { networkTypeLabel(context, it) },
                selectedIndex = NetworkType.entries.indexOf(item.networkType ?: NetworkType.WIFI),
                onIntChange = { i -> onConditionChange(item.copy(networkType = NetworkType.entries.getOrNull(i))) },
            )
            ConditionType.WIFI_SSID -> {
                var locationGranted by remember {
                    mutableStateOf(
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED,
                    )
                }
                val locationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    locationGranted = granted
                    // 授权发生在主进程，:service 的条件源不会自己重读；不刷新会一直停在 null。
                    if (granted) DispatchClient.refreshConditionSources()
                }
                OutlinedTextField(
                    value = item.wifiSsid,
                    onValueChange = { onConditionChange(item.copy(wifiSsid = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.condition_wifi_ssid_value)) },
                    singleLine = true,
                    shape = hunoia.luno.ui.theme.LargeShape,
                )
                Text(
                    stringResource(R.string.condition_wifi_ssid_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!locationGranted) {
                    OutlinedButton(
                        onClick = {
                            locationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                    ) {
                        Text(stringResource(R.string.condition_wifi_permission))
                    }
                }
            }
            ConditionType.HEADPHONES -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.headphones_plugged),
                    stringResource(R.string.headphones_unplugged),
                ),
                selectedIndex = if (item.plugged == true) 0 else 1,
                onIntChange = { i -> onConditionChange(item.copy(plugged = i == 0)) },
            )
            ConditionType.BLUETOOTH -> {
                var bluetoothGranted by remember {
                    mutableStateOf(
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                        ) == PackageManager.PERMISSION_GRANTED,
                    )
                }
                val bluetoothLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    bluetoothGranted = granted
                    if (granted) DispatchClient.refreshConditionSources()
                }
                if (!bluetoothGranted) {
                    OutlinedButton(
                        onClick = {
                            bluetoothLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                        },
                    ) {
                        Text(stringResource(R.string.condition_bluetooth_permission))
                    }
                }
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
                    onIntChange = { i ->
                        onConditionChange(item.copy(expectedBluetoothAdapterOn = when (i) { 0 -> null; 1 -> true; else -> false }))
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
                    onIntChange = { i ->
                        onConditionChange(item.copy(expectedBluetoothAudioConnected = when (i) { 0 -> null; 1 -> true; else -> false }))
                    },
                )
                OutlinedTextField(
                    value = item.bluetoothDeviceName,
                    onValueChange = { onConditionChange(item.copy(bluetoothDeviceName = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.condition_bluetooth_device_name)) },
                    singleLine = true,
                    shape = hunoia.luno.ui.theme.LargeShape,
                )
                OutlinedTextField(
                    value = item.bluetoothDeviceAddress,
                    onValueChange = { onConditionChange(item.copy(bluetoothDeviceAddress = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.condition_bluetooth_device_address)) },
                    singleLine = true,
                    shape = hunoia.luno.ui.theme.LargeShape,
                )
                Text(
                    stringResource(R.string.condition_bluetooth_device_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConditionType.AIRPLANE_MODE -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.state_on),
                    stringResource(R.string.state_off),
                ),
                selectedIndex = if (item.airplaneMode == true) 0 else 1,
                onIntChange = { i -> onConditionChange(item.copy(airplaneMode = i == 0)) },
            )
            ConditionType.TORCH_ON -> LunoSwitchRow(
                title = stringResource(R.string.condition_torch_on),
                checked = item.torchOn == true,
                onCheckedChange = { onConditionChange(item.copy(torchOn = it)) },
            )
            ConditionType.DND_ON -> LunoSwitchRow(
                title = stringResource(R.string.condition_dnd_on),
                checked = item.dndOn == true,
                onCheckedChange = { onConditionChange(item.copy(dndOn = it)) },
            )
            ConditionType.TIME_RANGE -> TimeRangeField(
                startMinute = item.startMinute ?: 8 * 60,
                endMinute = item.endMinute ?: 18 * 60,
                weekdays = item.timeWeekdays,
                onRangeChange = { lo, hi -> onConditionChange(item.copy(startMinute = lo, endMinute = hi)) },
                onWeekdaysChange = { w -> onConditionChange(item.copy(timeWeekdays = w)) },
            )
            ConditionType.NOTIFICATION_POSTED,
            ConditionType.NOTIFICATION_REMOVED,
            ConditionType.NOTIFICATION_EXISTS -> NotificationConditionFields(item, onConditionChange)
            ConditionType.APP_PACKAGE_CHANGED -> {
                Text(
                    stringResource(R.string.condition_package_action),
                    style = MaterialTheme.typography.labelMedium,
                )
                FilterChipRow(
                    options = listOf(stringResource(R.string.condition_package_action_any)) +
                        PackageChangeAction.all.map { packageActionLabel(context, it) },
                    selectedIndex = item.packageChangeAction?.let {
                        PackageChangeAction.all.indexOf(it) + 1
                    } ?: 0,
                    onIntChange = { index ->
                        onConditionChange(
                            item.copy(
                                packageChangeAction = PackageChangeAction.all.getOrNull(index - 1),
                            ),
                        )
                    },
                )
                AppConditionField(
                    packageNames = item.packageNames,
                    onPackagesChange = { onConditionChange(item.copy(packageNames = it)) },
                    emptyHint = stringResource(R.string.condition_package_apps_hint),
                )
            }
            ConditionType.INTERVAL -> {
                OutlinedTextField(
                    value = item.intervalMinutes?.toString().orEmpty(),
                    onValueChange = { value ->
                        val minutes = value.filter { it.isDigit() }.take(4).toIntOrNull()
                        onConditionChange(item.copy(intervalMinutes = minutes))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.condition_interval_minutes)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    shape = hunoia.luno.ui.theme.LargeShape,
                )
                Text(
                    stringResource(R.string.condition_interval_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConditionType.SHARE_RECEIVED -> Text(
                stringResource(R.string.condition_share_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConditionType.CLIPBOARD_CHANGED -> {
                OutlinedTextField(
                    value = item.clipboardKeyword,
                    onValueChange = { onConditionChange(item.copy(clipboardKeyword = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.condition_clipboard_keyword)) },
                    singleLine = true,
                    shape = hunoia.luno.ui.theme.LargeShape,
                )
                Text(
                    stringResource(R.string.condition_clipboard_privacy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConditionType.GESTURE_PERFORMED -> Text(
                stringResource(R.string.condition_gesture_any),
                style = MaterialTheme.typography.bodyMedium,
            )
            ConditionType.BLUETOOTH_CHANGED,
            ConditionType.HEADPHONES_CHANGED -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.condition_unlimited),
                    stringResource(R.string.condition_connected),
                    stringResource(R.string.condition_disconnected),
                ),
                selectedIndex = when (item.connectionDirection) {
                    null -> 0
                    ConnectionDirection.CONNECTED -> 1
                    ConnectionDirection.DISCONNECTED -> 2
                },
                onIntChange = { i ->
                    onConditionChange(item.copy(connectionDirection = when (i) {
                        0 -> null
                        1 -> ConnectionDirection.CONNECTED
                        else -> ConnectionDirection.DISCONNECTED
                    }))
                },
            )
            ConditionType.TORCH_CHANGED -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.condition_unlimited),
                    stringResource(R.string.state_on),
                    stringResource(R.string.state_off),
                ),
                selectedIndex = when (item.torchChangedTo) {
                    null -> 0
                    true -> 1
                    false -> 2
                },
                onIntChange = { i ->
                    onConditionChange(item.copy(torchChangedTo = when (i) {
                        1 -> true
                        2 -> false
                        else -> null
                    }))
                },
            )
            ConditionType.DND_CHANGED -> TriStateLabelRow(
                labels = listOf(
                    stringResource(R.string.condition_unlimited),
                    stringResource(R.string.state_on),
                    stringResource(R.string.state_off),
                ),
                selectedIndex = when (item.dndChangedTo) {
                    null -> 0
                    true -> 1
                    false -> 2
                },
                onIntChange = { i ->
                    onConditionChange(item.copy(dndChangedTo = when (i) {
                        1 -> true
                        2 -> false
                        else -> null
                    }))
                },
            )
            ConditionType.SCHEDULED_TIME -> ScheduledTimeField(
                minutes = item.scheduledMinutes,
                weekdays = item.scheduledWeekdays,
                onScheduleChange = { m, w -> onConditionChange(item.copy(scheduledMinutes = m, scheduledWeekdays = w)) },
            )
            ConditionType.VAR_COMPARE -> {
                OutlinedTextField(
                    value = item.compareVarName,
                    onValueChange = {
                        onConditionChange(item.copy(compareVarName = it.trimStart()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.condition_var_name)) },
                    singleLine = true,
                    isError = item.compareVarName.isNotBlank() &&
                        !hunoia.luno.config.model.VariableNames.isValid(item.compareVarName),
                    shape = hunoia.luno.ui.theme.LargeShape,
                )
                Text(
                    text = stringResource(R.string.condition_var_op),
                    style = MaterialTheme.typography.labelMedium,
                )
                FilterChipRow(
                    options = hunoia.luno.config.model.VariableCompareOp.all.map {
                        variableCompareOpLabel(context, it)
                    },
                    selectedIndex = hunoia.luno.config.model.VariableCompareOp.all.indexOf(item.compareOp)
                        .coerceAtLeast(0),
                    onIntChange = { index ->
                        onConditionChange(item.copy(
                            compareOp = hunoia.luno.config.model.VariableCompareOp.all[index],
                        ))
                    },
                )
                val noRightOperand = item.compareOp == hunoia.luno.config.model.VariableCompareOp.IS_EMPTY ||
                    item.compareOp == hunoia.luno.config.model.VariableCompareOp.IS_NOT_EMPTY
                if (!noRightOperand) {
                    OutlinedTextField(
                        value = item.compareValue,
                        onValueChange = { onConditionChange(item.copy(compareValue = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.condition_var_value)) },
                        singleLine = true,
                        shape = hunoia.luno.ui.theme.LargeShape,
                    )
                }
                Text(
                    text = stringResource(R.string.condition_var_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConditionType.MANUAL -> Text(
                stringResource(R.string.condition_manual_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationConditionFields(
    item: ConditionItem,
    onConditionChange: (ConditionItem) -> Unit,
) {
    val context = LocalContext.current
    if (item.type == ConditionType.NOTIFICATION_EXISTS) {
        Text(
            stringResource(R.string.condition_notify_exists),
            style = MaterialTheme.typography.labelMedium,
        )
        TriStateLabelRow(
            labels = listOf(
                stringResource(R.string.condition_notify_exists_yes),
                stringResource(R.string.condition_notify_exists_no),
            ),
            selectedIndex = if (item.notifyExists == false) 1 else 0,
            onIntChange = { index ->
                onConditionChange(item.copy(notifyExists = index == 0))
            },
        )
    }
    Text(
        stringResource(R.string.condition_notify_match_mode),
        style = MaterialTheme.typography.labelMedium,
    )
    FilterChipRow(
        options = NotificationMatchMode.all.map {
            stringResource(
                when (it) {
                    NotificationMatchMode.GLOB -> R.string.condition_notify_match_glob
                    NotificationMatchMode.REGEX -> R.string.condition_notify_match_regex
                    else -> R.string.condition_notify_match_contains
                },
            )
        },
        selectedIndex = NotificationMatchMode.all.indexOf(item.notifyMatchMode).coerceAtLeast(0),
        onIntChange = { index ->
            onConditionChange(item.copy(notifyMatchMode = NotificationMatchMode.all[index]))
        },
    )
    if (item.notifyKeyword.isNotBlank()) {
        OutlinedTextField(
            value = item.notifyKeyword,
            onValueChange = { onConditionChange(item.copy(notifyKeyword = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.condition_notify_keyword)) },
            singleLine = true,
            shape = hunoia.luno.ui.theme.LargeShape,
        )
    }
    OutlinedTextField(
        value = item.notifyTitleKeyword,
        onValueChange = { onConditionChange(item.copy(notifyTitleKeyword = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.condition_notify_title_keyword)) },
        singleLine = true,
        shape = hunoia.luno.ui.theme.LargeShape,
    )
    OutlinedTextField(
        value = item.notifyTextKeyword,
        onValueChange = { onConditionChange(item.copy(notifyTextKeyword = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.condition_notify_text_keyword)) },
        singleLine = true,
        shape = hunoia.luno.ui.theme.LargeShape,
    )
    OutlinedTextField(
        value = item.notifyExcludeKeyword,
        onValueChange = { onConditionChange(item.copy(notifyExcludeKeyword = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.condition_notify_exclude_keyword)) },
        singleLine = true,
        shape = hunoia.luno.ui.theme.LargeShape,
    )
    if (item.notifyMatchMode == NotificationMatchMode.REGEX) {
        Text(
            stringResource(R.string.condition_notify_regex_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AppConditionField(
        packageNames = item.notifyPackages,
        onPackagesChange = { onConditionChange(item.copy(notifyPackages = it)) },
        emptyHint = stringResource(R.string.condition_notify_packages_hint),
    )
    Text(
        stringResource(R.string.condition_notify_exclude_packages),
        style = MaterialTheme.typography.labelMedium,
    )
    AppConditionField(
        packageNames = item.notifyExcludePackages,
        onPackagesChange = { onConditionChange(item.copy(notifyExcludePackages = it)) },
    )
    Text(
        stringResource(R.string.condition_notify_ongoing),
        style = MaterialTheme.typography.labelMedium,
    )
    TriStateLabelRow(
        labels = listOf(
            stringResource(R.string.condition_unlimited),
            stringResource(R.string.state_on),
            stringResource(R.string.state_off),
        ),
        selectedIndex = when (item.notifyOngoing) {
            null -> 0
            true -> 1
            false -> 2
        },
        onIntChange = { index ->
            onConditionChange(
                item.copy(notifyOngoing = when (index) { 1 -> true; 2 -> false; else -> null }),
            )
        },
    )
    Text(
        stringResource(R.string.condition_notify_clearable),
        style = MaterialTheme.typography.labelMedium,
    )
    TriStateLabelRow(
        labels = listOf(
            stringResource(R.string.condition_unlimited),
            stringResource(R.string.state_on),
            stringResource(R.string.state_off),
        ),
        selectedIndex = when (item.notifyClearable) {
            null -> 0
            true -> 1
            false -> 2
        },
        onIntChange = { index ->
            onConditionChange(
                item.copy(notifyClearable = when (index) { 1 -> true; 2 -> false; else -> null }),
            )
        },
    )
    Text(
        stringResource(R.string.condition_notify_importance),
        style = MaterialTheme.typography.labelMedium,
    )
    val importanceOptions = listOf(
        null to stringResource(R.string.condition_notify_importance_any),
        NotificationManager.IMPORTANCE_LOW to stringResource(R.string.condition_notify_importance_low),
        NotificationManager.IMPORTANCE_DEFAULT to stringResource(R.string.condition_notify_importance_default),
        NotificationManager.IMPORTANCE_HIGH to stringResource(R.string.condition_notify_importance_high),
    )
    FilterChipRow(
        options = importanceOptions.map { it.second },
        selectedIndex = importanceOptions.indexOfFirst { it.first == item.notifyImportanceAtLeast }
            .coerceAtLeast(0),
        onIntChange = { index ->
            onConditionChange(item.copy(notifyImportanceAtLeast = importanceOptions[index].first))
        },
    )
    Text(
        stringResource(R.string.condition_notify_privacy_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun packageActionLabel(context: Context, action: String): String = context.getString(
    when (action) {
        PackageChangeAction.ADDED -> R.string.condition_package_action_added
        PackageChangeAction.REMOVED -> R.string.condition_package_action_removed
        PackageChangeAction.REPLACED -> R.string.condition_package_action_replaced
        else -> R.string.condition_package_action_changed
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(
    options: List<String>,
    selectedIndex: Int,
    onIntChange: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PageGutter)) {
        options.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onIntChange(index) },
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
    onIntChange: (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onIntChange(index) },
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
    onRangeChange: (Int, Int) -> Unit,
) {
    var sliderValue by remember(min, max) {
        mutableStateOf((min ?: 0).toFloat()..(max ?: 100).toFloat())
    }
    RangeSlider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = {
            onRangeChange(
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
    onPackagesChange: (List<String>) -> Unit,
    emptyHint: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    Text(
        text = if (packageNames.isEmpty()) {
            emptyHint ?: stringResource(R.string.condition_apps_empty)
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
            onConfirm = { onPackagesChange(it) },
        )
    }
}
@Composable
private fun TimeRangeField(
    startMinute: Int,
    endMinute: Int,
    weekdays: Set<Int>,
    onRangeChange: (Int, Int) -> Unit,
    onWeekdaysChange: (Set<Int>) -> Unit,
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
    Text(
        stringResource(R.string.condition_weekdays),
        style = MaterialTheme.typography.labelMedium,
    )
    WeekdayChipRow(weekdays = weekdays, onWeekdaysChange = onWeekdaysChange)
    when (picking) {
        0 -> TimePickerDialog(
            title = stringResource(R.string.condition_time_start),
            initialMinute = startMinute,
            onDismissRequest = { picking = null },
            onConfirm = { onRangeChange(it, endMinute) },
        )
        1 -> TimePickerDialog(
            title = stringResource(R.string.condition_time_end),
            initialMinute = endMinute,
            onDismissRequest = { picking = null },
            onConfirm = { onRangeChange(startMinute, it) },
        )
        else -> Unit
    }
}
/** 周一起，映射 Calendar 常量（MONDAY=2..SUNDAY=1）。时间范围与定时时刻共用。 */
internal val WEEKDAY_ORDER = listOf(
    java.util.Calendar.MONDAY to R.string.weekday_mon,
    java.util.Calendar.TUESDAY to R.string.weekday_tue,
    java.util.Calendar.WEDNESDAY to R.string.weekday_wed,
    java.util.Calendar.THURSDAY to R.string.weekday_thu,
    java.util.Calendar.FRIDAY to R.string.weekday_fri,
    java.util.Calendar.SATURDAY to R.string.weekday_sat,
    java.util.Calendar.SUNDAY to R.string.weekday_sun,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChipRow(
    weekdays: Set<Int>,
    onWeekdaysChange: (Set<Int>) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PageGutter)) {
        WEEKDAY_ORDER.forEach { (day, labelRes) ->
            FilterChip(
                selected = day in weekdays,
                onClick = {
                    onWeekdaysChange(if (day in weekdays) weekdays - day else weekdays + day)
                },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduledTimeField(
    minutes: List<Int>,
    weekdays: Set<Int>,
    onScheduleChange: (List<Int>, Set<Int>) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.condition_scheduled_times),
        style = MaterialTheme.typography.labelMedium,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PageGutter)) {
        minutes.sorted().forEach { minute ->
            FilterChip(
                selected = true,
                onClick = { onScheduleChange(minutes.filterNot { it == minute }, weekdays) },
                label = { Text(formatTimeOfDay(minute)) },
            )
        }
        OutlinedButton(onClick = { picking = true }) {
            Text(stringResource(R.string.condition_scheduled_add))
        }
    }
    Text(
        stringResource(R.string.condition_scheduled_weekdays),
        style = MaterialTheme.typography.labelMedium,
    )
    WeekdayChipRow(weekdays = weekdays) { updated ->
        onScheduleChange(minutes, updated)
    }
    if (picking) {
        TimePickerDialog(
            title = stringResource(R.string.condition_scheduled_add),
            initialMinute = 8 * 60,
            onDismissRequest = { picking = false },
            onConfirm = {
                if (it !in minutes) onScheduleChange(minutes + it, weekdays)
            },
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    CustomDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(timeState.hour * 60 + timeState.minute) },
    ) {
        TimePicker(state = timeState)
    }
}