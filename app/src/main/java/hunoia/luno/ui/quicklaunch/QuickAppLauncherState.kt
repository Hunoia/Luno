package hunoia.luno.ui.quicklaunch

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.promeg.pinyinhelper.Pinyin
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.freeze.FreezeFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppSearch.key
import hunoia.luno.quicklaunch.query.AppSearch.sortApps
import hunoia.luno.quicklaunch.query.QuickAppLauncherAppList
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.ui.theme.AnimPostHideDelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class QuickAppLauncherState(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    initialSettings: QuickAppLauncherSettings,
    private val requestEnableFrozenPackage: (String, (Boolean) -> Unit) -> Unit,
    private val onCloseAnimatedRaw: () -> Unit,
    private val onLaunch: (AppInfo, Boolean) -> Boolean,
    private val onRegisterCloseAnimated: ((() -> Unit) -> Unit)?,
) {
    var appListState by mutableStateOf(QuickAppLauncherAppList(emptyList(), emptySet()))
        private set
    var tokens by mutableStateOf(emptyList<String>())
        private set
    var launcherSettings by mutableStateOf(initialSettings)
        private set
    var keyboardExpanded by mutableStateOf(true)
    var panelVisible by mutableStateOf(false)
    var closing by mutableStateOf(false)

    val closeAnimated: () -> Unit = {
        if (!closing) {
            closing = true
            panelVisible = false
            coroutineScope.launch {
                delay(AnimPostHideDelay)
                onCloseAnimatedRaw()
            }
        }
    }

    init {
        coroutineScope.launch {
            ConfigProvider.quickAppLauncherSettings.collectLatest { launcherSettings = it }
        }
        loadApps()
        onRegisterCloseAnimated?.invoke(closeAnimated)
    }

    private fun loadApps() {
        coroutineScope.launch {
            val state = withContext(Dispatchers.IO) {
                FreezeFacade.queryQuickAppLauncherApps(context)
            }
            appListState = state
            prewarmIcons()
        }
    }

    val visibleApps: List<AppInfo>
        get() = appListState.apps

    val filteredApps: List<AppInfo> by derivedStateOf {
        sortApps(visibleApps, launcherSettings, tokens)
    }

    fun addToken(token: String) {
        tokens = tokens + token
    }

    fun deleteToken() {
        tokens = tokens.dropLast(1)
    }

    fun clearTokens() {
        tokens = emptyList()
    }

    fun toggleKeyboard() {
        keyboardExpanded = !keyboardExpanded
    }

    fun expandKeyboard() {
        keyboardExpanded = true
    }

    private fun prewarmIcons() {
        coroutineScope.launch(Dispatchers.IO) {
            val apps = appListState.apps
            for (app in apps.take(25)) {
                if (QuickLaunchFacade.getCachedIcon(app.packageName) == null) {
                    QuickLaunchFacade.loadIcon(context, app.packageName)?.let {
                        QuickLaunchFacade.cacheIcon(app.packageName, it)
                    }
                }
            }
        }
    }

    fun launchApp(app: AppInfo, isFrozen: Boolean, miniWindow: Boolean, debugPrefix: String?) {
        QuickLaunchFacade.launchApp(
            context = context,
            coroutineScope = coroutineScope,
            app = app,
            isFrozen = isFrozen,
            miniWindow = miniWindow,
            debugPrefix = debugPrefix,
            requestEnableFrozenPackage = requestEnableFrozenPackage,
            log = { message -> android.util.Log.d("LunoLauncher", message) },
            onLaunch = onLaunch,
            onLaunched = closeAnimated
        )
    }
}

internal enum class Page { App, Settings, Password }

internal fun pageMatchesTokens(pageName: String, tokens: List<String>): Boolean {
    val text = pageName.lowercase()
    val pinyin = buildString { pageName.forEach { append(Pinyin.toPinyin(it).lowercase()) } }
    val initials = buildString { pageName.forEach { append(Pinyin.toPinyin(it).first().lowercaseChar()) } }
    return matchesTokens(text, tokens) || matchesTokens(pinyin, tokens) || matchesTokens(initials, tokens)
}

internal fun matchesTokens(text: String, tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return false
    if (tokens.size > text.length) return false
    for (start in 0..(text.length - tokens.size)) {
        var ok = true
        for (i in tokens.indices) {
            val token = tokens[i]
            val ch = text[start + i]
            if (token.length == 1 && token[0].isDigit()) {
                if (token[0] != ch) { ok = false; break }
            } else if (!token.lowercase().contains(ch.lowercaseChar())) { ok = false; break }
        }
        if (ok) return true
    }
    return false
}
