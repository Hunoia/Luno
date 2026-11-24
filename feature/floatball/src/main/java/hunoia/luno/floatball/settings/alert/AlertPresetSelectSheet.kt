package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AlertLayerCategory
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoSearchField
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.SegmentedGap

/**
 * 预设选择 sheet：搜索 + 单行可滚动类别筛选 + 单选（选中即落盘并关闭）。
 *
 * 到达提示消费页与动作动效页共用；[noneSubtitle] 用来解释「不用动效」在本页的含义。
 */
@Composable
internal fun AlertPresetSelectSheet(
    library: AlertLibrary,
    currentId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    noneSubtitle: String = stringResource(R.string.float_ball_alert_preset_none_subtitle),
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<AlertLayerCategory?>(null) }

    val all = library.pickerPresets()
    val nameOf: (AlertPreset) -> String = { it.displayNameString(context) }
    val filtered = all.filter { preset ->
        (category == null || category in preset.categories()) &&
            (query.isBlank() || nameOf(preset).contains(query.trim(), ignoreCase = true))
    }
    val mine = filtered.filter { !it.builtin }
    val builtin = filtered.filter { it.builtin }

    LunoSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.float_ball_alert_preset_picker),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = CapsuleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LunoSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.float_ball_alert_preset_search),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        AlertCategoryFilter(selected = category, onSelect = { category = it })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            val rows = segmentedRows {
                item { shape ->
                    PresetChoiceRow(
                        title = stringResource(R.string.float_ball_alert_preset_none),
                        subtitle = noneSubtitle,
                        selected = currentId == null,
                        shape = shape,
                        onClick = { onSelect(null) },
                    )
                }
                mine.forEach { preset ->
                    item { shape ->
                        PresetChoiceRow(
                            title = preset.displayName(),
                            subtitle = "${preset.layerSummary()}  ${preset.categorySummary()}",
                            selected = preset.id == currentId,
                            shape = shape,
                            onClick = { onSelect(preset.id) },
                        )
                    }
                }
                builtin.forEach { preset ->
                    item { shape ->
                        PresetChoiceRow(
                            title = preset.displayName(),
                            subtitle = "${preset.layerSummary()}  ${preset.categorySummary()}",
                            selected = preset.id == currentId,
                            shape = shape,
                            onClick = { onSelect(preset.id) },
                        )
                    }
                }
            }
            SegmentedRows(rows)
        }
    }
}

@Composable
private fun PresetChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    LunoSettingsRow(
        title = title,
        subtitle = subtitle,
        shape = shape,
        trailing = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
    )
}