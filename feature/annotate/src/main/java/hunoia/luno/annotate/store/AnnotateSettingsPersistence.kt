package hunoia.luno.annotate.store

import androidx.compose.runtime.snapshotFlow
import hunoia.luno.annotate.model.AnnotateDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 偏好落盘防抖：样式滑杆连续拖动只写最后一次。 */
private const val PERSIST_DEBOUNCE_MS = 250L

/**
 * 关闭时的最后一次落盘不等防抖，也不随宿主 scope 取消：
 * 否则「拖完样式/工具条立刻退出」会停在防抖窗口里丢掉最后一次修改。
 * 进程内常驻、任务短命；不持有宿主或文档引用。
 */
private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** 读取持久化偏好并应用到文档；没有 store / 读取失败都按默认值打开（不影响标注本身）。 */
suspend fun restoreAnnotateSettings(document: AnnotateDocument, store: AnnotateSettingsStore?) {
    val preferences = store?.let { runCatching { it.load() }.getOrNull() } ?: AnnotatePreferences()
    document.applySettings(styles = preferences.styles, anchor = preferences.anchor)
}

/** 立即把文档当前偏好写回 store（关闭 / 换底图时调用）。 */
fun flushAnnotateSettings(document: AnnotateDocument, store: AnnotateSettingsStore?) {
    if (store == null) return
    val preferences = AnnotatePreferences(
        styles = document.state.styles,
        anchor = document.state.toolbarAnchor,
    )
    flushScope.launch { runCatching { store.save(preferences) } }
}

/**
 * 把文档里的偏好变化写回 store，挂在宿主 scope 上随宿主/文档取消。
 *
 * 首次发射是打开时刚恢复的偏好，跳过不写；之后样式或工具条位置一变就防抖落盘。
 * scope 取消会中断防抖中的等待，因此关闭路径必须再调用一次 [flushAnnotateSettings]，
 * 否则防抖窗口内退出会丢掉最后一次修改。
 */
fun CoroutineScope.observeAnnotateSettings(
    document: AnnotateDocument,
    store: AnnotateSettingsStore?,
): Job? {
    if (store == null) return null
    return launch {
        snapshotFlow {
            AnnotatePreferences(
                styles = document.state.styles,
                anchor = document.state.toolbarAnchor,
            )
        }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest { preferences ->
                delay(PERSIST_DEBOUNCE_MS)
                withContext(NonCancellable) {
                    runCatching { store.save(preferences) }
                }
            }
    }
}
