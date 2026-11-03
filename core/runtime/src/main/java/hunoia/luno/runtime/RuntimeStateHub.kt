package hunoia.luno.runtime

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 最新一份可见性运行态。由触钮刷新链路写入，供其他常驻 Surface（悬浮球）读取。
 *
 * 只传递派生状态，不新增平台监听；写入方必须是 `:service` 的 GestureCoordinator。
 * 观察者按写入顺序在主线程回调。
 */
object RuntimeStateHub {

    @Volatile
    private var currentValue: GestureRuntimeState? = null

    private val observers = CopyOnWriteArrayList<(GestureRuntimeState) -> Unit>()

    fun current(): GestureRuntimeState? = currentValue

    fun update(state: GestureRuntimeState) {
        currentValue = state
        observers.forEach { observer -> runCatching { observer(state) } }
    }

    fun addObserver(observer: (GestureRuntimeState) -> Unit) {
        observers += observer
    }

    fun removeObserver(observer: (GestureRuntimeState) -> Unit) {
        observers -= observer
    }
}
