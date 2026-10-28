package hunoia.luno.service;

/** 编辑页「测试运行」结果回调：resultJson 为 RuleTestResult 的 JSON。 */
oneway interface IRuleTestCallback {
    void onResult(String resultJson);
}
