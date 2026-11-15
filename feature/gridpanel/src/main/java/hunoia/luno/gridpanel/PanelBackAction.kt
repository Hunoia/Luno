package hunoia.luno.gridpanel

/**
 * 面板浮层的系统返回键落点。
 *
 * 优先级：拖拽中 → 取消拖拽；缩放手柄中 → 取消缩放；编辑态 → 退编辑；否则 → 关面板。
 *
 * 纯函数，便于单测。**调用方必须现读编辑态**（`editState.value`），不能传组合期快照：
 * 返回键处理器注册在 `LaunchedEffect(state)` 里，`state` 不随编辑态变化，闭包捕获的
 * 组合期 `editMode` 会停在 false —— 表现就是「编辑态按返回把面板关了」（ui-guidelines §7：
 * 手势/点击回调里现读状态，不用组合期快照）。
 */
enum class PanelBackAction {
    /** 编辑态整项拖拽中：返回 = 取消这次拖拽，不动编辑态、不关面板。 */
    CancelDrag,

    /** 编辑态缩放手柄中：返回 = 取消这次缩放。 */
    CancelResize,

    /** 编辑态（无在途拖拽/缩放）：返回 = 退出编辑态，面板保留。 */
    ExitEdit,

    /** 都不是：返回 = 关面板。 */
    Close,
}

internal fun panelBackAction(
    draggingId: String?,
    resizingId: String?,
    inEditMode: Boolean,
): PanelBackAction = when {
    draggingId != null -> PanelBackAction.CancelDrag
    resizingId != null -> PanelBackAction.CancelResize
    inEditMode -> PanelBackAction.ExitEdit
    else -> PanelBackAction.Close
}