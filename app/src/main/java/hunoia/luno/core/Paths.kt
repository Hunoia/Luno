package hunoia.luno.core


object Paths {

    val AppData: String by lazy {
        val ctx = AppContext.get()
        ctx.getExternalFilesDir(null)?.absolutePath ?: ctx.filesDir.absolutePath
    }

    val AppCache: String by lazy {
        val ctx = AppContext.get()
        ctx.externalCacheDir?.absolutePath ?: ctx.cacheDir.absolutePath
    }

    val Image: String by lazy {
        val ctx = AppContext.get()
        "${ctx.getExternalFilesDir(null)?.absolutePath ?: ctx.filesDir.absolutePath}/Pictures"
    }
}
