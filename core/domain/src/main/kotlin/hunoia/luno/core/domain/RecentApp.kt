package hunoia.luno.core.domain

/**
 * 最近应用条目：同包名的主应用与分身（XSpace / 工作资料）靠 [userId] 区分。
 *
 * a11y 事件只给包名，userId 由运行时在应用成为前台后解析并回填；
 * 解析失败 / 旧数据按主用户（0）处理。
 */
data class RecentApp(
    val packageName: String,
    val userId: Int = 0,
) {
    /** 黑名单持久化键：主用户保持裸包名（兼容旧配置），分身追加 `#userId`。 */
    val blockKey: String get() = blockKey(packageName, userId)

    companion object {
        fun blockKey(packageName: String, userId: Int): String =
            if (userId == 0) packageName else "$packageName#$userId"

        /** 解析持久化键；无 `#userId` 后缀或后缀非法时按主应用处理。 */
        fun fromBlockKey(key: String): RecentApp {
            val index = key.lastIndexOf('#')
            if (index <= 0 || index == key.lastIndex) return RecentApp(key)
            val userId = key.substring(index + 1).toIntOrNull() ?: return RecentApp(key)
            return RecentApp(key.substring(0, index), userId)
        }
    }
}
