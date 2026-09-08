package hunoia.luno.runtime.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreviousAppTracker(
    private val packageManager: android.content.pm.PackageManager,
    private val startActivity: (Intent) -> Unit,
    private val rootInActiveWindowPackageName: () -> String?,
    private val excludePackageNames: () -> List<String>,
) {
    private var prevPackageName: String? = null
    private var currPackageName: String? = null
    private val launchablePackageCache = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 256
    }
    private val activityExistsCache = object : LinkedHashMap<String, Boolean>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 512
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()
                val className = event.className?.toString()

                isActivity(packageName, className)
                val prevAppExcludePkgNames = excludePackageNames()
                if (packageName !in prevAppExcludePkgNames &&
                    hasLaunchIntent(packageName) &&
                    currPackageName != packageName
                ) {
                    prevPackageName = currPackageName
                    currPackageName = packageName
                    if (prevPackageName == null) {
                        prevPackageName = currPackageName
                    }
                }
            }
            else -> Unit
        }
    }

    suspend fun previousApp(): Boolean {
        val prevPkgName = prevPackageName
        val curPkgName = currPackageName
        if (prevPkgName.isNullOrEmpty() || curPkgName.isNullOrEmpty()) {
            return false
        }
        if (currPackageNameError()) {
            queryLaunchIntentAndStart(curPkgName)
            return true
        }
        if (prevPkgName == curPkgName) return false
        if (queryLaunchIntentAndStart(prevPkgName)) {
            prevPackageName = curPkgName
            currPackageName = prevPkgName
            return true
        }
        return false
    }

    fun onRelease() {
        launchablePackageCache.clear()
        activityExistsCache.clear()
    }

    private suspend fun queryLaunchIntentAndStart(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val intent = withContext(Dispatchers.IO) {
            packageManager.getLaunchIntentForPackage(packageName)
        } ?: return false
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (ignored: Exception) {
            false
        }
    }

    private fun currPackageNameError(): Boolean {
        val pkgName = rootInActiveWindowPackageName()
        return pkgName != currPackageName
    }

    private fun hasLaunchIntent(packageName: String?): Boolean {
        val key = packageName ?: return false
        launchablePackageCache[key]?.let { return it }
        val result = packageManager.getLaunchIntentForPackage(key) != null
        launchablePackageCache[key] = result
        return result
    }

    private fun isActivity(packageName: String?, className: String?): Boolean {
        packageName ?: return false
        className ?: return false
        val key = "$packageName/$className"
        activityExistsCache[key]?.let { return it }
        return try {
            val component = ComponentName(packageName, className)
            packageManager.getActivityInfo(component, 0)
            cacheActivityExists(key, true)
            true
        } catch (e: Exception) {
            cacheActivityExists(key, false)
            false
        }
    }

    private fun cacheActivityExists(key: String, value: Boolean) {
        activityExistsCache[key] = value
    }
}
