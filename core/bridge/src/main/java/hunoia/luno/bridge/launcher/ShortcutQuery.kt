package hunoia.luno.bridge.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.net.Uri
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.bridge.BuildConfig
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import org.xmlpull.v1.XmlPullParser
import java.util.UUID

object ShortcutQuery {

    private const val TAG = "ShortcutQuery"
    private const val XMLNS_ANDROID = "http://schemas.android.com/apk/res/android"

    /**
     * 按包名 + shortcutId 重新读取系统里的静态快捷方式定义。
     *
     * 落库的快捷方式可能来自旧版本应用（或其 shortcuts.xml 后来补了 action/data），
     * 只信任落库 intent 会在点击时落入「参数无效」。启动前用当前安装包的定义刷新一次。
     */
    fun findShortcut(context: Context, packageName: String, shortcutId: String): LauncherInfo.ShortcutInfo? {
        if (packageName.isBlank() || shortcutId.isBlank()) return null
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
            val activities = context.packageManager.queryIntentActivitiesCompat(
                intent,
                PackageManager.GET_META_DATA,
            )
            for (resolveInfo in activities) {
                val activityInfo = resolveInfo.activityInfo ?: continue
                val shortcutsMetadata = activityInfo.loadXmlMetaData(
                    context.packageManager,
                    "android.app.shortcuts",
                ) ?: continue
                shortcutsMetadata.use { parser ->
                    val found = parseShortcuts(context, activityInfo, parser)
                        .firstOrNull { it.shortcutId == shortcutId }
                    if (found != null) return found
                }
            }
            null
        }.getOrNull()
    }

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
                            intents = currentIntents,
                            shortcutId = currentId,
                        )
                        result.add(info)
                    }
                    currentId = null
                    currentLabel = null
                    currentIconRes = 0
                    currentIntents = arrayListOf()
                }
                eventType == XmlPullParser.END_TAG && parser.name == "intent" && currentIntent != null -> {
                    currentIntents.add(currentIntent!!.toUri(Intent.URI_INTENT_SCHEME))
                }
                eventType != XmlPullParser.START_TAG -> {}
                parser.name == "shortcut" && parser.getAttributeBooleanValue(XMLNS_ANDROID, "enabled", true) -> {
                    currentId = parser.attr("shortcutId") ?: UUID.randomUUID().toString()
                    var labelRes = parser.attrResource("shortcutShortLabel")
                    if (labelRes == 0) labelRes = parser.attrResource("shortcutLongLabel")
                    currentLabel = if (labelRes == 0) null else {
                        val resources = context.packageManager.getResourcesForApplication(actInfo.packageName)
                        resources.getString(labelRes)
                    }
                    currentIconRes = parser.attrResource("icon")
                }
                parser.name == "intent" && currentId != null -> {
                    try {
                        val shortcutIntent = Intent().apply {
                            action = parser.attr("action")
                            val pkg = parser.attr("targetPackage")
                            val rawClass = parser.attr("targetClass")
                                ?: parser.attr("targetActivity")
                            if (pkg != null && rawClass != null) {
                                setClassName(pkg, normalizeShortcutClassName(pkg, rawClass))
                            } else if (pkg != null) {
                                // 部分快捷方式只声明 targetPackage + data（隐式 URI），
                                // 之前丢掉 package 后可能被其他应用接走或无法启动。
                                setPackage(pkg)
                            } else if (rawClass != null) {
                                val classPackage = rawClass.substringBeforeLast('.', "")
                                if (classPackage.isNotEmpty()) {
                                    setClassName(classPackage, normalizeShortcutClassName(classPackage, rawClass))
                                }
                            }
                            currentLabel = currentLabel ?: rawClass?.substringAfterLast('.')
                            // Android 的 setType(null) 会清掉 data：两者必须一起设置，
                            // 否则只有 data、没有 mimeType 的快捷方式会退化成 component-only，
                            // 点击时落到错误的默认入口（或报“参数无效”）。
                            val dataUri = parser.attr("data")?.let(Uri::parse)
                            val mimeType = parser.attr("mimeType")
                            if (dataUri != null && mimeType != null) {
                                setDataAndType(dataUri, mimeType)
                            } else if (dataUri != null) {
                                data = dataUri
                            } else if (mimeType != null) {
                                type = mimeType
                            }
                        }
                        currentIntent = shortcutIntent
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "ignore malformed shortcut XML", e)
                    }
                }
                parser.name == "extra" && currentIntent != null -> {
                    val name = parser.attr("name")
                    val value = parser.attr("value")
                    if (name != null && value != null) currentIntent.putExtra(name, value)
                }
                parser.name == "categories" && currentIntent != null -> {
                    val name = parser.attr("name")
                    if (name != null) currentIntent.addCategory(name)
                }
            }
            eventType = parser.next()
        }
        return result
    }

    /**
     * 优先按 android 命名空间取；部分 ROM / 应用会把快捷方式属性写成无命名空间，
     * 最后再按属性名遍历兜底，避免 action/data 被静默丢掉（表现为「参数无效」）。
     */
    private fun XmlResourceParser.attr(attribute: String): String? {
        getAttributeValue(XMLNS_ANDROID, attribute)?.let { return it }
        getAttributeValue(null, attribute)?.let { return it }
        for (i in 0 until attributeCount) {
            if (getAttributeName(i) == attribute) return getAttributeValue(i)
        }
        return null
    }

    private fun XmlResourceParser.attrResource(attribute: String): Int {
        val res = getAttributeResourceValue(XMLNS_ANDROID, attribute, 0)
        if (res != 0) return res
        getAttributeResourceValue(null, attribute, 0).takeIf { it != 0 }?.let { return it }
        for (i in 0 until attributeCount) {
            if (getAttributeName(i) == attribute) return getAttributeResourceValue(i, 0)
        }
        return 0
    }
}

/** shortcut XML 的 targetClass 可能是 `.MainActivity` / 短类名；补全后才能解析。 */
internal fun normalizeShortcutClassName(packageName: String, raw: String): String = when {
    raw.startsWith(".") -> packageName + raw
    !raw.contains('.') -> "$packageName.$raw"
    else -> raw
}
