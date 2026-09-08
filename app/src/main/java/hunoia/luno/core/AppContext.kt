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
