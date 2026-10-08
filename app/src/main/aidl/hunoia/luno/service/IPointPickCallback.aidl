package hunoia.luno.service;

/** 屏幕取点结果回调（方向：:service 进程 → 主进程）。 */
interface IPointPickCallback {
    /** 取到的 dp 坐标（执行时按当时 density 转 px）。 */
    oneway void onCommit(int xDp, int yDp);
    oneway void onDismiss();
}
