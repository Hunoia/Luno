package hunoia.luno.capability.notify

import android.app.NotificationManager
import android.content.Context
import hunoia.luno.core.LunoLog
import hunoia.luno.core.domain.IslandCapability
import hunoia.luno.core.domain.IslandNotificationSpec
import hunoia.luno.core.domain.IslandPublishResult
import hunoia.luno.core.domain.NotificationPresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [NotificationPresenter] 默认实现（v1：只有 ISLAND 发布路径；LIVE/STANDARD 及其回退链
 * 由调用方按 [IslandPublishResult] 执行——内容 / 通道归属调用方，见 capability/notify/README.md）。
 *
 * 投递事务串行化：`publishIsland` / `prewarmIslandAuth` 经 [mutex] 串行化同一实例的调用，
 * 跨实例（habit / backup / 设置页各持一个 presenter）由 `IslandPublisher` 的进程锁兜底。
 */
class DefaultNotificationPresenter(
    context: Context,
    validationWindowMs: Long = 220L,
) : NotificationPresenter {

    private val appContext = context.applicationContext
    private val probe = IslandProbe(appContext)
    private val gate = XmsfGate(probe)
    private val publisher = IslandPublisher(appContext, gate, validationWindowMs)
    private val mutex = Mutex()

    override fun probeIslandCapability(): IslandCapability = probe.capability

    /**
     * 只有 importance 这一个可信信号：HyperOS 4.0 的 `NotificationChannel` 上**没有**
     * `canShowFloat()`（实测 101 个字段全是 AOSP 的，无 float 相关方法/字段），
     * 部分 ROM（LinkGo 面向的版本）才有这个方法。反射必须单独 try：把「方法不存在」
     * 和「方法返回 false」混在一个 runCatching 里会静默降级成 false（踩过）。
     * 渠道真被关掉悬浮通知时 importance 仍是 HIGH，所以这个信号只是**上界**：
     * 调用方只当日志诊断用，别拿它否决用户设置。
     */
    override fun canChannelFloat(channelId: String): Boolean {
        val channel = runCatching {
            appContext.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channelId)
        }.getOrNull() ?: return false
        val reflected = runCatching {
            channel.javaClass.getMethod("canShowFloat").invoke(channel) as? Boolean
        }.getOrNull()
        return reflected ?: (channel.importance >= NotificationManager.IMPORTANCE_HIGH)
    }

    override suspend fun publishIsland(spec: IslandNotificationSpec): IslandPublishResult =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                capabilityFailure()?.let { return@withContext it }
                runCatching {
                    when (publisher.postWithValidation(spec)) {
                        IslandPublisher.Result.Posted -> {
                            LunoLog.i(
                                LOG_TAG,
                                "island posted: id=${spec.notificationId} actions=${spec.actions.size} " +
                                    "expand=${spec.autoExpand}/${spec.expandedSeconds}s shade=${spec.showInShade}",
                            )
                            IslandPublishResult.Posted
                        }

                        IslandPublisher.Result.Rejected -> {
                            LunoLog.w(LOG_TAG, "island rejected within validation window: id=${spec.notificationId}")
                            IslandPublishResult.Rejected
                        }

                        IslandPublisher.Result.BypassUnavailable -> {
                            LunoLog.w(LOG_TAG, "island bypass unavailable (shizuku not ready or cmd failed)")
                            IslandPublishResult.Unsupported("xmsf bypass unavailable")
                        }
                    }
                }.getOrElse {
                    if (it is CancellationException) throw it
                    LunoLog.w(LOG_TAG, "island publish failed: ${it.message}")
                    IslandPublishResult.Failed(it.message ?: "publish failed")
                }
            }
        }

    override suspend fun prewarmIslandAuth(channelId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (capabilityFailure() != null) return@withContext
                // 通道不存在时 notify 会被系统静默丢弃，预热无意义（通道归调用方创建）。
                if (!channelExists(channelId)) {
                    LunoLog.i(LOG_TAG, "island auth warmup skipped: channel=$channelId not created yet")
                    return@withContext
                }
                runCatching { publisher.warmup(warmupSpec(channelId)) }
                    .onSuccess { LunoLog.i(LOG_TAG, "island auth warmup done: channel=$channelId ok=$it") }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        LunoLog.w(LOG_TAG, "island auth warmup failed: ${error.message}")
                    }
            }
        }
    }

    /** 非 READY 时的统一失败因；READY 返回 null。 */
    private fun capabilityFailure(): IslandPublishResult.Unsupported? =
        when (probe.capability) {
            IslandCapability.READY -> null
            IslandCapability.OTHER_ANDROID,
            IslandCapability.FALLBACK,
            -> IslandPublishResult.Unsupported(
                "capability=${probe.capability} xmsf=${probe.xmsfPackage != null}",
            )
        }

    private fun channelExists(channelId: String): Boolean = runCatching {
        appContext.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(channelId) != null
    }.getOrDefault(false)

    /** 预热用最小 spec：不进通知栏、不展开、不常驻，投出去立刻撤掉。 */
    private fun warmupSpec(channelId: String): IslandNotificationSpec =
        IslandNotificationSpec(
            channelId = channelId,
            notificationId = WARMUP_NOTIFICATION_ID,
            title = "Luno",
            text = " ",
            capsuleText = "Luno",
            expandedSeconds = 0,
            showInShade = false,
            ongoing = false,
        )

    private companion object {
        const val LOG_TAG = "NotifyPresenter"

        /** 预热通知 id（投完即撤，不与提醒 id 冲突即可）。 */
        const val WARMUP_NOTIFICATION_ID = 2099
    }
}
