package hunoia.luno.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import hunoia.luno.ui.SideGestureApp

class MainActivity : ComponentActivity() {

    /**
     * NavController 缓存在 Activity 上，供 composition 重建后复用。
     *
     * ## 为什么要缓存
     *
     * manifest 已经声明了 `configChanges`，Activity 不会因转屏重建（实测
     * `onConfigurationChanged` 两次 instance 一致）。但 **Compose 的 composition 仍然会
     * dispose + recreate**，而 `rememberNavController()` 是 `rememberSaveable` —— 靠
     * `SaveableStateRegistry` 恢复。活动进程里栈能跨 composition 重建恢复的前提是
     * `SaveableStateRegistry.performSave()` 被调用过，而那通常由 `onSaveInstanceState`
     * 驱动；Activity 不重建时它不一定被调用。实测结果正是「首次转屏能恢复、第二次恢复
     * 不出来」：同一个 `rememberNavController()` 调用返回了不同实例
     * （`@144000409` → `@5824841`），栈回到 `[Home]`。
     *
     * 缓存后，composition 重建时 `rememberNavController()` 仍会照常调用（组合结构不变、
     * navigator 注册齐全），但实际传给 `SideGestureApp` 的始终是首次那个实例。
     *
     * ## 为什么不能自己 `NavHostController(this)`
     *
     * `rememberNavController()` 内部的 `createNavController` 还会注册三个 navigator：
     * `ComposeNavGraphNavigator` / `ComposeNavigator` / `DialogNavigator`。少任何一个都会在
     * `setGraph` 时抛 `Could not find Navigator with name "composable"` 直接闪退；而
     * `ComposeNavGraphNavigator` 是 internal 的，外部无法补齐。
     *
     * ## 已知取舍
     *
     * 缓存的实例不参与 `savedInstanceState`，进程被杀后重启会回 Home。考虑到这条恢复路径
     * 本来就是坏的（`rememberNavController` 的 saver 在上面那种重建下恢复不出来），这是
     * 平级替换而非退化。要真正保住，得让 NavController 自己接 `saveState`/`restoreState`。
     */
    private var cachedNavController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        myEnableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val created = rememberNavController()
            // 无 key 的 remember：每次 composition 重建都重新求值，但首次之后 cached
            // 已非 null，于是稳定返回同一个实例。
            val navController = remember { cachedNavController ?: created.also { cachedNavController = it } }
            SideGestureApp(navController)
        }

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.appTasks.firstOrNull()?.setExcludeFromRecents(true)
    }
}

private fun ComponentActivity.myEnableEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(DefaultLightScrim, DefaultDarkScrim),
    )
}

private val DefaultLightScrim = Color.argb(0xBF, 0xFF, 0xFF, 0xFF)
private val DefaultDarkScrim = Color.argb(0xBF, 0x00, 0x00, 0x00)