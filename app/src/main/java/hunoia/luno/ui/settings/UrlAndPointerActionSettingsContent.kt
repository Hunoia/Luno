package hunoia.luno.ui.settings

import hunoia.luno.ui.theme.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.core.JsonSerializer

@Composable
fun UrlSettingsContent(
    action: Action,
    onConfirm: (String) -> Unit,
    showConfirmButton: Boolean = true,
    onDataChange: ((String) -> Unit)? = null,
) {
    val existingData = remember(action.data) {
        runCatching { JsonSerializer.decodeFromString<OpenAppOrUrlData>(action.data) }.getOrNull()
    }
    var urlInput by remember(action.data) { mutableStateOf(existingData?.url ?: "") }
    var miniWindow by remember(action.data) { mutableStateOf(existingData?.miniWindow ?: false) }
    var queryParameters by remember(action.data) { mutableStateOf(existingData?.queryParameters ?: emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItemPadding),
        verticalArrangement = Arrangement.spacedBy(ItemPadding)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = urlInput,
            onValueChange = {
                urlInput = it
                onDataChange?.invoke(encodeOpenUrlData(it, miniWindow, queryParameters))
            },
            label = { Text(stringResource(R.string.url_link)) },
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.open_url_mini_window))
            Switch(
                checked = miniWindow,
                onCheckedChange = {
                    miniWindow = it
                    onDataChange?.invoke(encodeOpenUrlData(urlInput, it, queryParameters))
                }
            )
        }
        Text(text = stringResource(R.string.open_url_custom_parameters))
        queryParameters.forEachIndexed { index, parameter ->
            OpenUrlQueryParameterRow(
                parameter = parameter,
                onChange = { next ->
                    queryParameters = queryParameters.toMutableList().also { it[index] = next }
                    onDataChange?.invoke(encodeOpenUrlData(urlInput, miniWindow, queryParameters))
                },
                onDelete = {
                    queryParameters = queryParameters.toMutableList().also { it.removeAt(index) }
                    onDataChange?.invoke(encodeOpenUrlData(urlInput, miniWindow, queryParameters))
                }
            )
        }
        TextButton(
            modifier = Modifier.align(Alignment.Start),
            onClick = {
                queryParameters = queryParameters + OpenUrlQueryParameter()
                onDataChange?.invoke(encodeOpenUrlData(urlInput, miniWindow, queryParameters))
            }
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Text(text = stringResource(R.string.open_url_add_parameter))
        }
        if (showConfirmButton) {
            TextButton(
                modifier = Modifier.align(Alignment.End),
                enabled = urlInput.isNotBlank(),
                onClick = { onConfirm(encodeOpenUrlData(urlInput, miniWindow, queryParameters)) }
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Text(text = stringResource(id = R.string.confirm))
            }
        }
    }
}

private fun encodeOpenUrlData(
    url: String,
    miniWindow: Boolean,
    queryParameters: List<OpenUrlQueryParameter>,
): String = JsonSerializer.encodeToString(
    OpenAppOrUrlData(
        type = OpenAppOrUrlData.TYPE_URL,
        url = url.trim(),
        miniWindow = miniWindow,
        queryParameters = queryParameters,
    )
)

@Composable
private fun OpenUrlQueryParameterRow(
    parameter: OpenUrlQueryParameter,
    onChange: (OpenUrlQueryParameter) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ItemPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = parameter.enabled,
            onCheckedChange = { onChange(parameter.copy(enabled = it)) }
        )
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = parameter.name,
            onValueChange = { onChange(parameter.copy(name = it)) },
            label = { Text(stringResource(R.string.open_url_parameter_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = parameter.value,
            onValueChange = { onChange(parameter.copy(value = it)) },
            label = { Text(stringResource(R.string.open_url_parameter_value)) },
            singleLine = true,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.open_url_delete_parameter),
            )
        }
    }
}
