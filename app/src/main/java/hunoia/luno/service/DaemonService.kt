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
import android.provider.Settings
import android.text.TextUtils
import hunoia.luno.R
import hunoia.luno.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DaemonService : Service() {

    companion object {
        private const val PREF_NAME = "daemon"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "daemon"
    }

    private lateinit var prefs: SharedPreferences
    private var contentObserver: ContentObserver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var selfWroteValue = ""
    private val serviceClassName = SideGestureService::class.java.name

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.getBoolean(KEY_KEEP_ALIVE, false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasWriteSecureSettings()) {
            stopSelf()
            return START_NOT_STICKY
        }

        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        screenReceiver?.let { unregisterReceiver(it) }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForegroundNotification(notificationManager)

        val serviceComponentName = ComponentName(packageName, serviceClassName)
        val serviceId = serviceComponentName.flattenToShortString()

        selfWroteValue = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

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
        val observer = contentObserver ?: error("ContentObserver not initialized")
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            true,
            observer
        )

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
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)

        restoreIfNeeded(serviceComponentName, serviceId, selfWroteValue)

        return START_STICKY
    }

    private fun restoreIfNeeded(serviceComponentName: ComponentName, serviceId: String, current: String) {
        if (!hasWriteSecureSettings()) {
            android.util.Log.w("LunoLauncher", "restoreIfNeeded skipped: WRITE_SECURE_SETTINGS revoked")
            return
        }
        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
        }.getOrDefault(false)

        if (!accessibilityEnabled) {
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
