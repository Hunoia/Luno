package hunoia.luno.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import hunoia.luno.action.model.StoredAction
import hunoia.luno.core.JsonSerializer

/**
 * 分发 IPC 客户端（主进程 / :service 进程通用）。
 *
 * [bind] 由 Application.onCreate 调用，两进程都连。bind 是异步的，
 * 未连上时 [dispatch]/[isReady] 直接返回 false，调用方负责提示。
 */
object DispatchClient {

    private var stub: IDispatchService? = null
    @Volatile private var bindRequested = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            stub = runCatching { IDispatchService.Stub.asInterface(binder) }.getOrNull()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stub = null
        }
    }

    fun bind(appContext: Context) {
        if (bindRequested) return
        bindRequested = true
        runCatching {
            val intent = Intent(appContext, DispatchService::class.java)
            appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }
    }

    /** 分发链就绪。跨进程走 Binder，同进程 bind 未完成时兜底直查。 */
    fun isReady(): Boolean {
        val viaBinder = runCatching { stub?.isReady() == true }.getOrDefault(false)
        return viaBinder || SideGestureService.current?.isDispatchReady() == true
    }

    /** 转交 :service 分发。false = 未送达（未连接或对端未就绪）。 */
    fun dispatch(action: StoredAction): Boolean {
        val s = stub ?: return false
        return runCatching {
            s.dispatchAction(JsonSerializer.encodeToString(action))
        }.getOrDefault(false)
    }
}
