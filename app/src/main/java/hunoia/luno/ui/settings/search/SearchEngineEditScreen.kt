package hunoia.luno.ui.settings.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
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
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.allEngines
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.quicklaunch.query.SearchSourceRegistry
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.AppPickerSheet
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/** 进页一次性快照：目标引擎 + 标识判重输入；之后配置流变化不重建草稿。 */
private data class EngineEditSnapshot(
    val engine: SearchEngine?,
    val existingTriggers: List<String>,
)

/**
 * 引擎编辑页（引擎设置页的下级页）：字段与校验复用 [EngineEditFields]。
 * 右下角 FAB 保存（无效草稿 toast），有未保存改动时返回弹确认；
 * 保存以最新配置为基础只覆盖本页字段。engineId 空 = 新增。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineEditScreen(
    engineId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val addMode = engineId.isBlank()
    var snapshot by remember(engineId) { mutableStateOf<EngineEditSnapshot?>(null) }
    LaunchedEffect(engineId) {
        SearchCenter.search.collect { settings ->
            if (snapshot == null) {
                snapshot = EngineEditSnapshot(
                    engine = settings.allEngines().firstOrNull { it.id == engineId },
                    existingTriggers = settings.allEngines()
                        .filter { it.id != engineId }
                        .map { it.trigger } +
                        SearchSourceRegistry.all.map { settings.effectiveTrigger(it.key) },
                )
            }
        }
    }
    val snap = snapshot
    if (snap == null) return
    if (!addMode && snap.engine == null) {
        // 编辑期间引擎在别处被删：给明确落点，不静默弹回。
        LunoScreen(title = stringResource(R.string.search_engine_edit), onBack = onBack) {
            EmptyState(message = stringResource(R.string.search_engine_missing))
        }
        return
    }
    val editState = rememberEngineEditState(
        initial = snap.engine,
        existingTriggers = snap.existingTriggers,
    )
    var targetPackage by remember(engineId) {
        mutableStateOf(
            (snap.engine?.effectiveLaunch() as? SearchLaunch.Url)?.packageName.orEmpty(),
        )
    }
    val initialPackage = remember(engineId) {
        (snap.engine?.effectiveLaunch() as? SearchLaunch.Url)?.packageName.orEmpty()
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val valid = editState.valid(targetPackage)
    val dirty = editState.isDirty() || targetPackage != initialPackage
    val guardedBack = { if (dirty) showDiscardConfirm = true else onBack() }
    fun save() {
        val result = editState.result(targetPackage)
        scope.launch {
            SearchCenter.updateSearch { current ->
                if (addMode) {
                    current.copy(
                        customEngines = current.customEngines +
                            SearchEngine(
                                name = result.name,
                                urlTemplate = result.template,
                                trigger = result.trigger,
                                launch = result.launch,
                                iconText = result.iconText,
                            ),
                    )
                } else {
                    current.copy(
                        customEngines = current.customEngines.map {
                            if (it.id == engineId) {
                                it.copy(
                                    name = result.name,
                                    urlTemplate = result.template,
                                    trigger = result.trigger,
                                    launch = result.launch,
                                    iconText = result.iconText,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
            }
            onBack()
        }
    }
    LunoScreen(
        title = stringResource(
            if (addMode) R.string.search_engine_add else R.string.search_engine_edit,
        ),
        onBack = guardedBack,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            // 点击直接保存（无效草稿 toast 提示）；无效态换灰底提示不可保存。
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    FloatingActionButton(
                        onClick = {
                            if (valid) save() else showToast(R.string.search_engine_invalid_hint)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .size(64.dp),
                        shape = FloatingActionButtonDefaults.shape,
                        containerColor = if (valid) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save),
                        )
                    }
                }
            }
        },
        dialogs = {
            if (showDiscardConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDiscardConfirm = false },
                    title = stringResource(R.string.action_library_discard_title),
                    text = stringResource(R.string.action_library_discard_desc),
                    confirmLabel = stringResource(R.string.automation_discard),
                    onConfirm = {
                        showDiscardConfirm = false
                        onBack()
                    },
                )
            }
        },
        sheets = {
            if (showAppPicker) {
                AppPickerSheet(
                    onDismissRequest = { showAppPicker = false },
                    selectedPackageNames = listOf(targetPackage),
                    confirmOnSelect = true,
                    onConfirm = { packages ->
                        targetPackage = packages.firstOrNull().orEmpty()
                        showAppPicker = false
                    },
                )
            }
        },
    ) {
        EngineEditFields(
            state = editState,
            targetPackage = targetPackage,
            onPickApp = { showAppPicker = true },
        )
    }
}
