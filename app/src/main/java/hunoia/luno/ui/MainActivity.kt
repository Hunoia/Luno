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
     * 缓存 NavController 供 composition 重建后复用：Activity 不因转屏重建，但 composition
     * 会 dispose + recreate，`rememberNavController()` 的 saver 第二次重建时恢复不出栈。
     * 不能自己 new：`ComposeNavGraphNavigator` 是 internal，缺 navigator 会在 `setGraph` 闪退。
     * 进程被杀仍回 Home。
     */
    private var cachedNavController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        myEnableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val created = rememberNavController()
            // 无 key 的 remember：重建时重新求值，但 cached 已非 null，稳定返回同一实例。
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