package hunoia.luno.ui.settings

import hunoia.luno.ui.theme.*

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import hunoia.luno.R
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



@Composable
fun ActivitySettingsContent(
    action: hunoia.luno.config.model.Action,
    onConfirm: (String) -> Unit,
    onDataChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val launcherApps = remember(context) { QuickLaunchFacade.queryLauncherAppOptions(context) }
    val existingData = remember {
        runCatching { JsonSerializer.decodeFromString<OpenAppOrUrlData>(action.data) }.getOrNull()
    }

    var appQuery by remember { mutableStateOf("") }
    var selectedApp by remember {
        mutableStateOf(
            if (existingData?.type == OpenAppOrUrlData.TYPE_ACTIVITY && existingData.packageName.isNotBlank()) {
                launcherApps.firstOrNull { it.packageName == existingData.packageName }
            } else null
        )
    }
    var activityQuery by remember { mutableStateOf("") }

    val filteredApps = launcherApps.filter {
        appQuery.isBlank() ||
        it.label.contains(appQuery, ignoreCase = true) ||
        it.packageName.contains(appQuery, ignoreCase = true)
    }
    val activityOptions = remember(selectedApp) {
        val app = selectedApp
        if (app != null) QuickLaunchFacade.queryActivityOptions(
            context = context,
            packageName = app.packageName,
            selectedActivityClassName = "",
            launcherClassName = app.launcherClassName
        ) else emptyList()
    }
    val filteredActivities = activityOptions.filter {
        activityQuery.isBlank() ||
        QuickLaunchFacade.formatActivityOptionText(it, selectedApp?.packageName ?: "").contains(activityQuery, ignoreCase = true) ||
        it.className.contains(activityQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (selectedApp == null) {
            AppSearchBar(
                query = appQuery,
                onQueryChange = { appQuery = it },
                placeholder = stringResource(R.string.search_app_hint),
            )
            if (appQuery.isBlank()) {
                EmptyState(message = stringResource(R.string.no_matching_results))
            } else if (filteredApps.isEmpty()) {
                EmptyState(message = stringResource(R.string.no_matching_results))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredApps.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedApp = item
                                    appQuery = ""
                                    activityQuery = ""
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var icon by remember(item.packageName) { mutableStateOf<Drawable?>(null) }
                            LaunchedEffect(item.packageName) {
                                icon = withContext(Dispatchers.IO) {
                                    QuickLaunchFacade.loadIcon(context, item.packageName)
                                }
                            }
                            AsyncImage(
                                modifier = Modifier.size(SubMinInteractiveSize),
                                model = icon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = item.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val app = selectedApp ?: error("selectedApp is null in else branch")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${app.label} (${app.packageName})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { selectedApp = null }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
            AppSearchBar(
                query = activityQuery,
                onQueryChange = { activityQuery = it },
                placeholder = stringResource(R.string.search_activity_hint),
            )
            if (filteredActivities.isEmpty()) {
                EmptyState(message = stringResource(R.string.no_matching_results))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (if (activityQuery.isBlank()) activityOptions else filteredActivities).forEach { activity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val data = JsonSerializer.encodeToString(
                                        OpenAppOrUrlData(
                                            type = OpenAppOrUrlData.TYPE_ACTIVITY,
                                            packageName = app.packageName,
                                            activityClassName = activity.className
                                        )
                                    )
                                    if (onDataChange != null) onDataChange(data) else onConfirm(data)
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(SubMinInteractiveSize)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = QuickLaunchFacade.formatActivityOptionText(activity, app.packageName),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activity.className,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
