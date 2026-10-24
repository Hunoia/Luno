package hunoia.luno.ui.actionselect

import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.ShortcutIconFiles
import hunoia.luno.action.model.hasPointParams
import hunoia.luno.action.model.launchAppRef
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.action.model.withLongPressVariant
import hunoia.luno.domain.action.ActionCatalog
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.derivedPageCount
import hunoia.luno.config.model.effectiveGridRows
import hunoia.luno.config.model.GridCell
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import hunoia.luno.core.AppContext
import hunoia.luno.gridpanel.geometry.gridPanelVisibleRows
import hunoia.luno.core.JsonSerializer
import hunoia.luno.gridpanel.widget.PanelAppWidgetHost
import hunoia.luno.gridpanel.widget.WidgetSpan
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import hunoia.luno.ui.automation.AutomationDraftHolder
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.FloatBallSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun StoredAction.shortcutInfo(): LauncherInfo.ShortcutInfo? {
    if (typeId != "app.launchShortcut") return null
    val data = (params["data"] as? JsonPrimitive)?.contentOrNull ?: return null
    return runCatching { JsonSerializer.decodeFromString<LauncherInfo.ShortcutInfo>(data) }.getOrNull()
}

internal fun List<StoredAction>.shortcutIconPaths(): List<String> =
    flatMap { ShortcutIconFiles.iconPathsOf(it) }

internal fun StoredAction.shortcutIconPaths(): List<String> = ShortcutIconFiles.iconPathsOf(this)

internal fun tryDeleteShortcutIcons(old: StoredAction?, new: StoredAction?) {
    val newPaths = listOfNotNull(new).flatMap { it.shortcutIconPaths() }.toSet()
    listOfNotNull(old).flatMap { it.shortcutIconPaths() }
        .filter { it !in newPaths }
        .forEach(ShortcutIconFiles::delete)
}

/** 落盘结果：区分「成功」「页满」「目标消失」，VM 按此给不同提示，不假报已保存。 */
internal enum class SaveOutcome { Saved, PageFull, TargetMissing }

/**
 * 选择页的统一落盘入口。TargetMissing = 目标面板/规则在选择期间被删，
 * 数据未写入；调用方按目标类型提示，不报「保存成功」。
 */
internal suspend fun saveSettingsAction(
    actionSelect: ActionSelect,
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit
): SaveOutcome {
    // 单值语义：传 null 即「无动作」（删键/存 null），选别的传那一个。
    val picked = getUiState().selectedRecord.action
    if (actionSelect.actionPanelId.isNotBlank()) {
        return saveActionPanelItem(actionSelect, picked)
    }
    if (actionSelect.gridPanelId.isNotBlank()) {
        return saveGridPanelItem(actionSelect, picked)
    }
    if (actionSelect.floatBallSlot.isNotBlank()) {
        saveFloatBallSlot(actionSelect.floatBallSlot, picked)
        return SaveOutcome.Saved
    }
    if (actionSelect.automationRuleId.isNotBlank()) {
        saveAutomationEffect(actionSelect, picked)
        return SaveOutcome.Saved
    }
    if (actionSelect.subGestureId.isNotBlank()) {
        GestureConfig.updateSubGestures { settings ->
            settings.copy(
                subGestures = settings.subGestures.map { gesture ->
                    if (gesture.id != actionSelect.subGestureId) return@map gesture
                    val oldAction = gesture.actionFor(actionSelect.triggerType, actionSelect.direction)
                    tryDeleteShortcutIcons(oldAction, picked)
                    gesture.withAction(actionSelect.triggerType, actionSelect.direction, picked)
                }
            )
        }
        return SaveOutcome.Saved
    }
    GestureConfig.updateButtons { list ->
        val mutableList = list.toMutableList()
        val index = mutableList.indexOfFirst { it.id == actionSelect.gestureButtonId }
        if (index < 0) return@updateButtons mutableList
        val oldAction = mutableList[index].actionFor(actionSelect.triggerType, actionSelect.direction)
        tryDeleteShortcutIcons(oldAction, picked)
        mutableList[index] = mutableList[index]
            .withAction(actionSelect.triggerType, actionSelect.direction, picked)
        mutableList
    }
    return SaveOutcome.Saved
}

/**
 * 面板项落盘：`actionPanelItemIndex < 0` 是新增（追加），>=0 是替换该项的长按变体。
 * 返回 TargetMissing = 面板在选择期间被删（数据未写入），放弃添加不算失败。
 */
private suspend fun saveActionPanelItem(actionSelect: ActionSelect, picked: StoredAction?): SaveOutcome {
    val panelId = actionSelect.actionPanelId
    val index = actionSelect.actionPanelItemIndex
    var outcome = SaveOutcome.Saved
    TuneConfig.updateAction { settings ->
        val panel = settings.actionPanels.panelById(panelId) ?: run {
            outcome = SaveOutcome.TargetMissing
            return@updateAction settings
        }
        val items = when {
            // 新增：选了「无动作」等于放弃本次添加，不改面板。
            picked == null || picked.typeId == "internal.none" -> return@updateAction settings
            // 改长按变体：变体本身不带变体（防止套娃）。
            index >= 0 -> panel.items.mapIndexed { i, item ->
                if (i == index) item.withLongPressVariant(picked.withoutVariantKey()) else item
            }
            else -> panel.items + picked
        }
        settings.copy(
            actionPanels = settings.actionPanels.withPanel(panel.copy(items = items))
        )
    }
    return outcome
}

/**
 * 网格面板项落盘：新增项落到 [ActionSelect.gridPanelPageIndex] 指定页的首个空位
 *（只在本页内扫，跨页不占位）。只进不出，无「替换已有项」分支，也无长按变体。
 * 页满返回 false，由调用方明确提示；静默丢弃还报「保存成功」就是「添加没反应」。
 */
private suspend fun saveGridPanelItem(actionSelect: ActionSelect, picked: StoredAction?): SaveOutcome {
    // 选了「无动作」等于放弃本次操作，不改面板，也不当失败提示。
    if (picked == null || picked.typeId == "internal.none") return SaveOutcome.Saved
    var outcome = SaveOutcome.PageFull
    TuneConfig.updateAction { settings ->
        val panel = settings.actionGridPanels.panelById(actionSelect.gridPanelId) ?: run {
            outcome = SaveOutcome.TargetMissing
            return@updateAction settings
        }
        val landscape = AppContext.get().resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val geo = panel.geometry(landscape)
        val columns = geo.columnCount.coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
        val pageIndex = actionSelect.gridPanelPageIndex.coerceIn(0, panel.derivedPageCount)
        val pageOccupied = panel.items.filter { it.pageIndex == pageIndex }.maxOfOrNull { it.bottom } ?: 0
        val rows = panel.effectiveGridRows(
            pageIndex,
            gridPanelVisibleRows(
                screenWdp = AppContext.get().resources.configuration.screenWidthDp.toFloat(),
                screenHdp = AppContext.get().resources.configuration.screenHeightDp.toFloat(),
                columns = columns,
                cardWidthFraction = geo.cardWidthFraction,
                cardHeightFraction = geo.cardHeightFraction,
            ),
        ).coerceAtLeast(pageOccupied + 1) // 行数无上限：永远多留一行装 1×1 动作项
        val free = GridPanelItemLayout.firstFreeSlot(
            items = panel.items.filter { it.pageIndex == pageIndex },
            columns = columns,
            rows = rows,
            spanX = 1,
            spanY = 1,
        ) ?: return@updateAction settings
        outcome = SaveOutcome.Saved
        settings.copy(
            actionGridPanels = settings.actionGridPanels.withPanel(
                panel.copy(
                    items = panel.items + GridPanelItem(
                        x = free.x,
                        y = free.y,
                        pageIndex = pageIndex,
                        kind = GridPanelItem.Kind.Action(listOf(picked.withoutVariantKey())),
                    ),
                ),
            ),
        )
    }
    return outcome
}

/**
 * 追加语义的落点：新项放到本页已占内容正下方（y = max(bottom)），宽度不满整行时
 * 在那一行内从左往右找空位，被同排项挡住则顺延下一行。
 * [rows] 是「塞进现有网格空隙」的上限；越界参数用 [extendTo] 传底部追加的
 * 最小需求（占位行数无上限），rows 会被抬到至少装得下 —— 页面行数是派生的，
 * 追加永远能往下撑，不该出现「放不下」。
 */
private fun bottomSlot(
    items: List<GridPanelItem>,
    columns: Int,
    rows: Int,
    spanX: Int,
    spanY: Int,
): GridCell? {
    var y = items.maxOfOrNull { it.bottom } ?: 0
    val effectiveRows = maxOf(rows, y + spanY)
    while (y + spanY <= effectiveRows) {
        for (x in 0..(columns - spanX)) {
            val clash = items.any {
                it.x < x + spanX && x < it.right && it.y < y + spanY && y < it.bottom
            }
            if (!clash) return GridCell(x, y)
        }
        y++
    }
    return null
}

/** 落盘结果：区分「面板不存在」和「真放不下」，两者给用户的提示不同。 */
/** 行数派生且追加无上限：结果只有「存进去了」和「面板不存在」两种。 */
internal enum class GridPanelSaveResult { Saved, NoPanel }

/**
 * 第三方小部件落盘：原尺寸先追加到底部，放不下则面积从大到小逐档降格填空。
 * 返回 false 时调用方必须 Toast 并回收 appWidgetId。
 */
internal suspend fun saveGridPanelWidget(
    actionSelect: ActionSelect,
    provider: String,
    appWidgetId: Int,
    spanX: Int,
    spanY: Int,
): GridPanelSaveResult {
    var result = GridPanelSaveResult.NoPanel
    TuneConfig.updateAction { settings ->
        val panel = settings.actionGridPanels.panelById(actionSelect.gridPanelId) ?: run {
            android.util.Log.w("GridPanelSave", "widget: panel not found requested=${actionSelect.gridPanelId} " +
            "available=${settings.actionGridPanels.panels.map { it.id to it.name }} select=${actionSelect}")
            return@updateAction settings
        }
        val landscape = AppContext.get().resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val geo = panel.geometry(landscape)
        val columns = geo.columnCount
            .coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
        val pageIndex = actionSelect.gridPanelPageIndex.coerceIn(0, panel.derivedPageCount)
        val rows = panel.effectiveGridRows(
            pageIndex,
            gridPanelVisibleRows(
                screenWdp = AppContext.get().resources.configuration.screenWidthDp.toFloat(),
                screenHdp = AppContext.get().resources.configuration.screenHeightDp.toFloat(),
                columns = columns,
                cardWidthFraction = geo.cardWidthFraction,
                cardHeightFraction = geo.cardHeightFraction,
            ),
        )
        val pageItems = panel.items.filter { it.pageIndex == pageIndex }
        // 追加优先：原尺寸先试「放到最下面」；实在不行才走降档扫空位兜底。
        // 行数派生且无上限：追加时把 rows 抬到装得下，运行态页面随内容长高。
        bottomSlot(pageItems, columns, maxOf(rows, pageItems.maxOfOrNull { it.bottom } ?: 0), spanX, spanY)?.let { cell ->
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.copy(
                        items = panel.items + GridPanelItem(
                            x = cell.x,
                            y = cell.y,
                            spanX = spanX,
                            spanY = spanY,
                            pageIndex = pageIndex,
                            kind = GridPanelItem.Kind.AppWidget(
                                appWidgetId = appWidgetId,
                                provider = provider,
                                minSpanX = spanX,
                                minSpanY = spanY,
                            ),
                        ),
                    ),
                ),
            )
        }
        // 降格序列与拖拽的重排思路分开：加项只填空（firstFreeSlot），放不下自动降档
        // 占更小的空位 —— 重排是编辑态拖拽的语义，加项不该惊动已有项。
        val candidates = buildList {
            for (h in spanY downTo 1) {
                for (w in spanX downTo 1) {
                    add(WidgetSpan(w, h))
                }
            }
        }.sortedByDescending { it.spanX * it.spanY }
            .distinct()
        for (span in candidates) {
            val cell = GridPanelItemLayout.firstFreeSlot(
                items = pageItems,
                columns = columns,
                rows = rows,
                spanX = span.spanX,
                spanY = span.spanY,
            ) ?: continue
            result = GridPanelSaveResult.Saved
            // 必须 return copy 的结果本身：return 原 settings 会让落盘形同没写。
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.copy(
                        items = panel.items + GridPanelItem(
                            x = cell.x,
                            y = cell.y,
                            spanX = span.spanX,
                            spanY = span.spanY,
                            pageIndex = pageIndex,
                            kind = GridPanelItem.Kind.AppWidget(
                                appWidgetId = appWidgetId,
                                provider = provider,
                                // 冻结实际落位的 span（不是 provider 声明的最小值）：
                                // minSpan 会经 options 传给 provider，必须与真实渲染一致。
                                // 页满降档低于 provider 声明最小值是本路径的既定权衡。
                                minSpanX = span.spanX,
                                minSpanY = span.spanY,
                            ),
                        ),
                    ),
                ),
            )
        }
        settings
    }
    if (result != GridPanelSaveResult.Saved) {
        android.util.Log.w("GridPanelSave", "widget not placed: result=$result span=$spanX x $spanY")
        PanelAppWidgetHost.get().release(appWidgetId)
    }
    return result
}

/**
 * 内置小部件落盘：同款「只填空 + 放不下降档」，无宿主 id 可回收。
 * 默认 span 4×1（def 声明），列数不够时逐档降到 1×1。
 */
internal suspend fun saveGridPanelInternalWidget(
    actionSelect: ActionSelect,
    widgetId: String,
): GridPanelSaveResult {
    val def = hunoia.luno.gridpanel.internal.InternalWidgetDefs.firstOrNull { it.id == widgetId }
    if (def == null) {
        android.util.Log.w("GridPanelSave", "internal widget: unknown id=$widgetId")
        return GridPanelSaveResult.NoPanel
    }
    var result = GridPanelSaveResult.NoPanel
    TuneConfig.updateAction { settings ->
        val panel = settings.actionGridPanels.panelById(actionSelect.gridPanelId) ?: run {
            android.util.Log.w("GridPanelSave", "internal: panel not found id=${actionSelect.gridPanelId}")
            return@updateAction settings
        }
        val landscape = AppContext.get().resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val geo = panel.geometry(landscape)
        val columns = geo.columnCount
            .coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
        val pageIndex = actionSelect.gridPanelPageIndex.coerceIn(0, panel.derivedPageCount)
        val rows = panel.effectiveGridRows(
            pageIndex,
            gridPanelVisibleRows(
                screenWdp = AppContext.get().resources.configuration.screenWidthDp.toFloat(),
                screenHdp = AppContext.get().resources.configuration.screenHeightDp.toFloat(),
                columns = columns,
                cardWidthFraction = geo.cardWidthFraction,
                cardHeightFraction = geo.cardHeightFraction,
            ),
        )
        val pageItems = panel.items.filter { it.pageIndex == pageIndex }
        // 追加优先：原尺寸先试「放到最下面」；行数无上限，追加时同步抬 rows。
        bottomSlot(pageItems, columns, maxOf(rows, pageItems.maxOfOrNull { it.bottom } ?: 0), def.spanX, def.spanY)?.let { cell ->
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.copy(
                        items = panel.items + GridPanelItem(
                            x = cell.x,
                            y = cell.y,
                            spanX = def.spanX,
                            spanY = def.spanY,
                            pageIndex = pageIndex,
                            kind = GridPanelItem.Kind.Internal(widgetId = widgetId, showBackground = false),
                        ),
                    ),
                ),
            )
        }
        val candidates = buildList {
            for (h in def.spanY downTo 1) {
                for (w in def.spanX downTo 1) {
                    add(WidgetSpan(w, h))
                }
            }
        }.sortedByDescending { it.spanX * it.spanY }.distinct()
        for (span in candidates) {
            val cell = GridPanelItemLayout.firstFreeSlot(
                items = pageItems,
                columns = columns,
                rows = rows,
                spanX = span.spanX,
                spanY = span.spanY,
            ) ?: continue
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.copy(
                        items = panel.items + GridPanelItem(
                            x = cell.x,
                            y = cell.y,
                            spanX = span.spanX,
                            spanY = span.spanY,
                            pageIndex = pageIndex,
                            kind = GridPanelItem.Kind.Internal(widgetId = widgetId, showBackground = false),
                        ),
                    ),
                ),
            )
        }
        settings
    }
    return result
}

/** 悬浮球槽位落盘。按槽名分发而非写死，后续开放长按/滑动槽只加 case。 */
private suspend fun saveFloatBallSlot(slot: String, picked: StoredAction?) {
    if (!FloatBallSlot.isValid(slot)) return
    TuneConfig.updateAction { settings ->
        settings.copy(
            floatBall = settings.floatBall.copy(
                tapAction = picked?.takeIf { it.typeId != "internal.none" },
            ),
        )
    }
}

private suspend fun saveAutomationEffect(actionSelect: ActionSelect, picked: StoredAction?) {
    val current = loadAutomationAction(actionSelect)
    tryDeleteShortcutIcons(current, picked)
    // 动作库条目存的就是引用本身（internal.libraryRef），运行时由分发器就地解析成条目动作。
    val action = picked?.withoutVariantKey()
    val path = actionSelect.automationStepPath
    fun applyTo(rule: AutomationRule): AutomationRule {
        if (rule.effect.stepAt(path) == null) {
            android.util.Log.w(
                "ActionSelect",
                "automation step path missing, rule=${rule.id} path=$path",
            )
        }
        return rule.copy(effect = rule.effect.withStepAt(path) { step ->
            step.copy(kind = hunoia.luno.config.model.RuleStepKind.ACTION, action = action)
        })
    }
    // 草稿优先：编辑页跳转时只暂存内存，未保存前不写库。
    // 无暂存时以库中规则为底新建暂存——选择页永不直写库，落盘统一由编辑页保存键负责。
    val base = AutomationDraftHolder.take(actionSelect.automationRuleId)
        ?: AutomationCenter.getRules().find { it.id == actionSelect.automationRuleId }
        ?: return
    AutomationDraftHolder.stage(applyTo(base))
}

private suspend fun loadAutomationAction(actionSelect: ActionSelect): StoredAction? {
    // 草稿命中就不读库（getRules 是一次 DataStore 读）。
    val draft = AutomationDraftHolder.take(actionSelect.automationRuleId)
    val rule = draft ?: AutomationCenter.getRules().find { it.id == actionSelect.automationRuleId }
    ?: return null
    return rule.effect.stepAt(actionSelect.automationStepPath)?.action
}

internal suspend fun updateShortcutInfosBody(
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit,
    addNewShortcut: (LauncherInfo, LauncherInfo.ShortcutInfo) -> Unit
) {
    val createLauncherInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryShortcutActivities(AppContext.get())
    }
    val launchLauncherInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryShortcuts(AppContext.get())
    }
    val selectedRecord = withContext(Dispatchers.Default) {
        getUiState().selectedRecord.let { record ->
            // 单值：选中项若是已卸载的快捷方式，整个槽清空。
            val selected = record.action?.shortcutInfo()
            if (selected == null) {
                record
            } else {
                val stillThere = createLauncherInfos.any { it.qualifiedName == selected.qualifiedName } ||
                        launchLauncherInfos.any { launcher ->
                            launcher.shortcuts.any { it.qualifiedNameWithIntents == selected.qualifiedNameWithIntents }
                        }
                if (stillThere) record else UiState.SelectedRecord()
            }
        }
    }
    val finalCreateList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<LauncherInfo>()
        val list2 = mutableListOf<LauncherInfo>()
        val selectedShortcutInfo = selectedRecord.action?.shortcutInfo()
        createLauncherInfos.forEach { launcherInfo ->
            val isSelected = selectedShortcutInfo?.packageName == launcherInfo.packageName
            if (isSelected) list1.add(launcherInfo) else list2.add(launcherInfo)
        }
        list1 + list2
    }
    val finalLaunchList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<LauncherInfo>()
        val list2 = mutableListOf<LauncherInfo>()
        val selectedShortcutInfos = selectedRecord.action?.shortcutInfo()
        launchLauncherInfos.forEach { launcherInfo ->
            val isSelected = selectedShortcutInfos?.packageName == launcherInfo.packageName
            if (isSelected) list1.add(launcherInfo) else list2.add(launcherInfo)
        }
        list1 + list2
    }
    updateUiState {
        it.copy(
            createShortcuts = finalCreateList,
            launchShortcuts = finalLaunchList,
            selectedRecord = selectedRecord
        )
    }
    // 补偿：queryShortcutActivities 只返回支持快捷方式创建的应用行（不带 shortcuts），
    // 已选中的快捷方式行必须单独补进它的 launcher 缓存，否则列表里看不到自己选了啥。
    getUiState()
        .selectedRecord
        .action
        ?.shortcutInfo()
        ?.let { shortcut ->
            createLauncherInfos.find { it.qualifiedName == shortcut.qualifiedName }
                ?.let { addNewShortcut(it, shortcut) }
        }
}

internal suspend fun updateAppInfosBody(
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit
) {
    val appInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryApps(AppContext.get())
    }
    val disabledApps = DisabledAppQuery.queryDisabledApplicationsOnIo(AppContext.get())
    val normalPackageNames = appInfos.map { it.packageName }.toSet()
    val filteredDisabledApps = disabledApps.filter { it.packageName !in normalPackageNames }
    val mergedApps = mutableListOf<AppInfo>()
    mergedApps.addAll(appInfos)
    mergedApps.addAll(filteredDisabledApps)
    val selectedRecord = withContext(Dispatchers.Default) {
        getUiState().selectedRecord.let { record ->
            val selectedApp = record.action?.launchAppRef()
            if (selectedApp == null) {
                record
            } else if (mergedApps.any { it.qualifiedName == selectedApp.qualifiedName }) {
                record
            } else {
                UiState.SelectedRecord()   // 已卸载：槽清空
            }
        }
    }
    val finalList = withContext(Dispatchers.Default) {
        // 选中的应用置顶（沿用既有排序），单值下最多只有一个。
        val selectedRef = selectedRecord.action?.launchAppRef()
        val (selected, rest) = mergedApps.partition { appInfo ->
            selectedRef != null && selectedRef.qualifiedName == appInfo.qualifiedName
        }
        selected.map { it.copy(miniWindow = selectedRef?.miniWindow ?: it.miniWindow) } + rest
    }
    updateUiState {
        it.copy(
            apps = finalList,
            selectedRecord = selectedRecord
        )
    }
}

internal suspend fun loadDataBody(
    actionSelect: ActionSelect,
    onUpdateState: (update: (UiState) -> UiState) -> Unit
) {
    val buttons = GestureConfig.getButtons()
    val gestureSettings = TuneConfig.getGesture()
    val subGestures = GestureConfig.getSubGestures().subGestures
    val actionLibraryEntries = ActionLibrary.getSettings().entries
    // 全局动作面板列表：选择页一行一个具名面板。
    val actionSettings = TuneConfig.getAction()
    val actionPanels = actionSettings.actionPanels.panels
    val gridPanels = actionSettings.actionGridPanels.panels
    val isAutomation = actionSelect.automationRuleId.isNotBlank()
    // 规则即动作：选择页专属分组（只留启用；自动化模式剔除正在编辑的自身，禁自引用）。
    val rules = ActionCatalog.rulesForPicker(
        AutomationCenter.getRules(),
        excludeRuleId = if (isAutomation) actionSelect.automationRuleId else "",
    )

    // pickerExcluded 决定「有哪些类型可被选」，必须恒非空（空集让整张定义表都可选）；
    // libraryExcluded 只过滤动作库已存条目；触钮路径不额外过滤，否则已保存的条目会被藏掉。
    val pickerExcluded = if (isAutomation) {
        ActionDefinitions.automationExcludedTypeIds()
    } else {
        ActionDefinitions.pickerExcludedTypeIds()
    }
    val libraryExcluded = if (isAutomation) ActionDefinitions.automationExcludedTypeIds() else emptySet()
    val button = if (isAutomation) null else buttons.find { it.id == actionSelect.gestureButtonId }
    val subGesture = if (isAutomation) null else subGestures.find { it.id == actionSelect.subGestureId }
    onUpdateState { state ->
        state.copy(
            isAutomation = isAutomation,
            subGestures = subGestures,
            excludedSubGestureId = actionSelect.subGestureId,
            actionLibraryEntries = actionLibraryEntries.filter {
                // 自动化放行带固定坐标的 tap/longPress 条目（参数优先于触点）；
                // 无坐标的条目在无触点的自动化路径必 InvalidParameter，继续藏。
                it.typeId !in libraryExcluded || it.storedAction.hasPointParams()
            },
            excludedTypeIds = pickerExcluded,
            rules = rules,
            actionPanels = actionPanels,
            gridPanels = gridPanels,
            isPanelItemMode = actionSelect.actionPanelId.isNotBlank() || actionSelect.gridPanelId.isNotBlank(),
        )
    }
    val action = when {
        // 面板项模式：读回当前面板项（改变体时预选原变体），不碰触钮槽。
        actionSelect.actionPanelId.isNotBlank() -> {
            val index = actionSelect.actionPanelItemIndex
            val panel = actionPanels.firstOrNull { it.id == actionSelect.actionPanelId }
            val item = if (index >= 0) panel?.items?.getOrNull(index) else null
            item?.longPressVariant() ?: item
        }
        // 网格面板只进不出：进选择页只会是加项，没有可预选的旧动作。
        actionSelect.gridPanelId.isNotBlank() -> null
        // 悬浮球槽：与触钮路径互斥，先判它，否则会掉进 button/subGesture 的 null 分支。
        actionSelect.floatBallSlot == FloatBallSlot.TAP -> actionSettings.floatBall.tapAction
        isAutomation -> loadAutomationAction(actionSelect)
        subGesture != null -> subGesture.actionFor(actionSelect.triggerType, actionSelect.direction)
        button != null -> button.actionFor(actionSelect.triggerType, actionSelect.direction)
        else -> null
    }
    onUpdateState { state ->
        state.copy(selectedRecord = state.selectedRecord.selectOne(action))
    }
}

private fun GestureButton.actionFor(triggerType: GestureTriggerType, direction: hunoia.luno.config.model.GestureDirection): StoredAction? {
    return when (triggerType) {
        GestureTriggerType.Tap -> tapActions
        GestureTriggerType.DoubleTap -> doubleTapActions
        GestureTriggerType.LongPress -> longPressActions
        GestureTriggerType.Slide -> slideActions.actionBy(direction)
        GestureTriggerType.SlideHold -> slideHoldActions.actionBy(direction)
        GestureTriggerType.LongSlide -> longSlideActions.actionBy(direction)
        GestureTriggerType.LongSlideHold -> longSlideHoldActions.actionBy(direction)
    }
}

private fun GestureButton.withAction(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection,
    action: StoredAction?
): GestureButton {
    return when (triggerType) {
        GestureTriggerType.Tap -> copy(tapActions = action)
        GestureTriggerType.DoubleTap -> copy(doubleTapActions = action)
        GestureTriggerType.LongPress -> copy(longPressActions = action)
        GestureTriggerType.Slide -> copy(slideActions = slideActions.withAction(direction, action))
        GestureTriggerType.SlideHold -> copy(slideHoldActions = slideHoldActions.withAction(direction, action))
        GestureTriggerType.LongSlide -> copy(longSlideActions = longSlideActions.withAction(direction, action))
        GestureTriggerType.LongSlideHold -> copy(longSlideHoldActions = longSlideHoldActions.withAction(direction, action))
    }
}

private fun hunoia.luno.config.model.SubGesture.actionFor(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection
): StoredAction? {
    return when (triggerType) {
        GestureTriggerType.Slide -> slideActionFor(direction)
        GestureTriggerType.SlideHold -> slideHoldActionFor(direction)
        GestureTriggerType.LongSlide -> longSlideActionFor(direction)
        GestureTriggerType.LongSlideHold -> longSlideHoldActionFor(direction)
        else -> null
    }
}

private fun hunoia.luno.config.model.SubGesture.withAction(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection,
    action: StoredAction?
): hunoia.luno.config.model.SubGesture {
    return when (triggerType) {
        GestureTriggerType.Slide -> withSlideAction(direction, action)
        GestureTriggerType.SlideHold -> withSlideHoldAction(direction, action)
        GestureTriggerType.LongSlide -> withLongSlideAction(direction, action)
        GestureTriggerType.LongSlideHold -> withLongSlideHoldAction(direction, action)
        else -> this
    }
}
