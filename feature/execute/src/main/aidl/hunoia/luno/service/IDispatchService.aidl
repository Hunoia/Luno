package hunoia.luno.service;

/** 跨进程分发口：主进程经 Binder 转发，分发仍只在 :service 的单 Dispatcher 执行。 */
interface IDispatchService {
    /** actionJson 为 StoredAction 的 JSON。返回 true = 已交分发器，false = 服务未就绪。 */
    boolean dispatchAction(String actionJson);
    /** 分发链就绪（无障碍服务存活且 coordinator 已初始化）。 */
    boolean isReady();
    /** 主进程删除面板后，让 :service 的 AppWidgetHost 回收未引用的 widget id。 */
    oneway void releaseOrphanAppWidgets(in int[] keepIds);

    /** OCR 模型导入/删除后，让 :service 丢弃旧引擎、重新加载并预热。 */
    oneway void refreshOcrEngine();
}
