package hunoia.luno.picker

import androidx.annotation.Keep
import hunoia.luno.config.model.GestureTriggerType
import kotlinx.serialization.Serializable

/** 动作选择器的输入：要配哪个动作位，或只做一次挑选。 */
@Keep
@Serializable
data class ActionSelect(
    val gestureButtonId: String = "",
    /** 方向槽序号（0..count-1）；点按 / 长按类动作不使用。 */
    val slot: Int = 0,
    /** 进入时的方向文案（罗盘名 + 角度），只用于标题展示。 */
    val directionLabel: String = "",
    val triggerType: GestureTriggerType = GestureTriggerType.Slide,
    val subGestureId: String = "",
    /** 非空 = 在配识别区里的这条触发。 */
    val globalGestureTriggerId: String = "",
    /** 非空 = 本次不配触钮/子手势，而是在这个动作面板里挑一个面板项。 */
    val actionPanelId: String = "",
    /** true = 只选动作并复制其 Deep Link，不写入任何配置。 */
    val copyDeepLink: Boolean = false,
    /**
     * >=0 = 在改 `actionPanelId` 面板里第 N 项的**长按变体**，否则是新增面板项。
     * 长按变体是面板专属机制：面板自带 500ms 长按判定，见 ActionPanelState。
     */
    val actionPanelItemIndex: Int = -1,
    /** 非空 = 配网格面板：加项只进不出，想换动作就删了重加，所以没有「改已有项」的分支。 */
    val gridPanelId: String = "",
    /** 加项落到的页（派生页数可为最大页 +1，即编辑态虚页，落项即建页）。 */
    val gridPanelPageIndex: Int = 0,
    /**
     * 非空 = 网格面板里要**原位替换**的项 id（如习惯部件删后重选）：
     * 落盘只改该项的 kind/params，保留位置与 span；找不到则回退新增。
     */
    val gridPanelReplaceItemId: String = "",
    /** 非空 = 在配悬浮球的这个槽（见 [FloatBallSlot]），与上面各 id 字段互斥。 */
    val floatBallSlot: String = "",
)

/** 悬浮球的动作槽。只有「单击」一个：滑动固定读面板引用，长按固定是拖球。 */
object FloatBallSlot {
    const val TAP = "tap"

    fun isValid(slot: String): Boolean = slot == TAP
}
