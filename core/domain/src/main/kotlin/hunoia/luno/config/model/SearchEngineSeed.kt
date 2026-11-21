package hunoia.luno.config.model

/**
 * 首装默认引擎（seed）：全新安装 / 恢复出厂时写进引擎列表的起步三条。
 *
 * 只作为 [QuickSearchSettings.customEngines] 的**字段默认值**存在——DataStore 整对象
 * 序列化，默认值仅在首次解码生效；用户删空引擎会持久化为空列表，不会被重新 seed。
 * 与旧「常用引擎预设」的区别：这里没有「逐条添加」的入口与判重流程，seed 一旦落库
 * 就是普通用户引擎，可删可改；想要更多引擎走编辑页新建或 JSON 导入。
 *
 * 顺序即主引擎优先级：首位（必应）= 主引擎。
 */
val DefaultSearchEngines: List<SearchEngine> = listOf(
    SearchEngine(
        name = "必应",
        trigger = "bing",
        urlTemplate = "https://www.bing.com/search?q=%s",
        launch = SearchLaunch.Url("https://www.bing.com/search?q=%s"),
        iconText = "必",
    ),
    SearchEngine(
        name = "谷歌",
        trigger = "google",
        urlTemplate = "https://www.google.com/search?q=%s",
        launch = SearchLaunch.Url("https://www.google.com/search?q=%s"),
        iconText = "G",
    ),
    SearchEngine(
        name = "DuckDuckGo",
        trigger = "ddg",
        urlTemplate = "https://duckduckgo.com/?q=%s",
        launch = SearchLaunch.Url("https://duckduckgo.com/?q=%s"),
        iconText = "D",
    ),
)
