package hunoia.luno.bridge.system

import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.content.ActivityNotFoundException

import hunoia.luno.R

fun Context.gotoAppDetailSettings() {
    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.also { startActivity(it) }
}

fun Context.launchAssist(): Boolean {
    return try {
        val intent = Intent().apply {
            setAction(Intent.ACTION_ASSIST)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        true
    } catch (ignored: ActivityNotFoundException) {
        showToast(R.string.launch_assist_failed)
        false
    }
}

fun Context.gotoAccessibilitySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    try {
        startActivity(intent)
    } catch (ignored: ActivityNotFoundException) {
        intent.action = Settings.ACTION_SETTINGS
        startActivity(intent)
    }
}

fun Context.launchUrl(url: String): Boolean {
    return try {
        val normalizedUrl = normalizeOpenAppOrUrl(url) ?: run {
            showToast(R.string.invalid_url)
            return false
        }
        val intent = buildViewIntent(normalizedUrl)
        if (!packageManager.hasViewActivity(intent)) {
            showToast(R.string.launch_no_handler)
            return false
        }
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        showToast(R.string.launch_failed)
        false
    }
}

fun buildViewIntent(normalizedUrl: String): Intent {
    val intent = when {
        normalizedUrl.startsWith("intent:") -> Intent.parseUri(normalizedUrl, Intent.URI_INTENT_SCHEME)
        normalizedUrl.startsWith("android-app:") -> Intent.parseUri(normalizedUrl, Intent.URI_ANDROID_APP_SCHEME)
        else -> Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl))
    }
    if (intent.action.isNullOrEmpty()) {
        intent.action = Intent.ACTION_VIEW
    }
    val scheme = intent.data?.scheme
    if ((scheme == "http" || scheme == "https") && !intent.hasCategory(Intent.CATEGORY_BROWSABLE)) {
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return intent
}

fun PackageManager.hasViewActivity(intent: Intent): Boolean {
    return queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL).isNotEmpty()
}

fun normalizeOpenAppOrUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    if (trimmed.startsWith("intent:")) {
        return runCatching {
            Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
            trimmed
        }.getOrNull()
    }

    val candidate = withUrlScheme(trimmed)
    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    return if (uri.scheme.isNullOrBlank()) null else candidate
}

/**
 * 补 scheme：裸域名（example.com）补 https://，已有 scheme 的原样返回。
 * 纯函数（不碰 Uri），便于 JVM 单测；[normalizeOpenAppOrUrl] 在它之上再做 Uri 校验。
 */
fun withUrlScheme(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return trimmed
    val hasExplicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed)
    return if (hasExplicitScheme || trimmed.contains("://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
}
