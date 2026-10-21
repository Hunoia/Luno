package hunoia.luno.ui.actionselect

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.core.ActionSelectClosedEvent
import hunoia.luno.core.Events
import hunoia.luno.core.JsonSerializer
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.navigation.LocalGridPanels
import hunoia.luno.ui.navigation.LocalNavController
import hunoia.luno.ui.theme.SideGestureTheme

/**
 * 动作选择页的独立宿主（`:service` 进程），供网格面板的加项/换动作/设变体使用。
 *
 * 与面板浮窗同进程：透明 Activity 承载同一个 [ActionSelectContent]，选择经 DataLoader
 * 落盘（选择页无返回值），finish 即回面板，回显事件走进程内 Events 总线。
 * 环境 locals 照抄 SideGestureApp 的提供方：ActionDisplay 渲染行名要用。
 */
class ActionSelectHostActivity : ComponentActivity() {

    /** 参数化内容源。`onNewIntent`（singleTask 重复触发）要能换掉，故走状态。 */
    private var actionSelect by mutableStateOf<ActionSelect?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        applyIntent(intent)
        setContent {
            SideGestureTheme {
                // UDFComponentDefaults 组合期无条件读 LocalNavController（Finish 默认
                // navigateUp）；本宿主自己接了 Finish→finish()，默认分支不可达但不给会崩。
                val navController = rememberNavController()
                val actionSettings by TuneConfig.action
                    .collectAsStateWithLifecycle(initialValue = ActionSettings())
                val actionLibrarySettings by ActionLibrary.settings
                    .collectAsStateWithLifecycle(initialValue = NewActionLibrarySettings())
                val select = actionSelect
                CompositionLocalProvider(
                    LocalNavController provides navController,
                    LocalActionLibraryEntries provides actionLibrarySettings.entries,
                    LocalActionPanels provides actionSettings.actionPanels.panels,
                    LocalGridPanels provides actionSettings.actionGridPanels.panels,
                ) {
                    // 参数解不出来（applyIntent 已 finish）时画空壳，别碰选择页内容。
                    if (select != null) {
                        ActionSelectContent(
                            onDismissRequest = { finish() },
                            actionSelect = select,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask 重复触发：不 setIntent 则 getIntent() 仍是旧的，页面会停在上一次
        // 的面板/索引（VM key 随 ActionSelectContent 换新，无残留状态）。
        setIntent(intent)
        applyIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 回显信号：网格面板浮窗此刻隐藏让位中（组合保留），等本事件恢复显示。
        // 挂 onDestroy 而非 finish：任务被移除等「没走 finish 的销毁」也要唤醒面板，
        // 否则窗口停在 alpha 0；配置变更已被 manifest 的 configChanges 挡掉。
        if (isFinishing) Events.post(ActionSelectClosedEvent())
    }

    private fun applyIntent(intent: Intent?) {
        val encoded = intent?.getStringExtra(EXTRA_ACTION_SELECT).orEmpty()
        val select = runCatching { JsonSerializer.decodeFromString<ActionSelect>(encoded) }.getOrNull()
        if (select == null) {
            actionSelect = null
            finish()
            return
        }
        actionSelect = select
    }

    companion object {
        private const val EXTRA_ACTION_SELECT = "hunoia.luno.extra.ACTION_SELECT"

        /** 起选择页；返回 false = 启动失败，调用方须自行兜底（面板的让位不可逆）。 */
        fun start(context: Context, actionSelect: ActionSelect): Boolean {
            val intent = Intent(context, ActionSelectHostActivity::class.java).apply {
                // 宿主现在从浮窗（服务侧，无 activity 上下文）发起，必须 NEW_TASK。
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_ACTION_SELECT, JsonSerializer.encodeToString(actionSelect))
            }
            return runCatching { context.startActivity(intent) }.isSuccess
        }
    }
}