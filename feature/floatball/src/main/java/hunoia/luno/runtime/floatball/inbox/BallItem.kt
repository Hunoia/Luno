package hunoia.luno.runtime.floatball.inbox

import hunoia.luno.config.model.RecognitionDefaults
import java.security.MessageDigest

/**
 * 智能球收件箱的内容来源。
 *
 * 现在只有两条独立队列：普通通知进通知胶囊，验证码/链接进建议胶囊。
 */
internal enum class BallSource {
    NOTIFICATION,
    SUGGESTION,
}

/**
 * 收件箱条目公共元数据。
 *
 * 气泡渲染所需的动作载荷（验证码 / 链接 / 目标应用）放在具体条目里，
 * 纯状态模型不依赖 Android 组件。
 */
internal sealed interface BallItem {
    val id: String
    val source: BallSource
    val receivedAtElapsedMs: Long

    /** 本条的固定 TTL；null = 跟随队列配置里该来源的默认值。 */
    val ttlMs: Long?
    val pinned: Boolean
    val title: String
    val text: String

    fun withPinned(pinned: Boolean): BallItem
}

/**
 * 通知项。通知只用系统 key 去重，v1 不支持钉住。
 */
internal data class NotificationItem(
    val notificationKey: String,
    val packageName: String,
    override val title: String,
    override val text: String,
    override val receivedAtElapsedMs: Long,
    override val ttlMs: Long? = null,
) : BallItem {
    override val id: String = "notif:$notificationKey"
    override val source: BallSource = BallSource.NOTIFICATION
    override val pinned: Boolean get() = false

    override fun withPinned(pinned: Boolean): BallItem = this
}

/**
 * 建议主动作，由识别器的输出决定。
 *
 * [RUN_ACTION] = 规则配的是「动作输出」：主动作就是跑那条动作，执行细节交给
 * `RecognitionExecutor`，胶囊不再为每种动作长一个分支。
 * OPEN 捕获不到链接时创建阶段已降级为 COPY。
 */
internal enum class SuggestionPrimaryAction {
    COPY,
    OPEN,
    SHARE,
    RUN_ACTION,
}

/**
 * 建议项（规则命中或内置链接识别）。
 *
 * [sourceKey] 是来源去重键（通知 key / 剪贴板内容指纹 / 界面指纹），用于「滑掉后短时间同源抑制重弹」。
 * 捕获文本可能敏感：本类的 [toString] 刻意只保留类型与哈希 ID，不输出正文。
 */
internal data class SuggestionItem(
    val sourceKey: String,
    override val title: String,
    override val text: String,
    override val receivedAtElapsedMs: Long,
    override val ttlMs: Long? = null,
    /** 命中的规则 id（内置链接识别也是一条规则，恒非空）。 */
    val ruleId: String? = null,
    val action: SuggestionPrimaryAction = SuggestionPrimaryAction.COPY,
    /** 非链接捕获结果按敏感文本处理：复制不入历史、锁屏不自动弹。 */
    val sensitive: Boolean = false,
    val urls: List<String> = emptyList(),
    /**
     * 识别器给出的原始结果：执行时原样交给 `RecognitionExecutor`，
     * 胶囊不再自己把动作枚举翻译成链接分支。null = 旧路径 / 无规则项。
     */
    val recognized: hunoia.luno.runtime.recognition.RecognizedItem? = null,
    val targetPackage: String? = null,
    val targetLabel: String? = null,
    override val pinned: Boolean = false,
    /**
     * 情景识别来源的界面指纹；非 null 表示这一项属于某次界面扫描，
     * 新一次扫描到达时按 [BallInboxAction.DropScene] 整批替换。null = 通知 / 剪贴板来源。
     */
    val sceneKey: String? = null,
) : BallItem {
    override val id: String =
        "sug:${stableHash("${ruleId ?: "link"}|$sourceKey|$text|${urls.joinToString("|")}")}"
    override val source: BallSource = BallSource.SUGGESTION

    val primaryUrl: String? get() = urls.firstOrNull()
    val linkCount: Int get() = urls.size

    override fun withPinned(pinned: Boolean): BallItem = copy(pinned = pinned)

    /** 防泄露：规则捕获文本与链接正文不进日志。 */
    override fun toString(): String =
        "SuggestionItem(rule=${ruleId ?: "link"}, id=$id, sourceKey=${sourceKey.take(8)}, pinned=$pinned)"
}

/** 内容指纹哈希（sha-256 前 16 位十六进制），稳定且不落原文。 */
internal fun stableHash(raw: String): String = runCatching {
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    digest.take(8).joinToString("") { "%02x".format(it) }
}.getOrDefault((raw.hashCode().toLong() and 0xFFFFFFFFL).toString(16))

// ---- 情景识别（同一屏）在收件箱里的查询谓词 ----
//
// 纯函数：收件箱的读取方分布在主线程与后台调度器上，把判定收在这里，调用方只需在
// 自己那一侧负责「取 items」，谓词本身不碰线程、可单测。

/** 当前收件箱里是否已经有这一屏的情景建议。 */
internal fun hasSceneItems(items: List<BallItem>, scanKey: String): Boolean =
    items.any { (it as? SuggestionItem)?.sceneKey == scanKey }

/** 这一屏内置链接项（链接规则命中的情景项）的下标；没有返回 -1。 */
internal fun builtInSceneLinkIndex(items: List<BallItem>, scanKey: String): Int = items.indexOfFirst {
    val item = it as? SuggestionItem
    item != null && item.sceneKey == scanKey && item.ruleId == RecognitionDefaults.LinkRuleId
}

/** 这一屏是否已经有一条内置链接项。 */
internal fun hasBuiltInSceneLink(items: List<BallItem>, scanKey: String): Boolean =
    builtInSceneLinkIndex(items, scanKey) >= 0

/** 这一屏文本规则命中项里已用掉的链接：重建链接项时排除，避免同一链接两条。 */
internal fun usedSceneRuleUrls(items: List<BallItem>, scanKey: String): Set<String> = items.asSequence()
    .filterIsInstance<SuggestionItem>()
    .filter { it.sceneKey == scanKey && it.ruleId != RecognitionDefaults.LinkRuleId }
    .flatMap { it.urls.asSequence() }
    .toSet()
