package hunoia.luno.ui.settings.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.core.AppContext
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.screen.LunoScreenPlain
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.EmptyStatePaddingVertical
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 最近应用黑名单（全屏版应用多选）。
 *
 * 选择只保存在本地草稿；点右下角 FAB 才落盘。直接返回（含系统返回键）不写配置，即取消。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentAppsBlocklistScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    // 以当前配置为草稿初值；用户改动不写盘，确认时才提交。
    var selected by remember(actionSettings.recentAppsBlocklist) {
        mutableStateOf(actionSettings.recentAppsBlocklist.toSet())
    }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // 进入页面只重排一次：配置与列表都就绪后，把已选项排到前面。
    var initialOrderApplied by remember { mutableStateOf(false) }
    val permissionState = rememberGetInstalledAppsPermissionState { granted ->
        if (granted) loaded = false
    }

    suspend fun loadApps() {
        loading = true
        apps = withContext(Dispatchers.IO) {
            try {
                val context = AppContext.get()
                val appInfos = QuickLaunchFacade
                    .queryApps(context)
                    .filter { it.packageName != context.packageName }
                val disabledApps = DisabledAppQuery.queryDisabledApplicationsOnIo(context)
                val normalPackageNames = appInfos.map { it.packageName }.toSet()
                val filteredDisabledApps = disabledApps.filter {
                    it.packageName !in normalPackageNames && it.packageName != context.packageName
                }
                appInfos + filteredDisabledApps
            } catch (_: Exception) {
                emptyList()
            }
        }
        loading = false
    }

    // 配置可能是异步到达的：等 apps 与已选列表都就绪后再排一次，且只排一次。
    LaunchedEffect(apps, actionSettings.recentAppsBlocklist) {
        if (!initialOrderApplied && apps.isNotEmpty()) {
            val selectedPackages = actionSettings.recentAppsBlocklist.toSet()
            apps = apps.sortedByDescending { it.packageName in selectedPackages }
            initialOrderApplied = true
        }
    }

    LaunchedEffect(permissionState.isGranted, loaded) {
        if (!permissionState.isGranted) {
            permissionState.launchPermissionRequest()
        } else if (!loaded) {
            loadApps()
            loaded = true
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    fun confirm() {
        val ordered = actionSettings.recentAppsBlocklist.filter { it in selected } +
            selected.filter { it !in actionSettings.recentAppsBlocklist }
        scope.launch {
            TuneConfig.updateAction { it.copy(recentAppsBlocklist = ordered) }
            onBack()
        }
    }

    LunoScreenPlain(
        title = stringResource(R.string.recent_apps_blocklist),
        onBack = onBack,
        actions = {
            if (permissionState.isGranted) {
                LunoIconButton(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.reset),
                    onClick = { selected = emptySet() },
                )
            }
        },
        floatingContent = {
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    FloatingActionButton(
                        onClick = { confirm() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .size(64.dp),
                        shape = FloatingActionButtonDefaults.shape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.confirm),
                        )
                    }
                }
            }
        },
    ) { topPadding, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
        ) {
            if (!permissionState.isGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = EmptyStatePaddingVertical),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { permissionState.launchPermissionRequest() }) {
                        Text(stringResource(R.string.request_get_apps_permission))
                    }
                }
            } else {
                AppSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                    placeholder = stringResource(R.string.search_app_hint),
                )
                if (loading) {
                    LoadingState(message = stringResource(R.string.loading))
                } else {
                    val selectionFlags = filteredApps.map { it.packageName in selected }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = PageGutter,
                            end = PageGutter,
                            bottom = FloatingContentBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                    ) {
                        if (filteredApps.isEmpty()) {
                            item { EmptyState(message = stringResource(R.string.no_matching_results)) }
                        } else {
                            itemsIndexed(filteredApps, key = { _, item -> item.qualifiedName }) { index, item ->
                                LunoSelectableRow(
                                    title = item.label,
                                    subtitle = item.packageName,
                                    iconModel = item.icon,
                                    selected = item.packageName in selected,
                                    onSelectedChange = { isSelected ->
                                        selected = if (isSelected) {
                                            selected + item.packageName
                                        } else {
                                            selected - item.packageName
                                        }
                                    },
                                    shape = selectedSegmentedShape(index, filteredApps.size, selectionFlags),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
