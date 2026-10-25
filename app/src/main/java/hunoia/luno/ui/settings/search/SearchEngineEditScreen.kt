package hunoia.luno.ui.settings.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.allEngines
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.quicklaunch.launchSearchAction
import hunoia.luno.quicklaunch.query.SearchSourceRegistry
import hunoia.luno.quicklaunch.query.toSearchAction
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.AppPickerSheet
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/** 测试按钮用固定词：能直观看出 %s / %q 是否被正确替换。 */
private const val EDIT_TEST_QUERY = "Luno"

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
    val context = LocalContext.current
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
    // 目标应用显示名：包名解析失败（已卸载等）回退显示包名本身。
    val appLabel by produceState<String?>(null, targetPackage) {
        value = targetPackage.takeIf { it.isNotBlank() }?.let { pkg ->
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0),
                ).toString()
            }.getOrNull()
        }
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
        actions = {
            // 测试放顶栏（与动作库编辑页一致）：模板/目标应用就绪即可试，名称不拦。
            LunoIconButton(
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.search_engine_test),
                enabled = editState.testable(targetPackage),
                onClick = {
                    val action = editState.buildLaunch(targetPackage).toSearchAction(EDIT_TEST_QUERY)
                    if (!launchSearchAction(context, action)) showToast(R.string.launch_no_handler)
                },
            )
        },
        floatingContent = { _ ->
            // 点击直接保存（无效草稿 toast 提示）；无效态换灰底提示不可保存。
            LunoFab(
                onClick = {
                    if (valid) save() else showToast(R.string.search_engine_invalid_hint)
                },
                icon = Icons.Default.Save,
                contentDescription = stringResource(R.string.save),
                containerColor = if (valid) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        dialogs = {
            if (showDiscardConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDiscardConfirm = false },
                    title = stringResource(R.string.action_library_discard_title),
                    text = stringResource(R.string.action_library_discard_desc),
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
            appLabel = appLabel,
            onPickApp = { showAppPicker = true },
        )
    }
}
