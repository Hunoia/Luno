package hunoia.luno.quicklaunch

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.service.SideGestureService

class QuickAppLauncherActivity : Activity() {

    private companion object {
        const val EXPECTED_ACTION = "hunoia.luno.action.QUICK_APP_LAUNCHER"
    }

    private var activityCreateTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCreateTime = System.currentTimeMillis()
        val intent = intent

        if (intent?.action == EXPECTED_ACTION && BuildConfig.DEBUG) Log.d(
            "LunoLauncher",
            "activity: onCreate taskId=$taskId isTaskRoot=$isTaskRoot action=${intent.action} flags=${intent.flags}"
        )

        tryShowOverlay(intent?.action)

        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "activity: calling finish() reason=immediateAfterShow")
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) Log.d(
            "LunoLauncher",
            "activity: onNewIntent taskId=$taskId isTaskRoot=$isTaskRoot action=${intent.action} flags=${intent.flags}"
        )

        tryShowOverlay(intent.action)

        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "activity: calling finish() reason=afterNewIntent")
        finish()
    }

    override fun onResume() {
        super.onResume()
        val elapsed = if (activityCreateTime > 0) System.currentTimeMillis() - activityCreateTime else -1L
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "activity: onResume taskId=$taskId isTaskRoot=$isTaskRoot elapsedSinceCreate=${elapsed}ms")
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "activity: onPause taskId=$taskId")
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "activity: onDestroy taskId=$taskId")
        super.onDestroy()
    }

    override fun finish() {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "activity: finish called taskId=$taskId")
        super.finish()
    }

    private fun tryShowOverlay(action: String?): Boolean {
        if (action != EXPECTED_ACTION) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "activity: rejected action=$action")
            return false
        }
        if (!isAccessibilityServiceEnabled()) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "activity: accessibility service not enabled")
            Toast.makeText(this, R.string.quick_launcher_requires_accessibility, Toast.LENGTH_SHORT).show()
            return false
        }
        QuickLaunchFacade.showOverlay()
        return true
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val service = ComponentName(packageName, SideGestureService::class.java.name)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == service) return true
        }
        return false
    }
}
