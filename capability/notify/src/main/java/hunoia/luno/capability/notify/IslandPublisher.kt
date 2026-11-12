package hunoia.luno.capability.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import hunoia.luno.core.domain.IslandActionSpec
import hunoia.luno.core.domain.IslandIntentKind
import hunoia.luno.core.domain.IslandNotificationSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * FocusTemplate V3 payload 构建 + 焦点通知投递事务（切 XMSF → notify → 校验窗口 → 恢复）。
 *
 * payload 约束（见 capability/notify/README.md，全部在此强制，调用方无感）：
 * - `miui.focus.param` 总长 ≤ 3072 字节（超长抛 [IllegalArgumentException]）；
 * - 布尔字段必须是 JSON 布尔（protocol=3 严格反序列化，0/1 会解析失败——实测坑）；
 * - 文本经 HTML 转义；图片用 app 图标，不接外部位图（v1）。
 *
 * 结构参照 Xiaomi-SuperIsland-Playground / InstallerX-Revived（均已核对源码）。
 */
internal class IslandPublisher(
    private val appContext: Context,
    private val gate: XmsfGate,
    /** 校验窗口毫秒数；默认 220，调试可调（HyperOS 鉴权回包时序实测可变）。 */
    private val validationWindowMs: Long = VALIDATION_WINDOW_MS,
) {

    /**
     * 投递事务：block XMSF → notify → [VALIDATION_WINDOW_MS] 校验 → restore。
     * 全程阻塞，调用方保证不在主线程；[PROCESS_LOCK] 保证进程内（即使有多个 presenter 实例）
     * 同一时刻只有一个绕过事务，否则一个事务的 restore 会在另一个事务发布中途放开网络。
     * 校验窗口内通知被撤回 → [Result.Rejected]。
     */
    fun postWithValidation(spec: IslandNotificationSpec): Result = synchronized(PROCESS_LOCK) {
        val blockMs = resolveBlockWindowMs(spec)
        if (!gate.block()) return Result.BypassUnavailable
        var restored = false
        try {
            notificationManager().notify(spec.notificationId, buildNotification(spec))
            // 断网只服务 SystemUI 的鉴权：够它发起并失败就行，不必一直断着。
            Thread.sleep(blockMs)
            restoreOrThrow()
            restored = true
            // 恢复网络后再等一小段：鉴权回包与「撤回」都要在这段里落定，否则会把
            // 被撤回的岛误判成 Posted（或把正常岛误判成 Rejected）。
            Thread.sleep(AFTER_RESTORE_VALIDATION_MS)
            if (isActive(spec.notificationId)) Result.Posted else Result.Rejected
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } finally {
            if (!restored) restoreOrThrow()
        }
    }

    private fun resolveBlockWindowMs(spec: IslandNotificationSpec): Long =
        blockWindowMs(spec.blockWindowMs, validationWindowMs)

    private fun restoreOrThrow() {
        if (!gate.restore()) {
            throw IllegalStateException("xmsf gate restore failed; global net rule may be left blocked")
        }
    }

    /**
     * 鉴权预热事务：block → notify → 短睡 → cancel → restore（不走校验窗口）。
     *
     * 目的不是让用户看到通知，而是逼 SystemUI 跑完一次焦点鉴权并把结果缓住，
     * 之后真实提醒不必再等 XMSF 回包（首次投递常因鉴权慢而被当成撤回）。
     * 返回 false = 绕过不可用或发布失败（非致命，调用方忽略）。
     */
    fun warmup(spec: IslandNotificationSpec): Boolean = synchronized(PROCESS_LOCK) {
        transact {
            notificationManager().notify(spec.notificationId, buildNotification(spec))
            Thread.sleep(WARMUP_WINDOW_MS)
            notificationManager().cancel(spec.notificationId)
            true
        } == true
    }

    /**
     * 绕过事务骨架：block → [block] → restore；返回 null = 绕过不可用。
     *
     * restore 失败抛错（调用方映射为 Failed）：投递结果不可信，且不留全局断网继续发布。
     */
    private inline fun <T : Any> transact(block: () -> T): T? {
        if (!gate.block()) return null
        return try {
            block()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } finally {
            if (!gate.restore()) {
                throw IllegalStateException("xmsf gate restore failed; global net rule may be left blocked")
            }
        }
    }

    private fun notificationManager() =
        appContext.getSystemService(android.app.NotificationManager::class.java)!!

    private fun isActive(id: Int): Boolean = runCatching {
        notificationManager().activeNotifications.any { it.id == id }
    }.getOrDefault(false)

    internal fun buildNotification(spec: IslandNotificationSpec): Notification {
        val accent = spec.accentColor
        val accentHtml = accent?.let(::colorToHtml)
        val logoIcon = Icon.createWithResource(appContext, appIconRes())
        val logoKey = KEY_LOGO

        val paramV2 = JSONObject()
            .put("protocol", 1)
            .put("business", BUSINESS_NAME)
            .put("ticker", escapeHtml(spec.capsuleText.ifBlank { spec.title }))
            .put("tickerPic", logoKey)
            // 保持通知可更新：不可更新的焦点通知被 SystemUI 视为一次性，点击即收走。
            .put("updatable", true)
            .put("reopen", "close")
            // 浮出与首发展开是两个字段，但必须**同值**：SystemUI 用 enableFloat 决定是否建立
            // 大岛浮出区、用 islandFirstFloat 决定首帧是否直接展开。历史上我们把
            // enableFloat 恒写 true、只让 islandFirstFloat 跟随开关，结果是「开关拨了没反应」。
            // Playground / LinkGo / InstallerX 三份可用实现都写同一个值。
            .put("enableFloat", spec.autoExpand)
            .put("islandFirstFloat", spec.autoExpand)
            // 焦点通知默认可能不进通知栏；习惯提醒的正文与「完成」按钮还要在那里。
            .put("isShowNotification", spec.showInShade)
            .apply {
                if (spec.outerGlow) put("outEffectSrc", OUT_EFFECT_OUTER_GLOW)
                if (spec.timeoutSeconds > 0) {
                    // `timeout` 单位是分钟（islandTimeout 才是秒），统一按秒值换算。
                    put("timeout", islandTimeoutMinutes(spec.timeoutSeconds))
                }
                // 背景只认 backgroundColor：强调色（习惯色）只用于标题 / 高亮，不当背景。
                spec.backgroundColor?.let { bg ->
                    put(
                        "bgInfo",
                        JSONObject().put("type", 1).put("colorBg", colorToHtml(bg)),
                    )
                }
            }
            .put("iconTextInfo", iconTextInfo(spec, accentHtml))
            .put("param_island", islandPayload(spec, accentHtml))
            .apply {
                if (spec.actions.isNotEmpty()) {
                    put("textButton", actionsPayload(spec))
                }
            }

        val payload = JSONObject()
            .put("type", FOCUS_V3_SERIAL_NAME)
            .put("param_v2", paramV2)
            .toString()
        val bytes = payload.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FOCUS_PARAM_BYTES) {
            "focus payload too large: $bytes bytes (limit $MAX_FOCUS_PARAM_BYTES)"
        }

        val pics = android.os.Bundle().apply {
            putParcelable(logoKey, logoIcon)
        }

        return Notification.Builder(appContext, spec.channelId)
            .setSmallIcon(appIconRes())
            .setContentTitle(spec.title)
            .setContentText(spec.text.ifBlank { " " })
            .setContentIntent(contentIntent(spec))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(spec.ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setAutoCancel(!spec.ongoing)
            .apply {
                // 一次性事件在岛超时后把通知也收走，避免通知栏留一条不可点的旧失败。
                if (!spec.ongoing && spec.timeoutSeconds > 0) {
                    setTimeoutAfter(spec.timeoutSeconds * 1000L)
                }
            }
            .apply { accent?.let { setColor(it) } }
            .addExtras(
                android.os.Bundle().apply {
                    putString(FOCUS_PARAM, payload)
                    putBundle(FOCUS_PICS, pics)
                    actionsBundle(spec)?.let { putBundle(FOCUS_ACTIONS, it) }
                    putBoolean(ISLAND_UPDATE_NO_FLOAT, !spec.autoExpand)
                    putBoolean(ISLAND_FIRST_FLOAT, spec.autoExpand)
                    putBoolean(MIUI_ENABLE_FLOAT, spec.autoExpand)
                },
            )
            .build()
    }

    /**
     * 展开态主卡：图标 + 标题 + 正文（[InstallerX] / LinkGo 同构，HyperOS 4 实测渲染）。
     *
     * 之前用 `baseInfo{type:1}`，通知栏里能看，但大岛取不到标准图文组件，展开态退化成
     * 纯文字卡；`iconTextInfo` 是官方模板里带图标的主卡组件。
     */
    private fun iconTextInfo(spec: IslandNotificationSpec, accentHtml: String?): JSONObject =
        JSONObject()
            .put("title", escapeHtml(spec.title))
            .put("content", escapeHtml(spec.text.ifBlank { " " }))
            .put("animIconInfo", JSONObject().put("type", 0).put("src", KEY_LOGO))
            .apply { accentHtml?.let { put("colorTitle", it).put("colorTitleDark", it) } }

    private fun islandPayload(spec: IslandNotificationSpec, accentHtml: String?): JSONObject =
        JSONObject()
            // 「操作为主」：岛上有按钮时用 2；信息展示为主（无按钮，如备份失败）时用 1。
            .put("islandProperty", if (spec.actions.isEmpty()) 1 else 2)
            .put("islandOrder", false)
            .put("islandPriority", 2)
            .put("dismissIsland", false)
            .put("needCloseAnimation", true)
            .put("maxSize", false)
            // 首帧展开后大岛停留秒数；0 = 不展开（此时 islandFirstFloat 也是 false）。
            .put("expandedTime", if (spec.autoExpand) expandedSeconds(spec.expandedSeconds) else 0)
            // 长按岛卡片拖拽分享：三段纯文本（预览卡 + 最终插入文本）。
            .apply {
                spec.share?.let { share ->
                    put(
                        "shareData",
                        JSONObject()
                            .put("title", share.title.take(SHARE_TEXT_MAX))
                            .put("content", share.content.take(SHARE_CONTENT_MAX))
                            .put("shareContent", share.shareContent.take(SHARE_TEXT_MAX * 5)),
                    )
                }
            }
            .apply { accentHtml?.let { put("highlightColor", it) } }
            .put(
                "bigIslandArea",
                // 结构对齐 InstallerX（HyperOS 4 实测可用）：imageTextInfoLeft + imageTextInfoRight{type:3}。
                // Playground 的 LEFT_TEXT（根级 textInfo）在本 ROM 被拒，大岛 fallback 成 baseInfo 纯文字卡。
                JSONObject()
                    .put(
                        "imageTextInfoLeft",
                        JSONObject()
                            .put("type", 1)
                            .put("picInfo", JSONObject().put("type", 1).put("pic", KEY_LOGO)),
                    )
                    .put(
                        "imageTextInfoRight",
                        JSONObject()
                            .put("type", 3)
                            .put(
                                "textInfo",
                                JSONObject().put(
                                    "title",
                                    escapeHtml(spec.capsuleText.ifBlank { spec.title }),
                                ),
                            ),
                    ),
            )
            .put(
                "smallIslandArea",
                JSONObject().put(
                    "picInfo",
                    JSONObject().put("type", 1).put("pic", KEY_LOGO),
                ),
            )
            .apply {
                if (spec.timeoutSeconds > 0) {
                    put("islandTimeout", islandTimeoutSeconds(spec.timeoutSeconds))
                }
            }

    private fun actionsPayload(spec: IslandNotificationSpec): JSONArray = JSONArray().apply {
        spec.actions.take(MAX_ISLAND_ACTIONS).forEach { action ->
            put(
                JSONObject()
                    // `action` 引用 miui.focus.actions Bundle 里的 [Notification.Action]（键同名）。
                    .put("type", ACTION_TYPE_TEXT)
                    .put("action", action.key)
                    .put("actionTitle", escapeHtml(action.title))
                    // SystemUI 按这个值决定用 action 的 PendingIntent 拉起（而不是把 actionIntent 当 URI）。
                    .put("actionIntentType", ACTION_INTENT_TYPE_PENDING)
                    // 动作可以声明「不收起岛」：+1 这类要留在原地展示进度变化。
                    .put("clickWithCollapse", action.collapseOnClick)
                    .apply {
                        if (action.highlighted) {
                            put("actionBgColor", HIGHLIGHT_BG)
                            put("actionBgColorDark", HIGHLIGHT_BG)
                            put("actionTitleColor", HIGHLIGHT_TITLE)
                            put("actionTitleColorDark", HIGHLIGHT_TITLE)
                        }
                    },
            )
        }
    }

    /**
     * `miui.focus.actions`：[Notification.Action] 与 `textButton[i].action` 同名对应。
     *
     * **必须是 `Notification.Action`（含有效 Icon），不能是裸 PendingIntent**——
     * SystemUI 按 Action 取 `actionIntent` 落地；直接放 PendingIntent 时按钮渲染正常
     * 但点击无任何反应（实测无广播、无异常日志）。
     */
    private fun actionsBundle(spec: IslandNotificationSpec): android.os.Bundle? {
        val taken = spec.actions.take(MAX_ISLAND_ACTIONS)
        if (taken.isEmpty()) return null
        val icon = Icon.createWithResource(appContext, appIconRes())
        return android.os.Bundle().apply {
            taken.forEachIndexed { index, action ->
                putParcelable(
                    action.key,
                    Notification.Action.Builder(
                        icon,
                        action.title,
                        actionPendingIntent(spec.notificationId, index, action),
                    ).build(),
                )
            }
        }
    }

    /** 契约层不携带 PendingIntent（见 [IslandIntentKind]），这里按 kind 构建。 */
    private fun actionPendingIntent(
        notificationId: Int,
        index: Int,
        action: IslandActionSpec,
    ): PendingIntent {
        val intent = Intent.parseUri(action.intentUri, Intent.URI_INTENT_SCHEME)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return when (action.intentKind) {
            IslandIntentKind.ACTIVITY ->
                PendingIntent.getActivity(appContext, notificationId + index, intent, flags)
            IslandIntentKind.BROADCAST ->
                PendingIntent.getBroadcast(appContext, notificationId + index, intent, flags)
        }
    }

    private fun contentIntent(spec: IslandNotificationSpec): PendingIntent? {
        val uri = spec.contentIntentUri ?: return null
        return runCatching {
            PendingIntent.getActivity(
                appContext,
                spec.notificationId,
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()
    }

    private fun appIconRes(): Int =
        appContext.applicationInfo.icon.takeIf { it != 0 }
            ?: android.R.drawable.sym_def_app_icon

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun colorToHtml(color: Int): String =
        "#%02X%02X%02X".format(
            java.util.Locale.US,
            (color shr 16) and 0xFF,
            (color shr 8) and 0xFF,
            color and 0xFF,
        )

    internal sealed interface Result {
        data object Posted : Result
        data object Rejected : Result
        data object BypassUnavailable : Result
    }

    internal companion object {
        /** 进程内事务锁：跨 presenter 实例串行化 XMSF 绕过（block → notify → restore）。 */
        val PROCESS_LOCK = Any()

        const val FOCUS_PARAM = "miui.focus.param"
        const val FOCUS_PICS = "miui.focus.pics"
        const val FOCUS_ACTIONS = "miui.focus.actions"
        const val KEY_LOGO = "miui.focus.pic_island_logo"
        const val BUSINESS_NAME = "custom_island"
        const val ISLAND_UPDATE_NO_FLOAT = "miui.island.updateNoFloat"
        const val ISLAND_FIRST_FLOAT = "miui.island.firstFloat"
        const val MIUI_ENABLE_FLOAT = "miui.enableFloat"

        /** protocol=3 严格反序列化的 V3 模板序列名（xzakota focus-api 同值镜像）。 */
        const val FOCUS_V3_SERIAL_NAME =
            "com.xzakota.hyper.notification.focus.FocusNotification.FocusTemplateFactory.V3"

        const val VALIDATION_WINDOW_MS = 220L

        /** 恢复网络后的校验等待：抓「SystemUI 有没有撤回」。 */
        const val AFTER_RESTORE_VALIDATION_MS = 120L

        const val MIN_BLOCK_WINDOW_MS = 50L
        const val MAX_BLOCK_WINDOW_MS = 2_000L

        /** 预热事务的驻留窗口：够 SystemUI 发起鉴权，又不至于真被用户看到。 */
        const val WARMUP_WINDOW_MS = 120L
        const val MAX_FOCUS_PARAM_BYTES = 3_072
        const val MAX_ISLAND_ACTIONS = 2

        /** textButton 组件类型：2 = 文字按钮（0 圆按钮 / 1 进度按钮）。 */
        const val ACTION_TYPE_TEXT = 2

        /** actionIntentType：1 = 用 `miui.focus.actions` 里的 PendingIntent（2 = 用 actionIntent URI）。 */
        const val ACTION_INTENT_TYPE_PENDING = 1


        /** 外发光 effect 名（SystemUI 侧图片预置）。 */
        const val OUT_EFFECT_OUTER_GLOW = "outer_glow"

        /** 拖拽分享文本上限（与 LinkGo 同口径：title 32 / content 80 / shareContent 500）。 */
        const val SHARE_TEXT_MAX = 32
        const val SHARE_CONTENT_MAX = 80

        const val HIGHLIGHT_BG = "#006EFF"
        const val HIGHLIGHT_TITLE = "#FFFFFF"
    }
}

/** 岛超时上限 12 小时：小时级 payload 字段（秒 / 分钟）共用同一封顶。 */
private const val MAX_ISLAND_TIMEOUT_SECONDS = 43_200

/**
 * `param_island.islandTimeout`：秒，0 = 不设超时。
 *
 * 超过上限的配置按上限算，不向少量超时的调用方报错（超时不是安全边界）。
 */
internal fun islandTimeoutSeconds(configuredSeconds: Int): Int =
    configuredSeconds.coerceIn(0, MAX_ISLAND_TIMEOUT_SECONDS)

/**
 * `param_v2.timeout`：**分钟**（系统按分钟读）。向上取整，不缩短用户选定的超时。
 *
 * 两个字段必须同源换算：曾直接对秒值做分钟封顶，13 分钟以上的配置全被截成
 * 12 分钟，与 `islandTimeout` 不一致。
 */
internal fun islandTimeoutMinutes(configuredSeconds: Int): Long =
    (islandTimeoutSeconds(configuredSeconds) + 59) / 60L

/** 大岛停留秒数：限制在可感知且有界的区间（0 = 不展开）。 */
internal fun expandedSeconds(configuredSeconds: Int): Int = configuredSeconds.coerceIn(1, 60)

/**
 * 断网时长：spec 值优先（设置页「断网时间」），否则用发布器构造参数（调试窗口）。
 * 限制在 50..2000ms：低于 50 基本赶不上鉴权，高于 2s 只是白断网。
 */
internal fun blockWindowMs(configuredMs: Int, fallbackMs: Long): Long =
    configuredMs.takeIf { it > 0 }
        ?.toLong()
        ?.coerceIn(IslandPublisher.MIN_BLOCK_WINDOW_MS, IslandPublisher.MAX_BLOCK_WINDOW_MS)
        ?: fallbackMs
