# capability:ocr — 屏幕文字识别

PP-OCRv6（LiteRT `CompiledModel` + GPU delegate / OpenCL）的本地离线识别。
NPU（QNN / HTP）方案因社区反馈 v6 算子覆盖问题弃用，切换为 GPU 运行时 delegate
（历史方案见 [ADR-0005](../../docs/adr/0005-ocr-npu-model-management.md)）。

## 契约

- `OcrEngine.recognize(bitmap, scripts)`：suspend，返回 bitmap 像素坐标下的 `OcrBlock` 列表；
  已去重、按阅读顺序排序。
- `OcrText`：纯函数（合并 / 落点命中 / 区域命中 / 转文本），不依赖 Android，JVM 单测覆盖。
- `OcrScript`：定义在 `:core:domain` 的配置模型里；v6 单模型覆盖全部脚本，请求的脚本
  仅作为结果块标签。
- `PpOcrDetector` / `PpOcrRecognizer` / `PpOcrModelRunner`：det / rec / 模型生命周期。

## 实现要点

- 模型**不随 APK 打包**：用户导入 GPU zip（原始 fp32 tflite + `characters.json`），
  落盘在 `filesDir/ocr_models/ppocrv6_gpu/`，由 `OcrModelStore` 校验
  `manifest.json`（`kind="gpu"`）与逐文件 SHA-256；未导入时识别直接返回空结果，
  不做隐式下载。包构建：`scripts/build_ocr_gpu.py`（`docs/ppocrv` 的 HF tflite + 字典）。
- 运行时 GPU delegate 优先（`Accelerator.GPU + CPU`）；GPU 首次加载有设备端编译 warmup，
  delegate 不可用自动回落 CPU。
- v6 det 输出为 fp32 sigmoid（0..1），无需 v5 int8 的概率还原系数。
- 正 / 反两遍检测取并集，覆盖暗色模式与彩底白字。
- rec 用**原图**裁剪（不是 640 检测图）：宽高比超过 320/48 的行走切段识别，避免横向压扁；
  CTC 字典为统一 50 语言 `characters.json`（index 0 = blank）。
- 不持有 Context / Bitmap；调用方负责裁剪与回收。

## 使用方

- `:feature:gesture` 的 `ScreenPickOverlay`：**仅框选菜单的 OCR 动作 / 图片文字**。
  长按取词走 a11y 字符级快照，不再调用 OCR（见 [ADR-0006](../../docs/adr/0006-screen-text-acquisition.md)）。
- 组装根 `:app`（`SideGestureService`）持有 `PpOcrEngine`，服务销毁时 `close()`；
  模型导入 / 删除后经 `DispatchClient.refreshOcrEngine()` 让 `:service` 重载并预热。
