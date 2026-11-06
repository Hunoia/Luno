package hunoia.luno.service;

import hunoia.luno.service.IDispatchCallback;

/** 跨进程分发口：主进程经 Binder 转发，分发仍只在 :service 的单 Dispatcher 执行。 */
interface IDispatchService {
    /**
     * 提交动作；返回 true = 已交给 :service 执行，最终三态经 callback 回推。
     * origin 为 DispatchOrigin 枚举名；idempotencyKey 原样透传。
     */
    boolean dispatchAction(String actionJson, String idempotencyKey, String origin, IDispatchCallback callback);
    /** 分发链就绪（无障碍服务存活且 coordinator 已初始化）。 */
    boolean isReady();
    /** 主进程删除面板后，让 :service 的 AppWidgetHost 回收未引用的 widget id。 */
    oneway void releaseOrphanAppWidgets(in int[] keepIds);

    /** OCR 模型导入/删除后，让 :service 丢弃旧引擎、重新加载并预热。 */
    oneway void refreshOcrEngine();
}
