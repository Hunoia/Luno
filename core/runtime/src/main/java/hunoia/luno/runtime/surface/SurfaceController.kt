package hunoia.luno.runtime.surface

/** Surface 分类：生命周期控制器按类批量操作，不感知具体窗口实现。 */
enum class SurfaceKind {
    PANEL,
    FLOAT_BALL,
    TRANSIENT,
    BUTTON,
}

/**
 * 一个已挂载的 Surface。宿主负责实现窗口动作，控制器只负责聚合与批量调度。
 */
interface SurfaceHandle {
    val surfaceId: String
    val surfaceKind: SurfaceKind

    /** 短态隐藏：窗口保留、不可见不可点。 */
    fun softHide()

    /** 从短态恢复：免重建。 */
    fun restore()

    /** 长态关闭：移除窗口并释放资源。 */
    fun detach()
}

/**
 * 进程级 Surface 控制器：注册已挂载的 Surface，提供按类批量收起 / 恢复 / 销毁。
 *
 * 具体窗口挂摘仍归各 Surface；控制器不碰生命周期回调，只做聚合与唯一性校验。
 */
object SurfaceController {

    private val handles = LinkedHashMap<String, SurfaceHandle>()

    fun register(handle: SurfaceHandle) {
        require(handle.surfaceId.isNotBlank()) { "surface id must not be blank" }
        require(!handles.containsKey(handle.surfaceId)) {
            "duplicate surface: ${handle.surfaceId}"
        }
        handles[handle.surfaceId] = handle
    }

    fun unregister(surfaceId: String) {
        handles.remove(surfaceId)
    }

    fun softHideAll(kind: SurfaceKind? = null) {
        snapshot(kind).forEach { it.softHide() }
    }

    fun restoreAll(kind: SurfaceKind? = null) {
        snapshot(kind).forEach { it.restore() }
    }

    /** 长态关闭；先取快照再 detach，避免实现里 unregister 修改迭代中的集合。 */
    fun detachAll(kind: SurfaceKind? = null) {
        snapshot(kind).forEach { it.detach() }
    }

    fun activeIds(kind: SurfaceKind? = null): Set<String> =
        snapshot(kind).mapTo(LinkedHashSet()) { it.surfaceId }

    private fun snapshot(kind: SurfaceKind?): List<SurfaceHandle> =
        handles.values.filter { kind == null || it.surfaceKind == kind }
}
