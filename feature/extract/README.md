# feature:extract — 按压取词

长按落点取文字并弹结果浮层的能力模块。沿用 ADR-0003 §5 的后续规划：复用同一套
触摸流 / 拦截窗 / 注入原语，不另起炉灶。

## 定位与不变量

- **零提权优先**：默认走无障碍节点命中（`A11yExtractBackend`），不引入新权限 / 系统能力。
- **结果走轻量浮层，不静默改剪贴板**：取到的文字只展示在 `ExtractResultOverlay`，
  由用户点「复制 / 搜索 / 分享」才动作（架构 §7 / ADR-0003 §5）。
- **不复用 SelectToSpeak**：其底层与本项目自动化 / 读屏争抢同一套 a11y 会话，
  MIUI 上同 TIC 踩雷（见 ADR-0003 替代方案）。

## 扩展点

| 扩展点 | 落位 | 说明 |
|---|---|---|
| `ExtractBackend` | `backend/` | 一种「坐标 → 文字」实现；a11y 是一等实现，OCR 是占位兜底 |
| `ExtractStrategy` | `ExtractStrategy.kt` | 取词判定策略：按可用性选后端，输出统一 `ExtractResult` |
| `ExtractActions` | `ExtractActions.kt` | 结果浮层上的用户动作出口（复制 / 搜索 / 分享），落点实现在 `:app` |
| Surface（结果浮层） | `overlay/ExtractResultOverlay.kt` | transient overlay，自管理、不进 SurfaceController |

新增后端 = 实现 `ExtractBackend` 并在 `ExtractController` 的 `backends` 列表里注册，不改中央分发。

## 触发

- 触发复用 `:feature:gesture`：用户把一个触钮的「长按」动作设为「按压取词」
  （`internal.extract`，已进 `InternalActionGroup` 目录，动作选择页可直接选）。
- 长按命中后 `GestureCoordinator.onAction` 拦截 `internal.extract`，改走
  `SurfaceHost.showExtract(x, y)`（架构允许的 Surface 副作用端口），不再进 `Dispatcher`。
- 坐标即长按落点；来源包名由 a11y 根窗口提供，天然可做白/黑名单过滤。

## 后端现状

- `A11yExtractBackend`：DFS 命中坐标处「最深、带文字」的节点，返回整段文字
  （非严格分词；长按选区展开留待后续）。
- `OcrExtractBackend`：`isAvailable()` 恒 false，走 `Unsupported`。接入 ML Kit 时
  在此拿屏幕截图喂识别器，回填 `ExtractResult.Text.screenshotPath`。

## 依赖

仅 `core:common / core:domain / core:bridge / core:runtime / ui:design`，不反向依赖
`:app` 或 `:feature:gesture`（取词模块自身不含窗口/手势类型，宿主经 `RuntimeHost` 注入）。
