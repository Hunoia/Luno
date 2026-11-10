package hunoia.luno.bridge.system

import hunoia.luno.config.model.HiddenNotificationTarget
import hunoia.luno.config.model.UserFacingReminderChannelIds
import hunoia.luno.core.domain.PersistentNotificationEntry
import hunoia.luno.shizuku.ShizukuManager

/**
 * 通知栏常驻通知的 Shizuku 读 / 写口。
 *
 * 只做 shell 封装与输出解析，不持有状态；监听器可用 `NotificationListenerService` 时优先走监听器。
 * `cmd notification get` / `dumpsys notification` 的输出属于 ROM 细节，解析失败一律返回空，
 * 由调用方降级（设置页仍保留 Luno 自身分组）。
 */
object PersistentNotificationShell {

    /** 续 snooze 的时长：参考实现按一年续期，系统可能把时长夹到更短，启动/重投时会再续。 */
    const val SNOOZE_DURATION_MS = 365L * 24L * 60L * 60L * 1000L

    private const val LIST_MAX_CHARS = 64 * 1024
    private const val GET_MAX_CHARS = 512 * 1024
    private const val DUMP_MAX_CHARS = 2_000_000

    suspend fun listPersistent(): List<PersistentNotificationEntry> {
        if (!ShizukuManager.currentStatus().isReady) return emptyList()
        val list = ShizukuManager.executeShell("cmd notification list", LIST_MAX_CHARS)
        if (list.stdout.isBlank()) return emptyList()
        val keys = list.stdout.lineSequence()
            .map { it.trim() }
            .filter { key -> key.count { it == '|' } >= 4 }
            .toList()
        if (keys.isEmpty()) return emptyList()

        val script = keys.joinToString("\n") { key ->
            "cmd notification get ${shellQuote(key)}"
        }
        val output = ShizukuManager.executeShell(script, GET_MAX_CHARS)
        return parseRecords(output.stdout)
    }

    suspend fun snooze(key: String, durationMs: Long = SNOOZE_DURATION_MS): Boolean {
        if (key.isBlank()) return false
        val result = ShizukuManager.executeShell(
            "cmd notification snooze --for $durationMs ${shellQuote(key)}",
        )
        return result.isSuccess
    }

    suspend fun unsnooze(key: String): Boolean {
        if (key.isBlank()) return false
        val result = ShizukuManager.executeShell("cmd notification unsnooze ${shellQuote(key)}")
        return result.isSuccess
    }

    /** 只续 Luno 自己指定 id 的通知；用于前台服务启动后的快速兜底。 */
    suspend fun snoozeOwn(
        packageName: String,
        notificationId: Int,
        durationMs: Long = SNOOZE_DURATION_MS,
    ): Int {
        if (!ShizukuManager.currentStatus().isReady) return 0
        val list = ShizukuManager.executeShell("cmd notification list", LIST_MAX_CHARS)
        if (list.stdout.isBlank()) return 0
        val needle = "|$packageName|$notificationId|"
        val keys = list.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.contains(needle) }
            .toList()
        keys.forEach { snooze(it, durationMs) }
        return keys.size
    }

    /** 隐藏 Luno 当前所有常驻通知（用户开关用，避免误伤将来新增的普通通知）。 */
    suspend fun snoozeAllOwnPersistent(packageName: String, durationMs: Long = SNOOZE_DURATION_MS): Int {
        val keys = listPersistent()
            .filter { it.packageName == packageName }
            // 习惯 / 待办提醒是面向用户的常驻通知，用户显式开启的提醒不能因「隐藏 Luno 通知」被收走。
            .filterNot { it.channelId in UserFacingReminderChannelIds }
            .map { it.key }
        keys.forEach { snooze(it, durationMs) }
        return keys.size
    }

    suspend fun unsnoozeOwn(packageName: String): Int {
        val keys = findSnoozedKeys().filter { key -> key.split('|').getOrNull(1) == packageName }
        keys.forEach { unsnooze(it) }
        return keys.size
    }

    suspend fun unsnoozeTarget(target: HiddenNotificationTarget): Int {
        val keys = findSnoozedKeys().filter { key -> key.matches(target) }
        keys.forEach { unsnooze(it) }
        return keys.size
    }

    /** 对当前仍在通知栏的目标做一次即时隐藏；用于重新启用开关。 */
    suspend fun snoozeTarget(target: HiddenNotificationTarget): Int {
        val keys = listPersistent()
            .filter {
                it.packageName == target.packageName &&
                    it.notificationId == target.notificationId &&
                    it.tag == target.tag
            }
            .map { it.key }
        keys.forEach { snooze(it) }
        return keys.size
    }

    /** 从 dumpsys 的 snoozed 区段找 key；找不到就返回空，不做二次探测。 */
    suspend fun findSnoozedKeys(): List<String> {
        if (!ShizukuManager.currentStatus().isReady) return emptyList()
        val dump = ShizukuManager.executeShell("dumpsys notification --noredact", DUMP_MAX_CHARS)
        if (dump.stdout.isBlank()) return emptyList()
        val keys = mutableListOf<String>()
        var collecting = false
        for (line in dump.stdout.lineSequence()) {
            if (line.contains("Snoozed notifications:") ||
                line.contains("Pending snoozed notifications")
            ) {
                collecting = true
                continue
            }
            if (!collecting) continue
            val trimmed = line.trim()
            if (trimmed.startsWith("key: ")) {
                keys += trimmed.removePrefix("key: ")
                    .substringBefore(" until:")
                    .trim()
            } else if (trimmed.isNotEmpty() && !line.startsWith(" ")) {
                collecting = false
            }
        }
        return keys.distinct()
    }

    private fun String.matches(target: HiddenNotificationTarget): Boolean {
        val parts = split('|')
        if (parts.size < 5) return false
        return parts[1] == target.packageName &&
            parts[2] == target.notificationId.toString() &&
            parts[3] == (target.tag ?: "null")
    }

    private fun parseRecords(output: String): List<PersistentNotificationEntry> {
        if (output.isBlank()) return emptyList()
        val chunks = output.split(Regex("(?m)^NotificationRecord\\("))
        return chunks.drop(1).mapNotNull { chunk ->
            val block = "NotificationRecord($chunk"
            val header = block.lineSequence().firstOrNull { it.startsWith("NotificationRecord(") }
                ?: return@mapNotNull null
            val headerMatch = RECORD_HEADER.find(header) ?: return@mapNotNull null
            val packageName = headerMatch.groupValues[1]
            val notificationId = headerMatch.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val tag = headerMatch.groupValues[3].takeUnless { it == "null" }
            val key = headerMatch.groupValues[4]
            val channelId = CHANNEL.find(header)?.groupValues?.getOrNull(1)
            val flags = block.lineSequence()
                .firstOrNull { it.trimStart().startsWith("flags=") }
                ?.substringAfter("flags=")
                .orEmpty()
            val persistent = PERSISTENT_FLAGS.any { flags.contains(it) }
            if (!persistent) return@mapNotNull null
            PersistentNotificationEntry(
                key = key,
                packageName = packageName,
                notificationId = notificationId,
                tag = tag,
                channelId = channelId,
                title = extraValue(block, "android.title"),
                text = extraValue(block, "android.text")
                    .ifBlank { extraValue(block, "android.bigText") },
            )
        }
    }

    private fun extraValue(block: String, key: String): String {
        val line = block.lineSequence()
            .firstOrNull { it.trimStart().startsWith("$key=") }
            ?: return ""
        val raw = line.substringAfter('=').trim()
        val value = raw.substringAfter('(', raw).removeSuffix(")").trim()
        return value.takeUnless { it == "null" }.orEmpty()
    }


    private val RECORD_HEADER = Regex(
        "^NotificationRecord\\(0x[0-9a-fA-F]+: pkg=(\\S+) user=\\S+ id=(-?\\d+) tag=(\\S+) importance=\\d+ key=(\\S+)",
    )
    private val CHANNEL = Regex("channel=([^ )]+)")
    private val PERSISTENT_FLAGS = listOf("ONGOING_EVENT", "FOREGROUND_SERVICE", "NO_CLEAR")
}
