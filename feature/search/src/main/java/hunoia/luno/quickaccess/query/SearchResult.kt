package hunoia.luno.quickaccess.query

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.core.domain.StoredAction

/**
 * 快速搜索的展示层结果：Activity 只认识 [SearchAction]，不再按联系人/短信/文件等
 * 业务类型分支。新增来源只要产出 SearchResult 即可。
 */
data class SearchResult(
    val key: String,
    val source: String,
    val title: String,
    val subtitle: String,
    val icon: SearchIcon,
    val primary: SearchAction,
    /** 结果专属动作；通用复制/Pin/Hide 由长按 Sheet 动态补。 */
    val actions: List<SearchActionItem> = emptyList(),
    /** 标题后缀（如「已停用」），null = 不显示。 */
    val badge: SearchBadge? = null,
)

/** 长按 Sheet 的一项：标签 + 动作 + 是否破坏性。 */
data class SearchActionItem(
    val label: SearchActionLabel,
    val action: SearchAction,
    val destructive: Boolean = false,
)

sealed interface SearchActionLabel {
    data class Res(val id: Int) : SearchActionLabel
    data class Literal(val text: String) : SearchActionLabel
}

enum class SearchBadge { DisabledApp }

const val SOURCE_CALC = "calc"
const val SOURCE_URL = "url"
const val SOURCE_ENGINE = "engine"

sealed interface SearchIcon {
    data object None : SearchIcon
    data class Vector(val image: ImageVector) : SearchIcon
    data class Bitmap(val image: ImageBitmap) : SearchIcon
    /** 应用图标：行内按当前 density 从缓存取，未命中再走 Coil。 */
    data class AppIcon(
        val packageName: String,
        val bitmap: ImageBitmap? = null,
        /** 0 = 主用户；分身用角标图标，缓存键也要分开。 */
        val userId: Int = 0,
    ) : SearchIcon
    data class Request(val model: Any) : SearchIcon
    data class Text(val text: String) : SearchIcon
}

sealed interface SearchAction {
    /** 走动作分发（应用启动、动作库、设置页、剪贴板等）。 */
    data class Run(val action: StoredAction) : SearchAction

    /** 直接 startActivity。 */
    data class Open(val intent: Intent) : SearchAction

    /** 搜索引擎 URL：可指定目标应用、强制选择器，失败时回退。 */
    data class OpenUrl(
        val url: String,
        val packageName: String = "",
        val forceChooser: Boolean = false,
        val fallbackUrl: String = "",
        /** 用 Custom Tabs 打开（[packageName] 为空时生效）。 */
        val useCustomTabs: Boolean = false,
        /** 用 Luno 小窗卡片（WebView）打开，优先级最高。 */
        val useSmallWindow: Boolean = false,
    ) : SearchAction

    /**
     * 跳转到目标并注入查询词（参考 GestureEVO 的 JUMP_TO_ACTIVITY / EXTERN_JUMP_LINK）：
     * [targetActivity] 非空 → 打开该 Activity；否则打开 [externLink]（在 [targetPackage] 里）。
     * 打开后把 [query] 注入焦点输入框，[autoInputEnter] 时再按一次 IME 回车。
     */
    data class InjectQuery(
        val targetPackage: String,
        val targetActivity: String = "",
        val externLink: String = "",
        val query: String = "",
        val autoInputEnter: Boolean = false,
    ) : SearchAction

    data class Copy(val text: String) : SearchAction

    data class Share(val uri: String, val mime: String) : SearchAction

    /** 回填搜索框，不关面板。 */
    data class SetQuery(val text: String) : SearchAction

    /**
     * 图文双模式「每次询问」：先让用户选走文字搜索还是分享图片，选完再转成
     * [SearchAction.OpenUrl] / [SearchAction.PickImage] 执行。
     * 面板层弹选择器，不直接执行任何打开动作。
     */
    data class AskContent(
        val textAction: SearchAction,
        val imagePackageName: String,
    ) : SearchAction

    /** 图片搜索：先让用户选图，选完由 Activity 转成 [ShareImage]。 */
    data class PickImage(val packageName: String) : SearchAction

    /** 分享图片到指定应用。 */
    data class ShareImage(val uri: String, val packageName: String) : SearchAction
}
