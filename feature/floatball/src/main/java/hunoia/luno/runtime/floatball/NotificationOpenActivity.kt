package hunoia.luno.runtime.floatball

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log

/**
 * 透明跳板：把通知的 contentIntent 挪到前台来发送。
 *
 * 通知胶囊在 `:service`（overlay 窗口），直接 `pi.send()` 属于后台 Activity 启动，
 * Android 14+ 的后台启动限制会**静默**拦截——表现是「点了没反应、条目还消失」。
 * 这个 Activity 只是把 `PendingIntent` 带到前台一次：`onCreate` 里发送、立刻 `finish`，
 * 全程无界面、无动画。失败经 [EXTRA_RESULT_RECEIVER] 回传，由服务走应用启动回退。
 *
 * 进程跟 `:service`（见 manifest）：胶囊只可能在服务活着时出现，跳板跟着服务走，
 * 避免主进程冷启动的几百毫秒延迟。
 */
class NotificationOpenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pendingIntent = intent?.getParcelableExtra(EXTRA_PENDING_INTENT, PendingIntent::class.java)
        val receiver = intent?.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        val ok = pendingIntent != null && sendPendingIntent(pendingIntent)
        Log.i(LOG_TAG, "NotificationOpenActivity send ok=$ok hasPi=${pendingIntent != null}")
        receiver?.send(if (ok) RESULT_OK else RESULT_FAILED, null)
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun sendPendingIntent(pendingIntent: PendingIntent): Boolean = runCatching {
        // 前台 Activity 发送不受后台启动限制；无需 ActivityOptions（显式 BAL 模式
        // 在新 SDK 已废弃），直接送就是系统点通知的同一条路径。
        pendingIntent.send(this, 0, null)
    }.isSuccess

    companion object {
        private const val EXTRA_PENDING_INTENT = "hunoia.luno.notification.extra.PENDING_INTENT"
        private const val EXTRA_RESULT_RECEIVER = "hunoia.luno.notification.extra.RESULT_RECEIVER"
        internal const val RESULT_OK = 1
        private const val RESULT_FAILED = 0

        /**
         * 起跳板发送 [pendingIntent]。返回 false = 起跳板这一步就失败（无界面 / 被拦），
         * 调用方直接走应用启动回退；true = 结果经 [onResult] 异步回来（主线程）。
         */
        fun start(
            context: Context,
            pendingIntent: PendingIntent,
            onResult: (Boolean) -> Unit,
        ): Boolean {
            val intent = Intent(context, NotificationOpenActivity::class.java).apply {
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
                putExtra(
                    EXTRA_RESULT_RECEIVER,
                    OpenResultReceiver(Handler(Looper.getMainLooper()), onResult),
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            return runCatching { context.startActivity(intent) }.isSuccess
        }
    }
}

/** 跳板回执：跨进程把发送结果带回 `:service`（[ResultReceiver] 是 Parcelable）。 */
private class OpenResultReceiver(
    handler: Handler,
    private val onResult: (Boolean) -> Unit,
) : ResultReceiver(handler) {
    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        onResult(resultCode == NotificationOpenActivity.RESULT_OK)
    }
}
