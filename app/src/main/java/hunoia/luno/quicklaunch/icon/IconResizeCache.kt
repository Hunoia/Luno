package hunoia.luno.quicklaunch.icon

import android.graphics.drawable.Drawable
import java.util.Collections
import java.util.LinkedHashMap

object IconResizeCache {

    private const val MAX_ICON_CACHE_SIZE = 200

    val iconCache: MutableMap<String, Drawable> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Drawable>(MAX_ICON_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean = size > MAX_ICON_CACHE_SIZE
        }
    )
}
