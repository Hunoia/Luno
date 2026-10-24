package hunoia.luno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 应用更新（`adb install -r` / 应用商店更新）后恢复常驻前台服务。
 *
 * 为什么需要：包被替换时系统会**停止**（不是杀进程）包内的服务，
 * `START_STICKY` 的重启承诺只在「进程被杀」时生效，这里拿不到；
 * 更新后没有任何路径会重新拉起悬浮球 —— 现象是装完新版悬浮球消失，
 * 必须进设置页把开关关掉再打开才回来。
 *
 * `ACTION_MY_PACKAGE_REPLACED` 只发给被替换的包自己，处理逻辑与开机一致，
 * 统一走 [ServiceAutoStart]。
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i(TAG, "package replaced: restoring services")
        ServiceAutoStart.restore(context)
    }

    private companion object {
        const val TAG = "LunoLauncher"
    }
}
