package hunoia.luno.extract

/**
 * 取词结果浮层上的用户动作出口。
 *
 * 取词模块只负责「取」与「呈现」，不负责「复制/搜索/分享」的具体实现——
 * 落点实现在 `:app`（剪贴板控制器、快速搜索、系统分享），取词模块不反向依赖它们。
 */
interface ExtractActions {
    fun copy(text: String)
    fun search(text: String)
    fun share(text: String)
}
