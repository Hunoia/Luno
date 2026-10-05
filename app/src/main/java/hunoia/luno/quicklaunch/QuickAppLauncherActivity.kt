package hunoia.luno.quicklaunch

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.quicklaunch.launch.DisabledPackageEnabler
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.ui.quicklaunch.QuickAppLauncherRoot
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 全屏透明快捷启动器面板（Activity 版，与 QuickSearchActivity 同构）。
 *
 * 之前是无障碍 overlay 窗口：常驻 ComposeView + 1×1 收缩 + 跨进程 addView，
 * 唤起慢且占常驻窗口。改透明 Activity 后窗口生命周期交还系统，进退场只走 Compose。
 *
 * 约定：singleTask + noHistory + 只开不关。重复触发走 onNewIntent（内容保留，不重置）。
 */
class QuickAppLauncherActivity : ComponentActivity() {

    private var initialSettings by mutableStateOf<QuickAppLauncherSettings?>(null)
    private var advancedSettings by mutableStateOf(AdvancedSettings())
    private var closeAnimated: (() -> Unit)? = null

    private val disabledPackageEnabler by lazy {
        DisabledPackageEnabler(
            context = applicationContext,
            scopeProvider = { lifecycleScope },
            log = { message -> if (BuildConfig.DEBUG) Log.i("LunoLauncher", message) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickAppLauncherActivity onCreate taskId=$taskId")
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = ConfigProvider.getQuickAppLauncherSettings()
            val advanced = runCatching { ConfigProvider.getAdvancedSettings() }.getOrDefault(AdvancedSettings())
            launch(Dispatchers.Main.immediate) {
                advancedSettings = advanced
                initialSettings = settings
            }
        }
        // 返回键不直接 finish：走退场动画。
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    (closeAnimated ?: { finish() }).invoke()
                }
            },
        )
        setContent {
            SideGestureTheme {
                val settings = initialSettings
                if (settings != null) {
                    QuickAppLauncherRoot(
                        initialSettings = settings,
                        requestEnableDisabledPackage = disabledPackageEnabler::request,
                        onCloseAnimated = { finish() },
                        onClose = { (closeAnimated ?: { finish() }).invoke() },
                        onLaunch = { appInfo, miniWindow ->
                            val success = Launcher.launchAppInfo(
                                context = this,
                                appInfo = appInfo,
                                miniWindow = miniWindow,
                                miniWindowSettings = advancedSettings.miniWindowSettings(),
                            )
                            if (success) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    runCatching {
                                        ConfigProvider.recordQuickAppLaunch("${appInfo.packageName}/${appInfo.className}")
                                    }
                                }
                            }
                            success
                        },
                        onRegisterCloseAnimated = { callback -> closeAnimated = callback },
                        onStateReady = { state ->
                            state.resetForNewSession()
                            state.setPanelShown(true)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask 重复触发：只开不关，面板已在前台则保留当前内容，不做任何事。
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickAppLauncherActivity onNewIntent taskId=$taskId")
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickAppLauncherActivity destroyed")
        super.onDestroy()
    }

    companion object {
        /**
         * 从手势/Service 上下文启动启动器面板。
         *
         * BAL（后台启动限制）：与 QuickSearchActivity.start 同路径——应用有常驻可见窗口
         * （手势触钮）时 BAL_ALLOW_VISIBLE_WINDOW 放行。真机若被拦：logcat 会有
         * "Background activity start ... blocked"，届时再处理。
         */
        fun start(context: Context) {
            val intent = Intent(context, QuickAppLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching { context.startActivity(intent) }.isSuccess
            if (!ok) {
                Log.w("LunoLauncher", "QuickAppLauncherActivity start failed")
                showToast(R.string.quick_launcher_start_failed)
            }
        }
    }
}
