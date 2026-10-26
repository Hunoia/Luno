package hunoia.luno.service;

/** 跨进程分发口：主进程经 Binder 转发，分发仍只在 :service 的单 Dispatcher 执行。 */
import hunoia.luno.service.IPointPickCallback;
import hunoia.luno.service.IRuleTestCallback;

interface IDispatchService {
    /** actionJson 为 StoredAction 的 JSON。返回 true = 已交分发器，false = 服务未就绪。 */
    boolean dispatchAction(String actionJson);
    /** 分发链就绪（无障碍服务存活且 coordinator 已初始化）。 */
    boolean isReady();
    /** 打开屏幕取点 overlay（窗口由 :service 的无障碍服务展示），结果经回调送回主进程。 */
    oneway void showPointPicker(int initialXDp, int initialYdp, IPointPickCallback cb);

    /** 主进程删除面板后，让 :service 的 AppWidgetHost 回收未引用的 widget id。 */
    oneway void releaseOrphanAppWidgets(in int[] keepIds);

    /** 运行中的规则 id JSON 数组（规则列表页展示「运行中/停止」）。 */
    String runningRuleIds();

    /** 手动停止一条规则的所有实例；返回是否真的有实例被取消。 */
    boolean stopRule(String ruleId);

    /** 测试运行一条规则的步骤；ruleJson 为 AutomationRule 的 JSON。executeSteps=false 只检查条件。 */
    boolean runRuleTest(String ruleJson, boolean executeSteps, IRuleTestCallback cb);
}
