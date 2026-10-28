package hunoia.luno.link

import android.content.Context
import hunoia.luno.config.model.LinkDispatchRule
import hunoia.luno.config.model.LinkMatchType

/**
 * 一条预设规则：显示名 + 匹配串 + 目标包名候选（按顺序取第一个已安装的）。
 *
 * 同一应用的不同发行版包名不一样（B 站国内版 `tv.danmaku.bili`、海外版
 * `com.bilibili.app.in`；Telegram 官方版与 Web 版），写死一个会在另一半设备上失效。
 */
data class LinkPreset(
    val name: String,
    val pattern: String,
    val targetPackages: List<String>,
    /** 交给浏览器打开（短链场景），见 [LinkDispatchRule.viaBrowser]。 */
    val viaBrowser: Boolean = false,
    /** 解析短链（联网），见 [LinkDispatchRule.resolveShortLink]。 */
    val resolveShortLink: Boolean = false,
) {
    constructor(
        name: String,
        pattern: String,
        targetPackage: String,
        viaBrowser: Boolean = false,
        resolveShortLink: Boolean = false,
    ) : this(name, pattern, listOf(targetPackage), viaBrowser, resolveShortLink)
}

/**
 * 常用链接分发预设（B 站 / 抖音 / 小红书 / 淘宝 / 网易云 / 拼多多 / Telegram / GitHub / 微信读书）。
 *
 * 收录口径：**该应用在设备上确实声明了这条域名的 ACTION_VIEW**。
 * 分发走的是 `ACTION_VIEW + setPackage`，应用没声明该域名时会退化成「打开应用首页」，
 * 所以不能凭印象乱加（实测：小红书的 xhslink.com、抖音的 douyin.com、闲鱼的 goofish.com、
 * 美团/饿了么/贴吧/微博的 web 域名都不在各自 filter 里，因此没有收录）。
 * 应用改版可能调整 filter，届时改这一张表即可。
 */
object LinkPresets {

    val all: List<LinkPreset> = listOf(
        LinkPreset("哔哩哔哩", "b23.tv", listOf("tv.danmaku.bili", "com.bilibili.app.in")),
        LinkPreset("哔哩哔哩", "bilibili.com", listOf("tv.danmaku.bili", "com.bilibili.app.in")),
        LinkPreset("抖音", "v.douyin.com", "com.ss.android.ugc.aweme"),
        LinkPreset("小红书", "xiaohongshu.com", "com.xingin.xhs"),
        LinkPreset("淘宝", "m.tb.cn", "com.taobao.taobao"),
        LinkPreset("淘宝", "taobao.com", "com.taobao.taobao"),
        LinkPreset("网易云音乐", "163cn.tv", "com.netease.cloudmusic"),
        LinkPreset("网易云音乐", "music.163.com", "com.netease.cloudmusic"),
        LinkPreset("拼多多", "yangkeduo.com", "com.xunmeng.pinduoduo"),
        LinkPreset(
            "Telegram",
            "t.me",
            listOf("org.telegram.messenger", "org.telegram.messenger.web"),
        ),
        LinkPreset("GitHub", "github.com", "com.github.android"),
        LinkPreset("微信读书", "weread.qq.com", "com.tencent.weread"),
        // 小红书分享短链：xhslink.com / xhslink.cn 都没被应用声明，跟随后通常落到
        // xiaohongshu.com（会被上面那条规则接手）；解析不到就用本规则的目标把解析后的
        // 地址交给小红书。用 `xhslink` 前缀一条覆盖两个域名。
        LinkPreset("小红书短链", "xhslink", "com.xingin.xhs", resolveShortLink = true),
    )

    /**
     * 本机可用的预设：目标应用已安装，且同匹配串的规则尚不存在（按 pattern 判重）。
     * 每条都带新 id，直接拼进 [hunoia.luno.config.model.LinkDispatchSettings.rules] 即可。
     */
    fun addable(context: Context, existing: List<LinkDispatchRule>): List<LinkDispatchRule> {
        val pm = context.packageManager
        val usedPatterns = existing
            .map { it.pattern.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        val installed = mutableMapOf<String, Boolean>()
        fun isInstalled(pkg: String): Boolean = installed.getOrPut(pkg) {
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
        return all.mapNotNull { preset ->
            val pattern = preset.pattern
            if (!usedPatterns.add(pattern.lowercase())) return@mapNotNull null
            // 交给浏览器的规则没有固定目标包名：本机只要有浏览器就能用，不做安装校验。
            val pkg = if (preset.viaBrowser) {
                preset.targetPackages.firstOrNull().orEmpty()
            } else {
                preset.targetPackages.firstOrNull(::isInstalled) ?: return@mapNotNull null
            }
            LinkDispatchRule(
                name = preset.name,
                matchType = LinkMatchType.CONTAINS,
                pattern = pattern,
                targetPackage = pkg,
                viaBrowser = preset.viaBrowser,
                resolveShortLink = preset.resolveShortLink,
            )
        }
    }
}
