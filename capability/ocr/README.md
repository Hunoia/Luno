# capability:ocr — 屏幕文字识别

PP-OCRv6（LiteRT `CompiledModel` + GPU delegate / OpenCL）的本地离线识别。
NPU（QNN / HTP）方案因社区反馈 v6 算子覆盖问题弃用，切换为 GPU 运行时 delegate
（历史方案见 [architecture.md](../../docs/architecture.md) §9.4）。

## 契约

- `OcrEngine.recognize(bitmap, scripts)`：suspend，返回 bitmap 像素坐标下的 `OcrBlock` 列表；
  已去重、按阅读顺序排序。
- `OcrText`：纯函数（合并 / 落点命中 / 区域命中 / 转文本），不依赖 Android，JVM 单测覆盖。
- `OcrScript`：定义在 `:core:domain` 的配置模型里；v6 单模型覆盖全部脚本，请求的脚本
  仅作为结果块标签。
- `PpOcrDetector` / `PpOcrRecognizer` / `PpOcrModelRunner`：det / rec / 模型生命周期。

## 实现要点

- 模型**随 APK 打包**：det + rec640 + `characters.json`（约 34MB）落在 `:app` 的
  `assets/ppocrv6_gpu/`，由 `BundledOcrModel` 列出固定清单，`PpOcrModelRunner` 经
  `CompiledModel.create(assetManager, …)` 资产直载（tflite 需 `noCompress` 才能 mmap），
  不落盘、不校验、不联网；模型二进制不入 git，检出后先跑 `./gradlew syncOcrModels`
  （从 `docs/ppocrv` 同步）。来源与许可（Apache-2.0）见 `assets/ppocrv6_gpu/NOTICE.md`。
- 单档 rec：内置 640。短行也跑 640 宽（右补白，约 2× 320 档计算量，但 rec 只占管线
  小头），换取宽行不切段、常驻仅 1 份模型。`RecModelChoice` 按行宽自动选档的逻辑
  保留，单档时恒选 640。
- 运行时 GPU delegate 优先（`Accelerator.GPU + CPU`）；GPU 首次加载有设备端编译 warmup，
  delegate 不可用自动回落 CPU。
- 预热必须显式处理 det 与 rec：白图检测不出文字框，只跑 `recognize()` 不会加载 rec。
  `PpOcrEngine.warmUp()` 逐档打开已安装的 rec（最多 `MAX_RESIDENT_RECOGNIZERS` 档）并各跑
  一次 dummy 推理；`ensureWarm()` 供识屏层打开时复用，已预热直接返回。
- **不做常驻预热**：`SideGestureService` 启动时不再 warmUp。fp32 模型常驻实测占
   native ~23MB + GPU ~37MB（单档），而识屏是低频动作；现在每次使用后重置空闲计时，
  `IDLE_RELEASE_MS`（2 分钟）内无新使用就释放 det/rec（`close()` 同一路径，拿锁执行，
  与进行中的识别互斥）。重新加载藏在识屏层打开的框选期间，`ClipboardOcrLauncher`
  等直接调 `recognize()` 的路径首次会多付一次加载/设备端编译。
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
- 引擎只持有 application context（不持有 Service / Activity），不持有 Bitmap；
  调用方负责裁剪与回收。

## 使用方

- `:feature:gesture` 的 `ScreenPickOverlay`：**仅框选菜单的 OCR 动作 / 图片文字**。
  长按取词走 a11y 字符级快照，不再调用 OCR（见 [architecture.md](../../docs/architecture.md) §7.4）。
- 组装根 `:app`（`SideGestureService`）持有 `PpOcrEngine`，服务销毁时 `close()`。
- 识屏层打开时调用 `ensureWarm()`，把模型加载 / 设备端编译藏在用户框选期间；
  空闲释放后下一次开窗会自动重新预热。
- 结果页不等待 OCR：框选 / 幽灵卡只做裁剪并立即交接，OCR 由结果页的 `ocrLoader` 懒加载。
