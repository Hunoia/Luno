package hunoia.luno.quicklaunch.panel

/**
 * LazyColumn 的 item key 工具：不含 idx，只由内容决定。
 *
 * 之前每处 key 都拼了 `idx`（如 `"${class}-$idx-${title.hashCode()}"`），
 * 结果集一重排整列 key 全失效，LazyColumn 只能全部销毁重建（图标、文本测量、
 * 颜色动画全部重来），打字时肉眼可见闪一下。
 *
 * 这里的关键约束是「同一次组合内 key 必须唯一」：重复项的稳定串相同，
 * 单靠内容会撞 key（debug 直接抛 IllegalArgumentException）。所以重复出现的
 * 第 n 次追加 `~n`。非重复项 key 恒等于稳定串，重排/插入时身份不变。
 */
internal fun stableKeys(items: List<String>): List<String> {
    if (items.isEmpty()) return emptyList()
    val seen = HashMap<String, Int>(items.size)
    return items.map { base ->
        val n = seen.getOrDefault(base, 0)
        seen[base] = n + 1
        if (n == 0) base else "$base~$n"
    }
}

/**
 * 列表内容签名：首尾 + 数量。用于「内容变了才把列表钉回首项」。
 *
 * 不用整表 hashCode：剪贴行是全文（可能几十 KB），每帧算一遍比滚动本身还贵。
 * 误判方向是保守的（该滚没滚），下一帧内容再变时会补滚，不会「没变却抢滚动」。
 */
internal fun signatureOf(first: Any?, last: Any?, size: Int): Int =
    31 * (31 * (first?.hashCode() ?: 0) + (last?.hashCode() ?: 0)) + size
