package hunoia.luno.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import hunoia.luno.R
import hunoia.luno.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import hunoia.luno.core.LOG_TAG

class DaemonService : Service() {

    companion object {
        private const val PREF_NAME = "daemon"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "daemon"

        /** 用户开保活时 KeepAliveUseCase 显式携带：跨进程 prefs 缓存不可靠的兜底。 */
        const val ACTION_USER_ENABLE = "hunoia.luno.action.USER_ENABLE_DAEMON"

        /** 回写最小间隔：防「无障碍启动即崩」时的秒级回写循环（见 restoreIfNeeded）。 */
        private const val RESTORE_WRITE_MIN_INTERVAL_MS = 5_000L
    }

    private lateinit var prefs: SharedPreferences
    private var contentObserver: ContentObserver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var selfWroteValue = ""
    private var lastRestoreWriteAtMs = 0L
    private val serviceClassName = SideGestureService::class.java.name

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 只要是被 startForegroundService 拉起的，就必须先 startForeground：
        // 先判条件再 stopSelf 在部分 ROM 上仍会触发
        // ForegroundServiceDidNotStartInTimeException（5s 内未转前台）。
        // 不满足条件时短暂显示通知后立即撤掉，比进程崩溃安全。
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForegroundNotification(notificationManager)

        // 用户在主进程刚打开开关（ACTION_USER_ENABLE，磁盘写在前）：直接信任。
        // 其余来源（开机恢复、START_STICKY 重启）按落盘开关自判 —— 本服务已归并到
        // :service 进程，prefs 是各进程独立缓存，不能当跨进程事实源。
        val userEnabled = intent?.action == ACTION_USER_ENABLE
        if (!userEnabled && !prefs.getBoolean(KEY_KEEP_ALIVE, false)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasWriteSecureSettings()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val serviceComponentName = ComponentName(packageName, serviceClassName)
        val serviceId = serviceComponentName.flattenToShortString()

        selfWroteValue = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        // onStartCommand 可能被反复调用（START_STICKY 重启、外部多次 startService），
        // 监听器本身跨调用一直有效：已注册就跳过，别每次都「反注册再注册」白付一轮开销。
        if (contentObserver == null) {
            contentObserver = object : ContentObserver(Handler(mainLooper)) {
                override fun onChange(selfChange: Boolean) {
                    val current = Settings.Secure.getString(
                        contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    ) ?: ""
                    if (current == selfWroteValue) return
                    restoreIfNeeded(serviceComponentName, serviceId, current)
                }
            }
            // notifyForDescendants=false：只关心这一个 key，没有子节点需要穿透监听。
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                contentObserver!!,
            )
        }

        if (screenReceiver == null) {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val current = Settings.Secure.getString(
                        contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    ) ?: ""
                    if (current == selfWroteValue) return
                    restoreIfNeeded(serviceComponentName, serviceId, current)
                }
            }
            screenReceiver?.let {
                registerReceiver(it, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
            }
        }

        restoreIfNeeded(serviceComponentName, serviceId, selfWroteValue)

        return START_STICKY
    }

    private fun restoreIfNeeded(serviceComponentName: ComponentName, serviceId: String, current: String) {
        if (!hasWriteSecureSettings()) {
            android.util.Log.w(LOG_TAG, "restoreIfNeeded skipped: WRITE_SECURE_SETTINGS revoked")
            return
        }
        // 回写节流：无障碍服务启动即崩的场景下，ContentObserver / SCREEN_ON / onStartCommand
        // 三路触发会把「回写 → 系统重绑 → 再崩」变成秒级循环；最小间隔内不重复回写，
        // 循环断成稀疏重试，用户来得及处理（正常的一次性恢复不受影响）。
        val now = SystemClock.elapsedRealtime()
        if (now - lastRestoreWriteAtMs < RESTORE_WRITE_MIN_INTERVAL_MS) return
        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
        }.getOrDefault(false)

        if (!accessibilityEnabled) {
            lastRestoreWriteAtMs = now
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1"
            )
        }

        if (tokenContains(current, serviceComponentName)) {
            selfWroteValue = current
            return
        }

        val newValue = if (current.isBlank()) serviceId else "$serviceId:$current"
        selfWroteValue = newValue
        lastRestoreWriteAtMs = now
        Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            newValue
        )
        updateNotification("已恢复无障碍服务", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    private fun tokenContains(value: String, componentName: ComponentName): Boolean {
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(value)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == componentName) return true
        }
        return false
    }

    private fun hasWriteSecureSettings(): Boolean {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundNotification(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "服务保活",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("手势待命")
            .setContentText("无障碍服务保活中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startAsForeground(notification)
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, content: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        screenReceiver?.let { unregisterReceiver(it) }
    }
}
