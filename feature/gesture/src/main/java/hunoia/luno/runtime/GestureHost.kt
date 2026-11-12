package hunoia.luno.runtime

import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import hunoia.luno.runtime.pointer.PointerOverlayHost

/** 指针窗口宿主是 [GestureHost] 的子集（context + 生命周期三件套），直接继承。 */
interface GestureHost : hunoia.luno.runtime.RuntimeHost, PointerOverlayHost {

    val runtimePanelOverlay: RuntimePanelOverlay

    fun nowInLauncher(): Boolean
    fun requestEnableDisabledPackage(packageName: String, onResult: (Boolean) -> Unit)
    fun getCurrentPackageName(): String

    /**
     * 严格解析「当前应用」：最上层可见 `TYPE_APPLICATION` 窗口的包名。
     * 桌面 / 无应用窗口返回空串（不兜底最近应用）；Luno 自己的 Activity 算 Luno。
     */
    fun getCurrentAppForWindow(): String


    /** 供 overlay 等场景直接执行一个已构造好的动作；key 由意图产生方提供，转发层不补键。 */
    fun dispatchAction(
        action: StoredAction,
        idempotencyKey: String? = null,
        origin: DispatchOrigin = DispatchOrigin.PANEL,
        onSettled: ((typeId: String, result: ActionResult) -> Unit)? = null,
    )
}
