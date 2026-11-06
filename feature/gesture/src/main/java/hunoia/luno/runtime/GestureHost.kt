package hunoia.luno.runtime

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import hunoia.luno.runtime.pointer.PointerOverlayHost

/** 指针窗口宿主是 [GestureHost] 的子集（context + 生命周期三件套），直接继承。 */
interface GestureHost : hunoia.luno.runtime.RuntimeHost, PointerOverlayHost {

    val runtimePanelOverlay: RuntimePanelOverlay

    fun nowInLauncher(): Boolean
    fun requestEnableDisabledPackage(packageName: String, onResult: (Boolean) -> Unit)
    fun getCurrentPackageName(): String


    /** 供 overlay 等场景直接执行一个已构造好的动作；key 由意图产生方提供，转发层不补键。 */
    fun dispatchAction(action: StoredAction, idempotencyKey: String? = null)
}
