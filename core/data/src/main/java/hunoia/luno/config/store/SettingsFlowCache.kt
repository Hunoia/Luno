package hunoia.luno.config.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach

/**
 * 设置流的「最近一次真实值」进程内缓存。
 *
 * settings 页的兜底值**不能用空默认**（`ActionSettings()` 之类）：`collectAsStateWithLifecycle`
 * 的值持有者是普通 `remember`，返回上一页那一帧（组合重挂，或生命周期落到 STARTED 以下）会复位成
 * 兜底值。空默认会让「按条件渲染的 item」整组消失，`LazyColumn` 的偏移被钳住——表现为从二级页
 * 返回后整页跳顶 / 跳底（ui-guidelines §3.2 第 6 条；同族先例：`HabitEditScreen` 的空值初始化、
 * `AppControlVM.refresh` 的「不切 LoadingState 才不跳顶」）。
 *
 * 用法：门面把流包进 [observe]，页面兜底改用 [latest]。冷启动首帧仍是空默认，那时列表在顶部，
 * 不会被钳住。
 */
class SettingsFlowCache<T : Any>(private val fallback: () -> T) {

    private val latest = MutableStateFlow<T?>(null)

    /** 挂在门面暴露的流上；同一流只包一次。 */
    fun observe(flow: Flow<T>): Flow<T> = flow.onEach { latest.value = it }

    /** 真实值兜底：还没拿到过真值时回落 [fallback]。 */
    fun latest(): T = latest.value ?: fallback()

    /** 写盘后主动对齐缓存：`updateData` 返回时 DataStore 内存值已是新值，但流回推是异步的。 */
    fun publish(value: T) {
        latest.value = value
    }
}
