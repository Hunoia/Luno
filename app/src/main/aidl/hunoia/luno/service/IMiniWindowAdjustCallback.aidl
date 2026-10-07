package hunoia.luno.service;

/** 小窗调整结果回调（方向：:service 进程 → 主进程）。 */
interface IMiniWindowAdjustCallback {
    /** resultJson 为 MiniWindowSettings 的 JSON。 */
    oneway void onCommit(String resultJson);
    oneway void onDismiss();
}
