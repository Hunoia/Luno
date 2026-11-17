package hunoia.luno.core.domain

/**
 * 文本注入结果（[hunoia.luno.runtime.surface.GestureCommand] 端口签名用）。
 *
 * 上提自 `:feature:actions` 的 `TextInserter.Result`：运行时端口不能反向依赖 feature，
 * 纯结果枚举归契约层。`TextInserter` 以 `typealias Result = TextInsertResult` 保留原写法。
 */
enum class TextInsertResult {
    /** 已直接写进输入框（没碰剪贴板）。 */
    Inserted,

    /** 目标只支持粘贴，已用剪贴板写入。 */
    InsertedViaClipboard,

    /** 落点下没有可编辑节点。 */
    NoTarget,

    /** 找到了输入框但写入失败。 */
    Failed,
}

/**
 * 图片粘贴结果（[hunoia.luno.runtime.surface.GestureCommand] 端口签名用）。
 *
 * 上提自 `:feature:actions` 的 `ImageInserter.Result`；`ImageInserter` 以
 * `typealias Result = ImageInsertResult` 保留原写法。
 */
enum class ImageInsertResult {
    Pasted,
    NoTarget,
    Failed,
}
