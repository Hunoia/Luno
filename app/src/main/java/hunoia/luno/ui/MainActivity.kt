package hunoia.luno.ui

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import hunoia.luno.ui.SideGestureApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        myEnableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SideGestureApp()
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
