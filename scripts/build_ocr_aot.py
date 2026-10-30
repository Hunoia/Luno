#!/usr/bin/env python3
"""把 docs/ppocrv 的 int8 TFLite 编译成 SM8750 host AOT 包（识屏 PP-OCRv5 用）。

产物：`<out>/ppocrv5_sm8750_aot.zip`，结构见 ADR-0005 §D3，可直接在
「设置 → 屏幕识别 → OCR 模型」里导入。

环境（Linux x86_64）：
  pip install ai-edge-litert==2.3.0 ai-edge-litert-sdk-qualcomm==2.3.0
  # sdk 包安装时会把 QAIRT 2.50.0.260828 拉到 site-packages/ai_edge_litert_sdk_qualcomm/data；
  # 若想复用已下载的 QAIRT，可 SKIP_SDK_DOWNLOAD=1 安装后把 data/lib/x86_64-linux-clang 软链过去。
  # 插件还依赖 libc++.so.1 / libunwind.so.1（Ubuntu 上通常缺）：
  #   LD_LIBRARY_PATH=<libcxx>/lib:<libunwind>/lib
  #   nix build nixpkgs#llvmPackages.libcxx nixpkgs#llvmPackages.libunwind

用法：
  python3 scripts/build_ocr_aot.py --out /tmp/luno-aot
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import zipfile

REPO = pathlib.Path(__file__).resolve().parent.parent
SRC_DIR = REPO / "docs/ppocrv/extracted/pp_ocrv5_tflite_int8"

# (源文件, AOT 包内文件名)；lat_rec 与字典不匹配，本期不用（ADR-0005 §1.3）。
MODELS = [
    ("pp_ocrv5_det_mobile.tflite", "pp_ocrv5_det_mobile.tflite"),
    ("pp_ocrv5_zh_rec.tflite", "pp_ocrv5_zh_rec.tflite"),
    ("pp_ocrv5_ko_rec.tflite", "pp_ocrv5_ko_rec.tflite"),
]
DICTS = ["zh_dict.txt", "ko_dict.txt"]

SOC = "SM8750"
QAIRT_VERSION = "2.50.0.260828"
LITERT_VERSION = "2.3.0"
PACKAGE_DIR = "ppocrv5_sm8750_aot"


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def compile_models(work: pathlib.Path) -> dict[str, pathlib.Path]:
    # 延迟导入：--help 不需要装 AOT 依赖。
    from ai_edge_litert.aot import aot_compile
    from ai_edge_litert.aot.vendors.qualcomm import target as qcom

    target = qcom.Target(soc_model=qcom.SocModel.SM8750)
    compiled: dict[str, pathlib.Path] = {}
    for src_name, dst_name in MODELS:
        out_dir = work / "compiled" / pathlib.Path(dst_name).stem
        out_dir.mkdir(parents=True, exist_ok=True)
        result = aot_compile.aot_compile(
            input_model=str(SRC_DIR / src_name),
            output_dir=str(out_dir),
            target=target,
            keep_going=False,
        )
        if result.failed_backends:
            raise SystemExit(f"{src_name} 编译失败：{result.failed_backends}")
        outputs = list(out_dir.glob("*_apply_plugin.tflite"))
        if len(outputs) != 1:
            raise SystemExit(f"{src_name} 产物异常：{outputs}")
        compiled[dst_name] = outputs[0]
        print(f"[aot] {src_name} -> {outputs[0].name} ({outputs[0].stat().st_size} B)")
    return compiled


def build_package(work: pathlib.Path) -> pathlib.Path:
    pkg = work / PACKAGE_DIR
    pkg.mkdir(parents=True, exist_ok=True)
    for src_name, dst_name in MODELS:
        (pkg / dst_name).write_bytes((work / "compiled" / pathlib.Path(dst_name).stem
                                      / f"{pathlib.Path(src_name).stem}_Qualcomm_{SOC}_apply_plugin.tflite"
                                      ).read_bytes())
    for name in DICTS:
        (pkg / name).write_bytes((SRC_DIR / name).read_bytes())

    manifest = {
        "kind": "aot",
        "soc": SOC,
        "runtime": "litert-npu-qualcomm",
        "qairt": QAIRT_VERSION,
        "aiEdgeLitert": LITERT_VERSION,
        "files": {p.name: sha256(p) for p in sorted(pkg.iterdir()) if p.suffix != ".json"},
    }
    (pkg / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

    zip_path = work / f"{PACKAGE_DIR}.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for p in sorted(pkg.iterdir()):
            z.write(p, f"{PACKAGE_DIR}/{p.name}")
    print(f"[aot] package: {zip_path} ({zip_path.stat().st_size} B)")
    return zip_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="build/ocr-aot", help="工作目录（放编译中间产物与 zip）")
    args = parser.parse_args()

    work = pathlib.Path(args.out).resolve()
    work.mkdir(parents=True, exist_ok=True)
    compile_models(work)
    build_package(work)


if __name__ == "__main__":
    main()
