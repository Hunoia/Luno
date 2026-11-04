package hunoia.luno.domain.system

import android.content.Context
import hunoia.luno.bridge.system.NotificationAccess
import hunoia.luno.bridge.system.PersistentNotificationShell
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.HiddenNotificationTarget
import hunoia.luno.config.model.PersistentNotificationHidingSettings
import hunoia.luno.core.domain.PersistentNotificationEntry
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 固定通知隐藏的页面门面：读配置 + 通过 Shizuku 读通知栏快照 / 立即 snooze。
 *
 * 监听器仍隐藏的主要执行者；这里只保证设置页的开关与列表能即时反映，
 * 并在监听器未连接时用 shell 兜底。
 */
object PersistentNotificationHidingUseCase {

    data class PageState(
        val settings: PersistentNotificationHidingSettings = PersistentNotificationHidingSettings(),
        val notifications: List<PersistentNotificationEntry> = emptyList(),
        val notificationAccessGranted: Boolean = false,
        val shizukuReady: Boolean = false,
    )

    suspend fun load(context: Context): PageState {
        val settings = readSettings()
        val shizukuReady = ShizukuManager.currentStatus().isReady
        val active = if (shizukuReady) {
            runCatching { PersistentNotificationShell.listPersistent() }
                .getOrDefault(emptyList())
                .filter { it.packageName != context.packageName }
        } else {
            emptyList()
        }
        // 已隐藏的条目会被 snooze 移出通知栏；仍要保留在列表里，否则用户无法取消勾选。
        val hiddenOnly = settings.hiddenTargets
            .filter { it.packageName != context.packageName }
            .filterNot { target -> active.any { entry -> entry.matches(target) } }
            .map { it.toEntry() }
        return PageState(
            settings = settings,
            notifications = active + hiddenOnly,
            notificationAccessGranted = NotificationAccess.isGranted(context),
            shizukuReady = shizukuReady,
        )
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        val before = readSettings()
        update { current ->
            current.copy(
                enabled = enabled,
            )
        }
        if (enabled) {
            withContext(Dispatchers.IO) {
                if (before.hideLunoNotifications) {
                    PersistentNotificationShell.snoozeAllOwnPersistent(context.packageName)
                }
                before.hiddenTargets.forEach { PersistentNotificationShell.snoozeTarget(it) }
            }
        } else {
            withContext(Dispatchers.IO) {
                if (before.hideLunoNotifications) {
                    PersistentNotificationShell.unsnoozeOwn(context.packageName)
                }
                before.hiddenTargets.forEach { PersistentNotificationShell.unsnoozeTarget(it) }
            }
        }
    }

    suspend fun setLunoHidden(context: Context, hidden: Boolean) {
        val enabled = readSettings().enabled
        update { current ->
            current.copy(
                hideLunoNotifications = hidden,
            )
        }
        withContext(Dispatchers.IO) {
            if (hidden) {
                if (enabled) {
                    PersistentNotificationShell.snoozeAllOwnPersistent(context.packageName)
                }
            } else {
                PersistentNotificationShell.unsnoozeOwn(context.packageName)
            }
        }
    }

    suspend fun setTargetHidden(
        context: Context,
        notification: PersistentNotificationEntry,
        hidden: Boolean,
    ) {
        val target = notification.toTarget()
        val enabled = readSettings().enabled
        // 在 updateData 内基于最新配置增删，避免连续快速切换时后一次读到旧快照覆盖前一次。
        update { current ->
            val targets = if (hidden) {
                current.hiddenTargets.filterNot { it.sameNotificationAs(target) } + target
            } else {
                current.hiddenTargets.filterNot { it.sameNotificationAs(target) }
            }
            current.copy(hiddenTargets = targets)
        }
        withContext(Dispatchers.IO) {
            if (hidden) {
                // 列表里的目标可能已被 snooze 移出通知栏（key 为空）：按 package/id/tag 再找一次。
                if (enabled) {
                    if (notification.key.isNotBlank()) {
                        PersistentNotificationShell.snooze(notification.key)
                    }
                    PersistentNotificationShell.snoozeTarget(target)
                }
            } else {
                PersistentNotificationShell.unsnooze(notification.key)
                PersistentNotificationShell.unsnoozeTarget(target)
            }
        }
    }

    private suspend fun readSettings(): PersistentNotificationHidingSettings =
        ConfigProvider.getAdvancedSettings().persistentNotificationHiding

    private suspend fun update(
        transform: (PersistentNotificationHidingSettings) -> PersistentNotificationHidingSettings,
    ) {
        ConfigProvider.updateAdvancedSettings { current ->
            current.copy(persistentNotificationHiding = transform(current.persistentNotificationHiding))
        }
    }

    private fun PersistentNotificationEntry.toTarget(): HiddenNotificationTarget =
        HiddenNotificationTarget(
            packageName = packageName,
            notificationId = notificationId,
            tag = tag,
            channelId = channelId,
        )

    private fun HiddenNotificationTarget.toEntry(): PersistentNotificationEntry =
        PersistentNotificationEntry(
            key = "",
            packageName = packageName,
            notificationId = notificationId,
            tag = tag,
            channelId = channelId,
            title = "",
            text = "",
        )

    private fun PersistentNotificationEntry.matches(target: HiddenNotificationTarget): Boolean =
        packageName == target.packageName &&
            notificationId == target.notificationId &&
            tag == target.tag

    private fun HiddenNotificationTarget.sameNotificationAs(other: HiddenNotificationTarget): Boolean =
        packageName == other.packageName &&
            notificationId == other.notificationId &&
            tag == other.tag
}
