package hunoia.luno.gridpanel.geometry

/**
 * 循环 pager 的高位锚点索引：满足 `index % pageCount == real`。
 *
 * 面板的 pager 页数恒为极大值（`Int.MAX_VALUE`），真实页 = `index % 有效页数`。
 * 「第 0 页」因此不是一个索引而是一族等价索引；取高位锚点是为了两侧都留出翻页空间
 * （落到 0..n-1 会丢掉向左的循环空间）。
 *
 * 同一算法同时用于：面板打开时的初始页、切换面板后的重置。两处必须一致，
 * 否则切到页数不同的面板会沿用上一块的索引，落在任意页（「切换后不在首页」）。
 */
fun loopAnchorIndex(pageCount: Int, real: Int = 0): Int {
    if (pageCount <= 0) return real
    val base = 1_000_000 - 1_000_000 % pageCount
    return base + real
}
