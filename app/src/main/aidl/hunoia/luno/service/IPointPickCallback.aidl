package hunoia.luno.service;

/** 屏幕取点结果回调（方向：:service 进程 → 主进程）。 */
interface IPointPickCallback {
    /** 取到的 dp 坐标（执行时按当时 density 转 px）。 */
    oneway void onCommit(int xDp, int yDp);
    oneway void onDismiss();
    /** 取点服务不可用（无障碍服务未运行 / overlay 添加失败），调用方应提示而不是静默。 */
    oneway void onUnavailable();
}
