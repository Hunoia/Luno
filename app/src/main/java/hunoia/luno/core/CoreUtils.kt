package hunoia.luno.core

import android.content.Context
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    val Image: String by lazy {
        val ctx = AppContext.get()
        "${ctx.getExternalFilesDir(null)?.absolutePath ?: ctx.filesDir.absolutePath}/Pictures"
    }
}

@Keep
object JsonSerializer {

    val globalJson = Json {
        ignoreUnknownKeys = true
    }

    inline fun <reified T> encodeToString(value: T): String {
        return globalJson.encodeToString(value)
    }

    inline fun <reified T> decodeFromString(string: String): T {
        return globalJson.decodeFromString(string)
    }
}
