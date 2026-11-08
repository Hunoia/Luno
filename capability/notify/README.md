# capability:notify — 通知呈现（超级岛 / 实时 / 标准）

提醒类通知的统一呈现能力。契约在 `:core:domain`（`NotificationPresenter` /
`IslandNotificationSpec` / `IslandPublishResult`），本模块只提供默认实现；绕过机制的
否决路线与最终结论见 [docs/archive.md](../../docs/archive.md) §13。

## 契约

- `NotificationPresenter.probeIslandCapability()`：纯读探测，返回 `READY` / `FALLBACK` /
  `OTHER_ANDROID`，进程内缓存。
- `NotificationPresenter.publishIsland(spec)`：suspend，执行「切 XMSF → 焦点通知 →
  校验窗口 → 恢复」原子事务；返回 `Posted` / `Rejected` / `Unsupported` / `Failed`。
  调用方按结果执行回退链（LIVE → STANDARD），内容与通道始终归调用方。
- `IslandNotificationSpec`：通道、通知 id、标题 / 正文 / 胶囊摘要、展开与超时、强调色、
  点击 Intent URI、最多 2 个动作。PendingIntent 不进契约层，由发布器按
  `IslandIntentKind`（ACTIVITY / BROADCAST）构建。

## 实现要点

- **能力条件**：Xiaomi + `notification_focus_protocol >= 3` + `persist.sys.feature.island`
  + `com.xiaomi.xmsf` 可见（`IslandProbe`）。
- **绕过**：`XmsfGate` 经 `:core:bridge` 的 `ShizukuManager.executeShell` 执行
  `cmd connectivity`，用 OEM_DENY_3 防火墙链切断 XMSF 网络，使 SystemUI 的焦点通知鉴权
  失败放行；事务结束恢复。aurogon 规则已实测无效（见 archive.md §13），不再是路径。
- **串行化**：`publishIsland` 同实例用 `Mutex`，跨实例（habit / backup / 设置页各持一个
  presenter）由 `IslandPublisher` 的进程锁兜底；同一时刻最多一个绕过事务。
- **payload 约束**（调用方无感，构造内强制）：`miui.focus.param` ≤ 3072 字节；布尔必须是
  JSON 布尔（protocol=3 严格反序列化）；文本 HTML 转义；动作最多 2 个；超时上限
  12 小时 / 720 分钟。
- **前台不建岛**：应用在前台时，SystemUI 对焦点通知连小岛胶囊都不给（2026-12 真机验证）。
  首帧浮出判定发生在**投递瞬间**，所以「发送测试通知」必须**先切走再投递**，否则永远看不到
  展开效果（生产提醒都在后台发，没这个问题）。
- **浮出开关必须同值**：`param_v2.enableFloat` 与 `islandFirstFloat`（以及 extras 的
  `miui.enableFloat` / `miui.island.firstFloat`）都写成 `spec.autoExpand`。历史上把
  `enableFloat` 恒写 true、只让 `islandFirstFloat` 跟随开关，结果是「自动展开开关拨了
  没反应」。`enableFloat=false` 时小岛胶囊照常出现（真机复核，旧结论「首发 noFloat 就
  不出胶囊」不成立）。
- **首帧展开时长**：`param_island.expandedTime` 随 `autoExpand` 给 5 秒，0 = 不展开；
  到时大岛自动收回胶囊（真机验证）。
- **动作形态**：`textButton[i]` 用 `type=2`（文字按钮）+ `actionIntentType=1` + `action`
  键；对应 `miui.focus.actions` Bundle 的值必须是 `Notification.Action`（含有效 Icon）。
  **裸 PendingIntent 不行**：按钮能渲染，点击零反应（无广播、无异常日志）。
- **展开态主卡**：只用 `iconTextInfo`（左侧图标 + 标题 + 正文）。旧版 `baseInfo{type:1}`
  在通知栏可用但大岛拿不到标准图文组件；`param_v2.picInfo`（右上角小图标）也去掉了——
  左侧已经有图标，右侧重复同一个 logo 只是噪音。
- `islandProperty` 有按钮用 2、纯信息用 1；`isShowNotification` 由调用方按
  `islandKeepShadeNotification` 给（关掉 = 只上岛、通知栏不留条目）。
- **可选呈现**（都在 `IslandNotificationSpec` 上，由通用通知设置驱动）：
  `outerGlow` → `outEffectSrc=outer_glow`；`expandedSeconds` → `expandedTime`（1..60 clamp）；
  `share` → `param_island.shareData{title,content,shareContent}`（长按岛卡片拖拽分享，
  三段都是纯文本，`shareContent` 是最终插入聊天框的文本）。
- **断网时长可配**：`spec.blockWindowMs`（设置页「断网时间」，默认 100ms，clamp 50..2000）。
  事务是「block → notify → 睡 blockWindowMs → **先恢复网络** → 再等 120ms 校验」：
  断网只服务 SystemUI 的鉴权失败放行，恢复后再看有没有被撤回，因此短断网不会误判成
  Rejected，也不会把被撤回的岛当成 Posted。
- **鉴权预热**：`prewarmIslandAuth(channelId)` 用同一绕过事务投一条最小焦点通知再撤掉，
  让 SystemUI 缓存本包鉴权结果；首次真实投递因此不必等 XMSF 回包。通道不存在时跳过
  （通道归调用方创建）。`:app` 在 `MainActivity` 启动时按「配置里是否会用到岛」触发一次。
- **通道悬浮探测（不可依赖）**：HyperOS 4.0 的 `NotificationChannel` 上**没有**
  `canShowFloat()`（实测 101 个字段全是 AOSP 的，无 float 相关方法/字段；部分 ROM 才有），
  所以 `canChannelFloat()` 实际只会退化成 `importance ≥ HIGH`。真值（渠道「悬浮通知」开关）
  不下发到应用侧。**只做诊断，不参与决策**：拿它否决 `islandAutoExpand` 会把用户开关弄死。
- **常驻 / 一次性**：spec 的 `ongoing` 默认 true（习惯提醒）；备份失败这类一次性事件传
  false，发布器同时设 `setAutoCancel` 与 `setTimeoutAfter`，岛与通知栏一起收走。
- 图标使用应用图标（v1 不接外部位图）。

## 使用方

- `HabitReminderNotifier`（`:app`）：HABIT，两个 broadcast 动作——「完成」（打卡，幂等）、
  「推迟 N 分钟」（N 取自习惯设置，收起当前提醒并挂一次性闹钟，到点重算状态再弹；
  已打卡 / 休整日则静默收掉）。
- `AutoBackupNotifier`（`:app`）：BACKUP，一次性短岛，无动作。
- `NotificationSettingsScreen`（`:app`）：呈现模式、岛细节（自动展开 / 停留时长 / 外发光 /
  通知栏 / 拖拽分享 / 超时）、「发送测试通知」与权限状态。

Toast 不在此模块：系统 Toast 是平台原语，唯一入口在 `:core:bridge`
（`bridge.ui.showToast` / `showToastLong`），归位理由见 `docs/archive.md` §14。

## 调试

debug 构建的 `IslandProbeReceiver` 是 go/no-go 入口（含真实发布事务）：

```bash
adb shell am broadcast -n hunoia.luno.debug/hunoia.luno.domain.habit.IslandProbeReceiver \
    -a hunoia.luno.debug.ISLAND_PROBE --ez island true [--ei window_ms 220] [--ez expand true]
```

日志 tag：`NotifyPresenter`（判定链 / 结果）、`XmsfGate`（防火墙链与恢复）、
`IslandProbe`（能力条件）。

探针的可选参数：`--ez expand true`（首帧展开大岛）、`--ei id 9012`（换通知 id，避免重复
投递走「更新」路径而看不到首帧行为）、`--es channel habit_reminder`（默认）。

另有两个调试入口（接收器 `exported=false`，adb 发不进非导出广播，只能走探针）：
`--ez remind true [--es habit_id <uuid>]` 跑真实习惯提醒链路（真实状态 / 双按钮），
`--ez done true` 模拟点「完成」（测试习惯 id，验证收起 + toast）。
