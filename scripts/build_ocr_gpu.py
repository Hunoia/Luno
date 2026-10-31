#!/usr/bin/env python3
"""把 docs/ppocrv 的 PP-OCRv6 fp32 TFLite 打成 GPU 模型包（识屏 PP-OCRv6 用）。

GPU 方案不需要 AOT：app 端用 LiteRT GPU delegate（OpenCL，Adreno）运行时加载
原始 fp32 tflite。本脚本只做打包 + 校验（manifest / SHA-256），产物
`<out>/ppocrv6_gpu.zip` 可直接在「设置 → 屏幕识别 → OCR 模型」里导入。

用法：
  python3 scripts/build_ocr_gpu.py --out /tmp/luno-gpu --rec 320,640
  # 单档：--rec 320 / 640 / 960
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import zipfile

REPO = pathlib.Path(__file__).resolve().parent.parent
SRC_DIR = REPO / "docs/ppocrv"

DET = "ppocrv6_small_det_640_fp32.tflite"
REC_TEMPLATE = "ppocrv6_small_rec_{}_fp32.tflite"
# rec 输入宽度三档：app 端按行宽自动选“刚好放得下”的最小档（短行 320、宽行升 640/960）。
# 一个包可以同时放多档（--rec 320,640），运行时最多常驻两档，超出按 LRU 换入换出。
REC_WIDTHS = (320, 640, 960)
DEFAULT_REC = "320,640"
DICT = "characters.json"
RUNTIME = "litert-gpu-opencl"
LITERT_VERSION = "2.3.2"
PACKAGE_DIR = "ppocrv6_gpu"


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="build/ocr-gpu", help="输出目录（放 zip）")
    parser.add_argument(
        "--rec",
        default=DEFAULT_REC,
        help=f"rec 输入宽度，逗号分隔，可多选：{','.join(map(str, REC_WIDTHS))}（默认 {DEFAULT_REC}）",
    )
    args = parser.parse_args()

    recs = sorted({int(part) for part in args.rec.split(",") if part.strip()})
    if not recs or any(width not in REC_WIDTHS for width in recs):
        parser.error(f"--rec 只支持 {REC_WIDTHS}，收到：{args.rec}")

    models = [DET, *[REC_TEMPLATE.format(width) for width in recs]]
    out = pathlib.Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    pkg = out / PACKAGE_DIR
    pkg.mkdir(exist_ok=True)

    # 复用输出目录时先清掉上一次的模型文件：包里出现两个 rec 会直接被 app 拒绝导入。
    for stale in pkg.iterdir():
        if stale.is_file() and stale.name != DICT:
            stale.unlink()

    for name in [*models, DICT]:
        src = SRC_DIR / name
        if not src.is_file() or src.stat().st_size == 0:
            raise SystemExit(f"缺少模型资产：{src}")
        (pkg / name).write_bytes(src.read_bytes())

    manifest = {
        "kind": "gpu",
        "runtime": RUNTIME,
        "aiEdgeLitert": LITERT_VERSION,
        "files": {
            p.name: sha256(p)
            for p in sorted(pkg.iterdir())
            if p.suffix != ".json" or p.name == DICT
        },
    }
    (pkg / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

    zip_path = out / f"{PACKAGE_DIR}.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for p in sorted(pkg.iterdir()):
            z.write(p, f"{PACKAGE_DIR}/{p.name}")
    print(f"[gpu] package: {zip_path} ({zip_path.stat().st_size} B)")


if __name__ == "__main__":
    main()
