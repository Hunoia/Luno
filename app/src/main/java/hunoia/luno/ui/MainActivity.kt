package hunoia.luno.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import hunoia.luno.service.ServiceAutoStart
import hunoia.luno.ui.SideGestureApp

class MainActivity : ComponentActivity() {

    /**
     * 缓存 NavController 供 composition 重建后复用：Activity 不因转屏重建，但 composition
     * 会 dispose + recreate，`rememberNavController()` 的 saver 第二次重建时恢复不出栈。
     * 不能自己 new：`ComposeNavGraphNavigator` 是 internal，缺 navigator 会在 `setGraph` 闪退。
     * 进程被杀仍回 Home。
     */
    private var cachedNavController: NavHostController? = null

    /** 系统 Toast 在 MIUI 上会受「通知」权限限制：未授权时后台 Toast 被系统静默丢弃。 */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        myEnableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        // 更新/重启后的恢复广播可能被 ROM 的自启动管控拦下（接收器跑不到），
        // 主进程随本 Activity 启动时按落盘配置兜底恢复常驻服务，进程内只做一次。
        ServiceAutoStart.restoreOnce(applicationContext)

        setContent {
            val created = rememberNavController()
            // 无 key 的 remember：重建时重新求值，但 cached 已非 null，稳定返回同一实例。
            val navController = remember { cachedNavController ?: created.also { cachedNavController = it } }
            SideGestureApp(navController)
        }

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.appTasks.firstOrNull()?.setExcludeFromRecents(true)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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