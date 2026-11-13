package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.config.model.TrendGoalDirection
import hunoia.luno.config.model.TrendMaxDecimals
import hunoia.luno.config.model.TrendMetric
import hunoia.luno.domain.gridpanel.TrendCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.PressFilterChip
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.input.WheelColumn
import hunoia.luno.ui.component.input.wheelNumber
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.AnchoredDialog
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.toColor
import kotlinx.coroutines.launch

/** 默认自定义色：与 [TrendMetric] 默认一致，避免新建时颜色为空。 */
private const val DefaultMetricColor = 0xFF4CAF50.toInt()

/**
 * 新建 / 编辑趋势指标。
 *
 * 与记录不同，指标定义可以随时改：名称、单位、小数位、颜色、目标、提醒。
 * 单位只改显示标签，历史数值不换算（页面里明确提示一次）。
 */
@Composable
fun TrendEditScreen(
    metricId: String?,
    onBack: () -> Unit,
) {
    val configOrNull by TrendCenter.trendConfig.collectAsStateWithLifecycle(initialValue = null)
    val config = configOrNull
    if (config == null) {
        // 首帧还没读到 DataStore：不渲染表单，避免「先空后填」闪一下。
        LunoScreen(title = stringResource(R.string.trend_edit_title), onBack = onBack) {}
        return
    }
    val metric = metricId?.takeIf { it.isNotBlank() }?.let { config.metricById(it) }
    if (!metricId.isNullOrBlank() && metric == null) {
        LunoScreen(title = stringResource(R.string.trend_edit_title), onBack = onBack) {
            EmptyState(message = stringResource(R.string.trend_metric_missing))
        }
        return
    }
    TrendEditForm(metric = metric, onBack = onBack)
}

@Composable
private fun TrendEditForm(
    metric: TrendMetric?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var name by remember(metric?.id) { mutableStateOf(metric?.name.orEmpty()) }
    var unit by remember(metric?.id) { mutableStateOf(metric?.unit.orEmpty()) }
    var decimals by remember(metric?.id) { mutableIntStateOf(metric?.safeDecimals ?: 1) }
    var colorSpec by remember(metric?.id) {
        mutableStateOf(
            metric?.colorSpec ?: ColorSpec(
                source = ColorSource.Custom,
                themeKey = ThemeColorKey.Primary,
                customColor = DefaultMetricColor,
            ),
        )
    }
    var targetText by remember(metric?.id) {
        mutableStateOf(metric?.target?.let { formatTrendValue(it, metric.safeDecimals) }.orEmpty())
    }
    var direction by remember(metric?.id) {
        mutableStateOf(metric?.goalDirection ?: TrendGoalDirection.NEUTRAL)
    }
    var reminderEnabled by remember(metric?.id) {
        mutableStateOf(metric?.reminderEnabled ?: false)
    }
    var reminderHour by remember(metric?.id) { mutableIntStateOf(metric?.reminderHour ?: 8) }
    var reminderMinute by remember(metric?.id) { mutableIntStateOf(metric?.reminderMinute ?: 0) }
    var createdAt by remember(metric?.id) { mutableStateOf(metric?.createdAt ?: System.currentTimeMillis()) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun save() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            showToast(R.string.trend_edit_name_required)
            return
        }
        val target = targetText.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (targetText.isNotBlank() && (target == null || !target.isFinite())) {
            showToast(R.string.trend_edit_target_invalid)
            return
        }
        val base = metric ?: TrendMetric(createdAt = createdAt)
        val updated = base.copy(
            name = trimmedName,
            unit = unit.trim(),
            decimals = decimals.coerceIn(0, TrendMaxDecimals),
            colorSource = colorSpec.source,
            colorThemeKey = colorSpec.themeKey,
            color = colorSpec.customColor,
            target = target,
            goalDirection = direction,
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute,
        )
        scope.launch {
            if (metric == null) TrendCenter.addMetric(updated) else TrendCenter.updateMetric(updated)
            onBack()
        }
    }

    LunoScreen(
        title = stringResource(
            if (metric == null) R.string.trend_create_title else R.string.trend_edit_title,
        ),
        onBack = onBack,
        bottomContentPadding = ContentBottom,
        actions = {
            if (metric != null) {
                PressIconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.trend_delete),
                    )
                }
            }
            PressTextButton(onClick = ::save) {
                Text(stringResource(R.string.trend_save))
            }
        },
        dialogs = {
            if (showDeleteConfirm && metric != null) {
                ConfirmDialog(
                    title = stringResource(R.string.trend_delete_confirm_title),
                    text = stringResource(R.string.trend_delete_confirm_text, metric.name),
                    confirmLabel = stringResource(R.string.trend_delete),
                    destructive = true,
                    onDismissRequest = { showDeleteConfirm = false },
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            TrendCenter.deleteMetric(metric.id)
                            onBack()
                        }
                    },
                )
            }
            if (showTimePicker) {
                TrendTimePickerDialog(
                    initialHour = reminderHour,
                    initialMinute = reminderMinute,
                    onDismiss = { showTimePicker = false },
                    onConfirm = { hour, minute ->
                        reminderHour = hour
                        reminderMinute = minute
                        showTimePicker = false
                    },
                )
            }
        },
        sheets = {
            if (showColorPicker) {
                ColorPickerBottomSheet(
                    onDismissRequest = { showColorPicker = false },
                    initialSelection = colorSpec.toInitialSelection(scheme),
                    onColorSelected = { selection ->
                        colorSpec = selection.toColorSpec(scheme)
                        showColorPicker = false
                    },
                )
            }
        },
    ) {
        LunoGroup(title = stringResource(R.string.trend_edit_name)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.trend_edit_name_hint)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = 8.dp),
            )
        }
        LunoGroup(title = stringResource(R.string.trend_edit_unit)) {
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.trend_edit_unit_hint)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.trend_edit_unit_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PageGutter),
            )
        }
        LunoGroup(title = stringResource(R.string.trend_edit_decimals)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter),
                horizontalArrangement = Arrangement.spacedBy(ListSpacing),
            ) {
                (0..TrendMaxDecimals).forEach { digits ->
                    PressFilterChip(
                        selected = decimals == digits,
                        onClick = { decimals = digits },
                        label = { Text(stringResource(R.string.trend_edit_decimals_value, digits)) },
                    )
                }
            }
        }
        LunoGroup(title = stringResource(R.string.trend_edit_color)) {
            LunoSettingsRow(
                title = stringResource(R.string.trend_edit_color),
                leading = {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colorSpec.toColor()),
                    )
                },
                onClick = { showColorPicker = true },
            )
        }
        LunoGroup(title = stringResource(R.string.trend_edit_target)) {
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                singleLine = true,
                trailingIcon = { Text(unit) },
                placeholder = { Text(stringResource(R.string.trend_edit_target_hint)) },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter),
                horizontalArrangement = Arrangement.spacedBy(ListSpacing),
            ) {
                TrendGoalDirection.entries.forEach { item ->
                    PressFilterChip(
                        selected = direction == item,
                        onClick = { direction = item },
                        label = { Text(trendGoalLabel(item)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.trend_edit_target_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PageGutter),
            )
        }
        LunoGroup(title = stringResource(R.string.trend_edit_reminder)) {
            LunoSwitchRow(
                title = stringResource(R.string.trend_edit_reminder_enable),
                checked = reminderEnabled,
                onCheckedChange = { reminderEnabled = it },
            )
            if (reminderEnabled) {
                LunoSettingsRow(
                    title = stringResource(R.string.trend_edit_reminder_time),
                    subtitle = stringResource(
                        R.string.trend_edit_reminder_time_value,
                        reminderHour,
                        reminderMinute,
                    ),
                    onClick = { showTimePicker = true },
                )
                Text(
                    text = stringResource(R.string.trend_edit_reminder_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PageGutter),
                )
            }
        }
    }
}

@Composable
private fun trendGoalLabel(direction: TrendGoalDirection): String = stringResource(
    when (direction) {
        TrendGoalDirection.LOWER_IS_BETTER -> R.string.trend_goal_lower
        TrendGoalDirection.HIGHER_IS_BETTER -> R.string.trend_goal_higher
        TrendGoalDirection.NEUTRAL -> R.string.trend_goal_neutral
    },
)

/** 提醒时间滚轮：与习惯 / 待办共用 [WheelColumn]。 */
@Composable
private fun TrendTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val hours = remember { (0 until 24).toList() }
    val minutes = remember { (0 until 60).toList() }
    var hour by rememberSaveable { mutableIntStateOf(initialHour) }
    var minute by rememberSaveable { mutableIntStateOf(initialMinute) }
    AnchoredDialog(onDismiss = onDismiss) { dismiss ->
        Text(
            text = stringResource(R.string.trend_edit_reminder_time),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            WheelColumn(
                values = hours,
                initialIndex = initialHour,
                onSelected = { hour = it },
                label = ::wheelNumber,
                modifier = Modifier.weight(1f),
            )
            WheelColumn(
                values = minutes,
                initialIndex = initialMinute,
                onSelected = { minute = it },
                label = ::wheelNumber,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ListSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            PressTextButton(onClick = dismiss) {
                Text(stringResource(hunoia.luno.design.R.string.cancel))
            }
            PressTextButton(
                onClick = {
                    onConfirm(hour, minute)
                    dismiss()
                },
            ) {
                Text(stringResource(hunoia.luno.design.R.string.confirm))
            }
        }
    }
}
