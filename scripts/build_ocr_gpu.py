#!/usr/bin/env python3
"""把 docs/ppocrv 的 PP-OCRv6 fp32 TFLite 打成 GPU 模型包（识屏 PP-OCRv6 用）。

GPU 方案不需要 AOT：app 端用 LiteRT GPU delegate（OpenCL，Adreno）运行时加载
原始 fp32 tflite。本脚本只做打包 + 校验（manifest / SHA-256），产物
`<out>/ppocrv6_gpu.zip` 可直接在「设置 → 屏幕识别 → OCR 模型」里导入。

用法：
  python3 scripts/build_ocr_gpu.py --out /tmp/luno-gpu
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import zipfile

REPO = pathlib.Path(__file__).resolve().parent.parent
SRC_DIR = REPO / "docs/ppocrv"

# det 640 + rec 320（宽行由 app 内切段解决，暂不打包 640/960 档）。
MODELS = [
    "ppocrv6_small_det_640_fp32.tflite",
    "ppocrv6_small_rec_320_fp32.tflite",
]
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
    args = parser.parse_args()

    out = pathlib.Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    pkg = out / PACKAGE_DIR
    pkg.mkdir(exist_ok=True)

    for name in [*MODELS, DICT]:
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
