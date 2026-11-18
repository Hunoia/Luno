package hunoia.luno.config.model

/**
 * 常用搜索引擎预设：「添加常用引擎」里的那一张表。
 *
 * 与识别器的 `RecognitionDefaults` 同一口径——**表就是唯一事实源**：设置页只负责
 * 逐条添加（按 [urlTemplate] 判重），新建引擎仍走编辑页（可改标识、图标、打开方式）。
 *
 * `%s` 是 URL 编码后的查询词，`%q` 是原词；这里一律用 `%s`（搜索引擎都要编码）。
 * 预置 [trigger] 是为了让「标识 + 空格 + 内容」直达开箱可用，撞了已有标识由添加方清空。
 */
object SearchEnginePresets {

    private fun engine(
        name: String,
        icon: String,
        trigger: String,
        template: String,
    ) = SearchEngine(
        name = name,
        iconText = icon,
        trigger = trigger,
        urlTemplate = template,
        launch = SearchLaunch.Url(template),
    )

    /** 预设表。顺序即「添加常用引擎」里的展示顺序，常用在前。 */
    val all: List<SearchEngine> = listOf(
        engine("必应", "必", "bing", "https://www.bing.com/search?q=%s"),
        engine("百度", "百", "baidu", "https://www.baidu.com/s?wd=%s"),
        engine("谷歌", "G", "google", "https://www.google.com/search?q=%s"),
        engine("知乎", "知", "zhihu", "https://www.zhihu.com/search?type=content&q=%s"),
        engine("哔哩哔哩", "B", "bili", "https://search.bilibili.com/all?keyword=%s"),
        engine("淘宝", "淘", "taobao", "https://s.taobao.com/search?q=%s"),
        engine("京东", "东", "jd", "https://search.jd.com/Search?keyword=%s"),
        engine("微博", "微", "weibo", "https://s.weibo.com/weibo?q=%s"),
        engine("豆瓣", "豆", "douban", "https://www.douban.com/search?q=%s"),
        engine("小红书", "红", "xhs", "https://www.xiaohongshu.com/search_result?keyword=%s"),
        engine("抖音", "抖", "douyin", "https://www.douyin.com/search/%s"),
        engine("GitHub", "GH", "gh", "https://github.com/search?q=%s"),
        engine("Stack Overflow", "SO", "so", "https://stackoverflow.com/search?q=%s"),
        engine("维基百科", "W", "wiki", "https://zh.wikipedia.org/w/index.php?search=%s"),
        engine("YouTube", "YT", "yt", "https://www.youtube.com/results?search_query=%s"),
        engine("翻译", "译", "fy", "https://translate.google.com/?text=%s"),
        engine("地图", "图", "map", "https://www.amap.com/search?query=%s"),
        engine("维基词典", "词", "dict", "https://dict.youdao.com/result?word=%s&lang=en"),
    )

    /**
     * 还没被添加的预设：按 `urlTemplate` 判重（同一引擎换了名字 / 标识仍算已添加）。
     *
     * 不在这里判 trigger 冲突——[trigger] 只是预设，添加方若发现撞了会把标识清空，
     * 引擎本身照样可用（用户点开编辑页再改）。
     */
    fun addable(existing: List<SearchEngine>): List<SearchEngine> {
        val used = existing.mapTo(mutableSetOf()) { it.urlTemplate.trim() }
        return all.filter { it.urlTemplate.isNotBlank() && it.urlTemplate.trim() !in used }
    }
}
