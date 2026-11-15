package hunoia.luno.gridpanel

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hunoia.luno.bridge.system.NotificationAccess
import hunoia.luno.core.LunoLog as Log

/**
 * 上次播放媒体的应用（进程级单例）。
 *
 * 媒体部件只在面板打开时组合；面板关闭时部件无法捕获会话。要让「上次播放应用」跨面板
 * 关闭存活，由 [ensureListening] 在进程内注册一个常驻 [MediaSessionManager.OnActiveSessionsChangedListener]，
 * 无论面板开关都更新 [packageName]。
 *
 * 监听用 applicationContext、常驻不注销（进程生命周期内只注册一次）：不持有任何
 * Activity / Service 引用，无泄漏。会话从「在播」变「空」时不清空 [packageName]，
 * 保留上次 —— 这正是「移除后台后仍显示上次播放应用」的关键。
 */
object LastMediaApp {

    /** 上次播放媒体的应用包名；空串 = 本进程内还没播过。Compose 状态，部件读它即订阅。 */
    var packageName by mutableStateOf("")
        private set

    private var listening = false

    /** 幂等：注册常驻会话监听。仅在 [NotificationAccess.isGranted] 时注册；失败不置 [listening]，下次重试。 */
    fun ensureListening(context: Context) {
        if (listening) return
        val app = context.applicationContext
        if (!NotificationAccess.isGranted(app)) return
        val manager = app.getSystemService(MediaSessionManager::class.java) ?: return
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            sessions.orEmpty()
                .filter(::isActiveSession)
                .sortedWith(activeFirst)
                .firstOrNull()
                ?.let { note(it) }
        }
        runCatching {
            manager.addOnActiveSessionsChangedListener(listener, NotificationAccess.listenerComponent(app))
            listening = true
        }.onFailure { Log.w("LastMediaApp", "addOnActiveSessionsChangedListener failed", it) }
    }

    /** 记录一次有效的播放（非空包名才覆盖）。 */
    fun note(controller: MediaController) {
        if (controller.packageName.isNotBlank()) packageName = controller.packageName
    }

    /**
     * 只保留「真的在播/播过」的会话：很多 app（阅读器、浏览器）常驻一个空 MediaSession ——
     * STATE_NONE、无播放动作、位置 0，全都不算。与媒体部件的展示过滤共用同一谓词。
     */
    fun isActiveSession(c: MediaController): Boolean {
        val pb = c.playbackState ?: return false
        return pb.state != PlaybackState.STATE_NONE &&
            pb.actions.and(PlaybackState.ACTION_PLAY) != 0L &&
            (pb.state == PlaybackState.STATE_PLAYING ||
                pb.state == PlaybackState.STATE_PAUSED ||
                pb.state == PlaybackState.STATE_BUFFERING ||
                pb.position > 0L)
    }

    /** 本地播放优先、正在播放优先：蓝牙/投屏会话常驻但不播，别抢前排。 */
    private val activeFirst = compareByDescending<MediaController> {
        it.playbackState?.state == PlaybackState.STATE_PLAYING
    }
}
