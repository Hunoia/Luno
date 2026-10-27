package hunoia.luno.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope

object AppContext {
    private var appContext: Context? = null
    var applicationScope: CoroutineScope? = null
        private set

    fun init(context: Context, scope: CoroutineScope) {
        appContext = context
        applicationScope = scope
    }

    fun get(): Context = appContext ?: throw IllegalStateException("AppContext not initialized")
}

object Paths {

    val AppCache: String by lazy {
        val ctx = AppContext.get()
        ctx.externalCacheDir?.absolutePath ?: ctx.cacheDir.absolutePath
    }

    /** 快捷方式位图图标（ShortcutInfo.iconPath 指向这里）。 */
    val ShortcutIcons: String by lazy {
        "${AppContext.get().filesDir.absolutePath}/shortcut_icons"
    }
}
