package hunoia.luno.quickaccess.query

/**
 * 剪贴板搜索池：固定项（源内置顶）与普通历史（新→旧）。
 *
 * 由 UI 层从 `ClipboardCenter.history` 投影后传入，搜索层不直接依赖 DataStore；
 * 这样面板删/固定一条，搜索看到的池立刻同步，不再有第二份剪贴板历史。
 */
data class ClipPools(
    val pinned: List<String> = emptyList(),
    val history: List<String> = emptyList(),
)
