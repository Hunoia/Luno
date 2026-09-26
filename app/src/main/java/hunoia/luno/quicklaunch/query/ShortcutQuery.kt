package hunoia.luno.quicklaunch.query

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.net.Uri
import android.util.Log
import hunoia.luno.BuildConfig
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.bridge.queryIntentActivitiesCompat
import org.xmlpull.v1.XmlPullParser
import java.util.UUID

object ShortcutQuery {

    private const val TAG = "ShortcutQuery"
    private const val XMLNS_ANDROID = "http://schemas.android.com/apk/res/android"

    fun getAllAppsWithShortcut(context: Context): List<LauncherInfo> {
        val result = arrayListOf<LauncherInfo>()
        val intent2 = Intent(Intent.ACTION_MAIN)
        intent2.addCategory(Intent.CATEGORY_LAUNCHER)
        for (resolveInfo in context.packageManager.queryIntentActivitiesCompat(intent2, PackageManager.GET_META_DATA)) {
            val activityInfo = resolveInfo.activityInfo
            val shortcutsMetadata = activityInfo.loadXmlMetaData(context.packageManager, "android.app.shortcuts")
                ?: continue
            shortcutsMetadata.use { parser ->
                val shortcuts = parseShortcuts(context, activityInfo, parser)
                if (shortcuts.isNotEmpty()) {
                    val info = LauncherInfo(
                        packageName = activityInfo?.packageName ?: "",
                        className = activityInfo?.name ?: "",
                        label = activityInfo.loadLabel(context.packageManager).toString(),
                        shortcuts = shortcuts
                    )
                    result.add(info)
                }
            }
        }
        return result
    }

    private fun parseShortcuts(context: Context, actInfo: ActivityInfo, parser: XmlResourceParser): List<LauncherInfo.ShortcutInfo> {
        val result = arrayListOf<LauncherInfo.ShortcutInfo>()
        var eventType = parser.eventType
        var currentId: String? = null
        var currentLabel: String? = null
        var currentIconRes = 0
        var currentIntent: Intent? = null
        var currentIntents = arrayListOf<String>()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when {
                eventType == XmlPullParser.END_TAG && parser.name == "shortcut" -> {
                    if (currentId != null && currentLabel != null && currentIntents.isNotEmpty()) {
                        val info = LauncherInfo.ShortcutInfo(
                            packageName = actInfo.packageName,
                            className = actInfo.name,
                            label = currentLabel,
                            iconRes = currentIconRes,
                            intents = currentIntents
                        )
                        result.add(info)
                    }
                    currentId = null
                    currentLabel = null
                    currentIconRes = 0
                    currentIntents = arrayListOf()
                }
                eventType == XmlPullParser.END_TAG && parser.name == "intent" && currentIntent != null -> {
                    currentIntents.add(currentIntent.toUri(Intent.URI_INTENT_SCHEME))
                }
                eventType != XmlPullParser.START_TAG -> {}
                parser.name == "shortcut" && parser.getAttributeBooleanValue(XMLNS_ANDROID, "enabled", true) -> {
                    currentId = parser.getAttributeValue("shortcutId") ?: UUID.randomUUID().toString()
                    var labelRes = parser.getAttributeResourceValue("shortcutShortLabel")
                    if (labelRes == 0) labelRes = parser.getAttributeResourceValue("shortcutLongLabel")
                    currentLabel = if (labelRes == 0) null else {
                        val resources = context.packageManager.getResourcesForApplication(actInfo.packageName)
                        resources.getString(labelRes)
                    }
                    currentIconRes = parser.getAttributeResourceValue("icon")
                }
                parser.name == "intent" && currentId != null -> {
                    try {
                        val shortcutIntent = Intent().apply {
                            action = parser.getAttributeValue("action")
                            val pkg = parser.getAttributeValue("targetPackage")
                            val cls = parser.getAttributeValue("targetClass")
                                ?: parser.getAttributeValue("targetActivity")?.replace('$', '_')
                            if (pkg != null && cls != null) setClassName(pkg, cls)
                            currentLabel = currentLabel ?: cls?.substringAfterLast('.')
                            data = parser.getAttributeValue("data")?.let(Uri::parse)
                        }
                        currentIntent = shortcutIntent
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "ignore malformed shortcut XML", e)
                    }
                }
                parser.name == "extra" && currentIntent != null -> {
                    val name = parser.getAttributeValue("name")
                    val value = parser.getAttributeValue("value")
                    if (name != null && value != null) currentIntent.putExtra(name, value)
                }
                parser.name == "categories" && currentIntent != null -> {
                    val name = parser.getAttributeValue("name")
                    if (name != null) currentIntent.addCategory(name)
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun XmlResourceParser.getAttributeValue(attribute: String): String? {
        return getAttributeValue(XMLNS_ANDROID, attribute) ?: getAttributeValue(null, attribute)
    }

    private fun XmlResourceParser.getAttributeResourceValue(attribute: String): Int {
        val res = getAttributeResourceValue(XMLNS_ANDROID, attribute, 0)
        if (res != 0) return res
        return getAttributeResourceValue(null, attribute, 0)
    }
}
