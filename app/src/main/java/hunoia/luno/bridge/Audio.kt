package hunoia.luno.bridge

import hunoia.luno.bridge.feedback.showToast
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import hunoia.luno.R

fun Context.volumeUp() {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
}

fun Context.volumeDown() {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
}

fun Context.toggleMute() {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!notificationManager.isNotificationPolicyAccessGranted) {
        showToast(R.string.goto_grant_notification_policy_access_permission)
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        return
    }
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val ringerMode = audioManager.ringerMode
    val newRingerMode = when (ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
        else -> AudioManager.RINGER_MODE_SILENT
    }
    audioManager.ringerMode = newRingerMode
}

fun Context.dispatchMediaKeyEvent(keycode: Int) {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val down = KeyEvent(KeyEvent.ACTION_DOWN, keycode)
    audioManager.dispatchMediaKeyEvent(down)
    val up = KeyEvent(KeyEvent.ACTION_UP, keycode)
    audioManager.dispatchMediaKeyEvent(up)
}
