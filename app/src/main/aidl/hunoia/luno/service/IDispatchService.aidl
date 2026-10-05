package hunoia.luno.service;

/** 跨进程分发口：主进程经 Binder 转发，分发仍只在 :service 的单 Dispatcher 执行。 */
interface IDispatchService {
    /** actionJson 为 StoredAction 的 JSON。返回 true = 已交分发器，false = 服务未就绪。 */
    boolean dispatchAction(String actionJson);
    /** 分发链就绪（无障碍服务存活且 coordinator 已初始化）。 */
    boolean isReady();
}
