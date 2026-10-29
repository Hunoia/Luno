# capability:ocr — 屏幕文字识别

ML Kit（bundled）文本识别的默认实现。方案与取舍见
[docs/ocr-mlkit-plan-2026-10-29.md](../../docs/ocr-mlkit-plan-2026-10-29.md)。

## 契约

- `OcrEngine.recognize(bitmap, scripts)`：suspend，返回 bitmap 像素坐标下的 `OcrBlock` 列表；
  已按脚本匹配度去重、按阅读顺序排序。
- `OcrText`：纯函数（合并 / 落点命中 / 区域命中 / 转文本），不依赖 Android，JVM 单测覆盖。
- `OcrScript`：`Chinese`（中英）、`Japanese`、`Korean`；定义在 `:core:domain` 的配置模型里。

## 实现要点

- 三个模型 AAR 全部打包（约 6.5 MB），离线可用，不依赖 GMS 模型下载。
- 识别器懒加载、按脚本缓存；`close()` 释放。
- 首次识别前显式 `MlKit.initialize(appContext)`：识屏在 `:service` 独立进程，
  ContentProvider 自动初始化在该进程不可靠（实测会报 `MlKitContext has not been initialized`）。
- 多脚本并行识别，`Mutex` 串行化整组调用。
- 不持有 Context / Bitmap；调用方负责裁剪与回收。

## 使用方

- `:feature:gesture` 的 `ScreenPickOverlay`：长按无节点文字、图片内文字、框选文字。
- 组装根 `:app`（`SideGestureService`）注入 `MlKitOcrEngine`，服务销毁时 `close()`。
