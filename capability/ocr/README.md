# capability:ocr — 屏幕文字识别

PP-OCRv5（LiteRT `CompiledModel` + Qualcomm NPU dispatch）的本地离线识别。
方案与取舍见 [ADR-0005](../../docs/adr/0005-ocr-npu-model-management.md)，
host AOT 包的构建见 [findings/2026-10-30-ocr-aot-build.md](../../docs/findings/2026-10-30-ocr-aot-build.md)。

## 契约

- `OcrEngine.recognize(bitmap, scripts)`：suspend，返回 bitmap 像素坐标下的 `OcrBlock` 列表；
  已按脚本匹配度去重、按阅读顺序排序。
- `OcrText`：纯函数（合并 / 落点命中 / 区域命中 / 转文本），不依赖 Android，JVM 单测覆盖。
- `OcrScript`：`Chinese`（中英混排，默认）、`Korean`；定义在 `:core:domain` 的配置模型里。
- `PpOcrDetector` / `PpOcrRecognizer` / `PpOcrModelRunner`：det / rec / 模型生命周期。

## 实现要点

- 模型**不随 APK 打包**：用户导入 host AOT zip（SM8750 / HTP V79），
  落盘在 `filesDir/ocr_models/ppocrv5_sm8750_aot/`，由 `OcrModelStore` 校验
  `manifest.json` 与逐文件 SHA-256；未导入时识别直接返回空结果，不做隐式下载。
- 运行时优先 NPU；AOT 图是 NPU dispatch，加载失败按「无结果」处理（没有 CPU 兜底）。
- det 概率图是 0..0.5（int8 sigmoid 量化 scale=1/256），`PpOcrDetector.PROB_SCALE` 还原到 0..1；
  正 / 反两遍检测取并集，覆盖暗色模式与彩底白字。
- rec 用**原图**裁剪（不是 640 检测图）：宽高比超过 320/48 的行走切段识别，避免横向压扁。
- 不持有 Context / Bitmap；调用方负责裁剪与回收。

## 使用方

- `:feature:gesture` 的 `ScreenPickOverlay`：**仅框选菜单的 OCR 动作 / 图片文字**。
  长按取词走 a11y 字符级快照，不再调用 OCR（见 [ADR-0006](../../docs/adr/0006-screen-text-acquisition.md)）。
- 组装根 `:app`（`SideGestureService`）持有 `PpOcrEngine`，服务销毁时 `close()`；
  模型导入 / 删除后经 `DispatchClient.refreshOcrEngine()` 让 `:service` 重载并预热。
