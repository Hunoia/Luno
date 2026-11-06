package hunoia.luno.service;

/** 分发落定回调：:service 执行结束后回推三态（主进程不阻塞等待）。 */
oneway interface IDispatchCallback {
    /** status: 0=Success, 1=Failed, 2=RequiresCapability；reason/capability 为枚举序号，-1=无。 */
    void onSettled(String typeId, int status, int reason, int capability, String message, String userMessage);
}
