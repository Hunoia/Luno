package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 链接分发：认出文本里的链接后，按规则把它送进指定应用。
 *
 * 两个入口共用同一套规则与判定：
 * - 剪贴板胶囊（`:service` 的 [hunoia.luno.runtime.floatball.FloatBallService]）：
 *   复制到链接时胶囊显示目标应用，点击直达；多条链接给角标进选择页。
 * - 外部打开/分享到 Luno（[hunoia.luno.ui.link.LinkDispatchActivity]）：Luno 可被设为
 *   默认浏览器，收到的 http(s) 链接同样按规则分发。
 *
 * 必须挂在 [ActionSettings] 下，才能顺着 ConfigProvider → SettingsStore → SettingsState
 * 到达 `:service` 进程（见 FloatBallSettings 的类注释）。
 */
@Serializable
@Keep
data class LinkDispatchSettings(
    /**
     * 总开关：关掉后胶囊不再识别链接、外部打开也不按规则分发（走 [missAction]）。
     * 识屏幽灵卡的「打开链接」仍可用，但同样跳过规则分发、按 [missAction] 回退。
     */
    val enabled: Boolean = true,
    /** 规则表：按顺序取第一条命中（不设优先级字段）。 */
    val rules: List<LinkDispatchRule> = emptyList(),
    /** 胶囊命中链接时的点击行为（未命中恒走 [missAction]）。 */
    val capsuleTap: LinkTapMode = LinkTapMode.DISPATCH,
    /** 未命中任何规则时的行为（胶囊点击与外部打开共用）。 */
    val missAction: LinkMissMode = LinkMissMode.SHARE,
)

/** 规则匹配方式。 */
@Serializable
@Keep
enum class LinkMatchType {
    /** 域名（含子域）或完整 URL 包含即可。 */
    CONTAINS,
    /** 只比对域名：相等或子域，`bilibili.com` 不会命中 `bilibili.com.evil.com`。 */
    DOMAIN,
    /** 正则 `find`（部分匹配）。 */
    REGEX,
    /** 整串相等（忽略大小写）。 */
    EXACT,
}

/** 胶囊命中链接时的点击行为。 */
@Serializable
@Keep
enum class LinkTapMode {
    /** 直达规则目标应用。 */
    DISPATCH,
    /** 保持既有行为：弹系统分享面板。 */
    SHARE,
}

/** 未命中规则时的行为。 */
@Serializable
@Keep
enum class LinkMissMode {
    /** 系统分享面板（默认）。 */
    SHARE,
    /** 用其他浏览器打开（解析到 Luno 自身时改用选择器，避免自我循环）。 */
    BROWSER,
    /** 列出能打开 http(s) 的应用让用户挑。 */
    PICKER,
}

/**
 * 一条分发规则。
 *
 * [template] 为空 = 原样分发；非空时按 [hunoia.luno.link.LinkUrls.applyTemplate] 改写，
 * 支持 `{url}` `{host}` `{path}` `{query}` `{scheme}` 与 `{url_enc}` / `{url_dec}`。
 * [miniWindow] 走全局小窗设置（动作设置 → 小窗）。
 */
@Serializable
@Keep
data class LinkDispatchRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val matchType: LinkMatchType = LinkMatchType.CONTAINS,
    val pattern: String = "",
    val targetPackage: String = "",
    /** 目标 Activity（可选）：留空由系统按 ACTION_VIEW + 包名解析。 */
    val targetActivity: String = "",
    val template: String = "",
    val miniWindow: Boolean = false,
    /**
     * 交给浏览器打开（不联网解析短链的 B 方案）。
     *
     * 适用于 `xhslink.com` 这类「应用没有声明该域名」的短链：交给本机另一个浏览器
     * 打开，由浏览器跟随后跳，再靠 App Link / 网页里的「打开APP」回到目标应用。
     * [targetPackage] 为空 = 自动挑一个本机浏览器（永远不会选 Luno 自己，否则死循环）。
     */
    val viaBrowser: Boolean = false,
    /**
     * 短链解析（联网）：命中本规则后先跟随后（3s 超时、最多 5 跳），把最终 URL 与页面里
     * 的跳转目标重新匹配一遍规则；二次匹配失败时仍用本规则的目标，但换成解析后的 URL。
     * 用于 `xhslink.com` 这类应用没有声明的短链（方案 A，需要 INTERNET 权限）。
     */
    val resolveShortLink: Boolean = false,
    /**
     * 排除串：URL 里出现任意一条（忽略大小写）就跳过本规则，继续往下匹配。
     * 用来挡「同一域名下的特定路径」，比写复杂正则直观。
     */
    val excludePatterns: List<String> = emptyList(),
    /** 本规则命中但分发失败时的回退；null = 跟随全局未命中策略。 */
    val missAction: LinkMissMode? = null,
) {
    val isValid: Boolean
        get() = pattern.isNotBlank() && (targetPackage.isNotBlank() || viaBrowser)

    /** 有效排除串（去空白、去空项）。 */
    val effectiveExcludes: List<String>
        get() = excludePatterns.map { it.trim() }.filter { it.isNotEmpty() }
}

/** 新增/更新/删除都走这里，保持 `ActionSettings` 的字段级合并语义。 */
fun LinkDispatchSettings.upsert(rule: LinkDispatchRule): LinkDispatchSettings {
    val exists = rules.any { it.id == rule.id }
    return copy(
        rules = if (exists) rules.map { if (it.id == rule.id) rule else it } else rules + rule,
    )
}

fun LinkDispatchSettings.remove(ruleId: String): LinkDispatchSettings =
    copy(rules = rules.filterNot { it.id == ruleId })
