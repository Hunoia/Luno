package hunoia.luno.quicklaunch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import hunoia.luno.BuildConfig

class QuickAppLauncherActivity : Activity() {

    private var activityCreateTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCreateTime = System.currentTimeMillis()
        val intent = intent
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "activity: onCreate taskId=$taskId isTaskRoot=$isTaskRoot action=${intent?.action} flags=${intent?.flags} data=${intent?.dataString} categories=${intent?.categories} extras=${intent?.extras?.keySet()}")
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "activity: onCreate callingPackage=$callingPackage referrer=$referrer source=externalActivity")

        QuickLaunchFacade.showOverlay()

        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "activity: calling finish() reason=immediateAfterShow")
        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "activity: onNewIntent taskId=$taskId isTaskRoot=$isTaskRoot action=${intent.action} flags=${intent.flags} data=${intent.dataString}")
    }

    override fun onResume() {
        super.onResume()
        val elapsed = if (activityCreateTime > 0) System.currentTimeMillis() - activityCreateTime else -1L
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "activity: onResume taskId=$taskId isTaskRoot=$isTaskRoot elapsedSinceCreate=${elapsed}ms")
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "activity: onPause taskId=$taskId")
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "activity: onDestroy taskId=$taskId")
        super.onDestroy()
    }

    override fun finish() {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "activity: finish called taskId=$taskId")
        super.finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
