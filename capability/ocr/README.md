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
  不做隐式下载。包构建：`scripts/build_ocr_gpu.py`（`docs/ppocrv` 的 HF tflite + 字典），
  `--rec 320,640` 可在一个包里放多档 rec（默认 320+640），也支持单档 `--rec 960`。
- 多档 rec 动态选择：每行按 `行宽 × 模型高 ≤ 模型宽 × 行高` 选“刚好放得下”的最小档，
  短行 320、宽行自动升 640/960，三档都放不下才用最宽档切段。运行时最多常驻两档模型
  （LRU 换入换出，避免三档同时占显存）；单档包行为与以前一致。
- 运行时 GPU delegate 优先（`Accelerator.GPU + CPU`）；GPU 首次加载有设备端编译 warmup，
  delegate 不可用自动回落 CPU。
- v6 det 输出为 fp32 sigmoid（0..1），无需 v5 int8 的概率还原系数。
- 正 / 反两遍检测取并集，覆盖暗色模式与彩底白字。
- rec 用**原图**裁剪（不是 640 检测图）：宽高比超过 `模型输入宽/高` 的行走切段识别，
  避免横向压扁。rec 输入宽度由模型自己读（文件名 `ppocrv6_small_rec_<宽度>_fp32.tflite`），
  切段与字符坐标映射都跟着选中的那一档走。
- 切段拼接去重（宽行边界少字/错字的根因，2026-10-31 修）：相邻段重叠 2 个字高；
  后段丢弃中心落在开头 `实际重叠 - 0.65 * 字高` 内的字符（被丢的字一定在上一段里完整可见），
  非末段再丢弃结尾 `0.65 * 字高`（只有半截字，由下一段完整识别）。两条合起来让相邻段的
  保留区间严格首尾相接，不重复也不丢字。前缀丢弃按**实际重叠**算，不能按计划重叠——
  均分步进后实际重叠常更大，按计划值丢会让多出来的重叠区被两段各出一次字。
- CTC 字典为统一 50 语言 `characters.json`（index 0 = blank）。
- 不持有 Context / Bitmap；调用方负责裁剪与回收。

## 使用方

- `:feature:gesture` 的 `ScreenPickOverlay`：**仅框选菜单的 OCR 动作 / 图片文字**。
  长按取词走 a11y 字符级快照，不再调用 OCR（见 [ADR-0006](../../docs/adr/0006-screen-text-acquisition.md)）。
- 组装根 `:app`（`SideGestureService`）持有 `PpOcrEngine`，服务销毁时 `close()`；
  模型导入 / 删除后经 `DispatchClient.refreshOcrEngine()` 让 `:service` 重载并预热。
