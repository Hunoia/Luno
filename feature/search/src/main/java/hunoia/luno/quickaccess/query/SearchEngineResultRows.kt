package hunoia.luno.quickaccess.query

import android.content.Context
import android.content.Intent
import hunoia.luno.search.R
import hunoia.luno.config.model.EngineContentMode
import hunoia.luno.config.model.EngineIconType
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.badgeText
import hunoia.luno.config.model.copyUrl
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.config.model.fillSearchQuery
import hunoia.luno.config.model.SearchLaunch.Action as SearchLaunchAction
import hunoia.luno.config.model.SearchLaunch.Activity as SearchLaunchActivity
import hunoia.luno.config.model.SearchLaunch.Chooser
import hunoia.luno.config.model.SearchLaunch.Share as SearchLaunchShare
import hunoia.luno.config.model.SearchLaunch.ShareImage as SearchLaunchShareImage
import hunoia.luno.config.model.SearchLaunch.Url as SearchLaunchUrl

/**
 * 引擎/来源 launch 配置 → 展示层动作：查询词模板填充在这里一次完成。
 * 引擎行、直达行共用；新来源的 launch 配置也走这里。
 */
fun SearchLaunch.toSearchAction(query: String): SearchAction = when (this) {
    is SearchLaunchUrl -> SearchAction.OpenUrl(
        url = template.fillSearchQuery(query),
        packageName = packageName,
        fallbackUrl = fallbackTemplate.takeIf { it.isNotBlank() }
            ?.fillSearchQuery(query)
            .orEmpty(),
        useCustomTabs = useCustomTabs,
        useSmallWindow = useSmallWindow,
    )

    is Chooser -> SearchAction.OpenUrl(
        url = template.fillSearchQuery(query),
        forceChooser = true,
    )

    is SearchLaunchShare -> SearchAction.Open(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textTemplate.fillSearchQuery(query))
            if (packageName.isNotBlank()) setPackage(packageName)
        },
    )

    is SearchLaunchShareImage -> SearchAction.PickImage(packageName)

    is SearchLaunchActivity -> SearchAction.InjectQuery(
        targetPackage = targetPackage,
        targetActivity = targetActivity,
        externLink = externLink,
        query = query,
        autoInputEnter = autoInputEnter,
    )

    is SearchLaunchAction -> SearchAction.Run(action.fillSearchQuery(query))
}

/**
 * 单个引擎的结果行：默认行与「标识直达」行共用这一份。
 * key 固定为 `es#<引擎id>|<query>`，两条路径产出的行才能完全一致
 * （图标文字、复制链接、分享图片）。
 */
fun engineResultRow(
    context: Context,
    query: String,
    engine: SearchEngine,
): SearchResult = engineResultRow(
    title = context.getString(R.string.quick_search_engine_search, engine.name),
    query = query,
    engine = engine,
)

/** 不依赖 Context 的版本：标题由调用方给（索引层已有解析好的标题）。 */
fun engineResultRow(
    title: String,
    query: String,
    engine: SearchEngine,
): SearchResult {
    val launch = engine.effectiveLaunch()
    val copy = launch.copyUrl(query)
    val imageTarget = engine.imageTarget(launch)
    val actions = buildList {
        // 长按动作里始终提供「分享图片」（选图/剪贴板，见 PickImage 执行）。
        if (imageTarget.isNotBlank()) {
            add(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_share_image),
                    SearchAction.PickImage(imageTarget),
                ),
            )
        }
        copy?.let {
            add(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_copy_link),
                    SearchAction.Copy(it),
                ),
            )
        }
    }
    return SearchResult(
        key = "es#${engine.id}|$query",
        source = SOURCE_ENGINE,
        title = title,
        subtitle = "",
        icon = engineRowIcon(engine),
        primary = enginePrimaryAction(engine, query),
        actions = actions,
    )
}

/**
 * 引擎的图片分享目标包名：纯图片引擎（[SearchLaunch.ShareImage]）用 launch 的包名；
 * 图文双模式用 [SearchEngine.imageTargetPackage]。两者都没有返回空串。
 */
fun SearchEngine.imageTarget(launch: SearchLaunch = effectiveLaunch()): String = when {
    launch is SearchLaunch.ShareImage -> launch.packageName
    imageShareEnabled -> imageTargetPackage
    else -> ""
}

/**
 * 点击引擎的主动作：**唯一出口**，[EngineContentMode] 在这里生效。
 *
 * - [EngineContentMode.Image]：直接选图分享（要求双模式开启且有目标应用，否则回退文字搜索）
 * - [EngineContentMode.Ask]：交给面板弹选择器（[SearchAction.AskContent]）
 * - [EngineContentMode.Text] / 双模式未开启：常规文字搜索
 *
 * 纯图片引擎（ShareImage）不受 contentMode 影响，始终是选图。
 */
fun enginePrimaryAction(engine: SearchEngine, query: String): SearchAction {
    val launch = engine.effectiveLaunch()
    val textAction = launch.toSearchAction(query)
    // 纯图片引擎：launch 本身就是选图，contentMode 不参与。
    if (launch is SearchLaunch.ShareImage) return textAction
    val imageTarget = engine.imageTarget(launch)
    if (imageTarget.isBlank()) return textAction
    return when (engine.contentMode) {
        EngineContentMode.Image -> SearchAction.PickImage(imageTarget)
        EngineContentMode.Ask -> SearchAction.AskContent(
            textAction = textAction,
            imagePackageName = imageTarget,
        )
        EngineContentMode.Text -> textAction
    }
}

/** 引擎行图标：按 [EngineIconType] 给应用图标 / 图片 URI / 文字徽标；来源缺失回退文字。 */
fun engineRowIcon(engine: SearchEngine): SearchIcon = when (engine.iconType) {
    EngineIconType.App ->
        if (engine.icon.isNotBlank()) SearchIcon.AppIcon(engine.icon) else SearchIcon.Text(engine.badgeText)
    EngineIconType.Image ->
        if (engine.icon.isNotBlank()) SearchIcon.Request(engine.icon) else SearchIcon.Text(engine.badgeText)
    EngineIconType.Text -> SearchIcon.Text(engine.badgeText)
}
