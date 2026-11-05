#!/usr/bin/env python3
"""维护规范文档的“派生事实”与引用检查。

规范文档（architecture.md / ui-guidelines.md）只写不变量与契约，不手写：
模块清单、备份域、UI token 值、UI 组件路径、进程组件清单。

这些事实统一从源码生成到 docs/reference/，规范只链接过去。

用法：
  python3 scripts/spec_docs.py generate   # 重新生成 docs/reference/
  python3 scripts/spec_docs.py check      # 校验生成物是否最新、规范内链接是否有效
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
REF = DOCS / "reference"

SPEC_FILES = [
    DOCS / "README.md",
    DOCS / "architecture.md",
    DOCS / "ui-guidelines.md",
    DOCS / "architecture_future.md",
    DOCS / "archive.md",
]

AUTO = (
    "> **AUTO-GENERATED — 不要手工编辑。**\n"
    "> 由 `python3 scripts/spec_docs.py generate` 从源码生成；"
    "`check` 会验证本文与源码一致。\n\n"
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def modules_doc() -> str:
    settings = read(ROOT / "settings.gradle.kts")
    modules = re.findall(r'include\("(:[^"]+)"\)', settings)
    lines = [
        "# 模块清单（自动生成）",
        "",
        AUTO + "> 来源：`settings.gradle.kts`、各模块 `build.gradle.kts`。",
        "",
        "| 模块 | 直接依赖 |",
        "|---|---|",
    ]
    for module in modules:
        build_file = ROOT / module.lstrip(":").replace(":", "/") / "build.gradle.kts"
        deps = []
        if build_file.is_file():
            deps = sorted(set(re.findall(r'project\("(:[^"]+)"\)', read(build_file))))
        dep_text = ", ".join(f"`{dep}`" for dep in deps) if deps else "—"
        lines.append(f"| `{module}` | {dep_text} |")
    lines.append("")
    return "\n".join(lines)


def backup_domains_doc() -> str:
    backup = read(ROOT / "core/data/src/main/java/hunoia/luno/config/model/Backup.kt")
    enum_match = re.search(r"enum class BackupDomain\s*\{(.*?)\}", backup, re.S)
    domains = []
    if enum_match:
        domains = [item.strip() for item in enum_match.group(1).split(",") if item.strip()]
    versions = dict(re.findall(r"const val (\w+) = (\d+)", backup))

    registry = read(
        ROOT / "core/data/src/main/java/hunoia/luno/config/backup/BackupDomains.kt"
    )
    registered = set(re.findall(r"domain = BackupDomain\.(\w+)", registry))

    lines = [
        "# 备份域（自动生成）",
        "",
        AUTO + "> 来源：`core/data/.../config/model/Backup.kt`、"
        "`core/data/.../config/backup/BackupDomains.kt`。",
        "",
        "| 域 | Schema 版本 | 已注册 |",
        "|---|---|---|",
    ]
    for domain in domains:
        version = versions.get(domain, "—")
        lines.append(
            f"| `{domain}` | `{version}` | {'是' if domain in registered else '否'} |"
        )
    lines.extend(
        [
            "",
            "新增/删除域：修改 `BackupDomain` 与 `Backup`，在 `BuiltinBackupDomains` 注册 `BackupDomainSpec`；"
            "不要改规范文档里的清单。",
            "",
        ]
    )
    return "\n".join(lines)


def ui_tokens_doc() -> str:
    source = ROOT / "ui/design/src/main/java/hunoia/luno/ui/theme/Dimension.kt"
    lines = [
        "# UI token 清单（自动生成）",
        "",
        AUTO + "> 来源：`ui/design/.../theme/Dimension.kt`（唯一事实源）。",
        "",
        "| token | 值 | 源码行 |",
        "|---|---|---|",
    ]
    token_re = re.compile(r"^(?:const )?val\s+(\w+)\s*=\s*(.+?)\s*$")
    for lineno, line in enumerate(read(source).splitlines(), start=1):
        match = token_re.match(line)
        if not match:
            continue
        name, value = match.group(1), match.group(2).replace("|", r"\|")
        lines.append(f"| `{name}` | `{value}` | {lineno} |")
    lines.append("")
    return "\n".join(lines)


def ui_components_doc() -> str:
    root = ROOT / "ui/design/src/main/java/hunoia/luno/ui/component"
    composable_re = re.compile(
        r"@Composable\s+(?:private\s+|internal\s+|public\s+)?fun\s+(\w+)"
    )
    lines = [
        "# UI 组件索引（自动生成）",
        "",
        AUTO + "> 来源：`ui/design/src/main/java/hunoia/luno/ui/component/**/*.kt`。",
        "",
        "| 文件 | 顶层 Composable |",
        "|---|---|",
    ]
    for path in sorted(root.rglob("*.kt")):
        names = list(dict.fromkeys(composable_re.findall(read(path))))
        name_text = ", ".join(f"`{name}`" for name in names) if names else "—"
        lines.append(f"| `{rel(path)}` | {name_text} |")
    lines.append("")
    return "\n".join(lines)


def process_components_doc() -> str:
    manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
    tag_re = re.compile(
        r"<(activity|service|receiver|provider)\b(.*?)(?:/>|>(.*?)</\1>)", re.S
    )
    lines = [
        "# 进程组件（自动生成）",
        "",
        AUTO + "> 来源：`app/src/main/AndroidManifest.xml`。",
        "",
        "| 类型 | 组件 | 进程 |",
        "|---|---|---|",
    ]
    for kind, attrs, _body in tag_re.findall(manifest):
        name_match = re.search(r'android:name="([^"]+)"', attrs)
        if not name_match:
            continue
        process_match = re.search(r'android:process="([^"]+)"', attrs)
        name = name_match.group(1)
        process = process_match.group(1) if process_match else "主进程"
        lines.append(f"| `{kind}` | `{name}` | `{process}` |")
    lines.append("")
    return "\n".join(lines)


def build_files() -> dict[str, str]:
    return {
        "modules.md": modules_doc(),
        "backup-domains.md": backup_domains_doc(),
        "ui-tokens.md": ui_tokens_doc(),
        "ui-components.md": ui_components_doc(),
        "process-components.md": process_components_doc(),
    }


def generate() -> int:
    REF.mkdir(parents=True, exist_ok=True)
    for name, content in build_files().items():
        (REF / name).write_text(content, encoding="utf-8")
        print(f"wrote {rel(REF / name)}")
    return 0


def check() -> int:
    errors: list[str] = []
    for name, expected in build_files().items():
        path = REF / name
        if not path.is_file():
            errors.append(f"缺少生成文件：{rel(path)}（运行 generate）")
        elif read(path) != expected:
            errors.append(f"生成文件已过期：{rel(path)}（运行 generate）")

    link_re = re.compile(r"\]\(([^)]+)\)")
    for path in SPEC_FILES:
        if not path.is_file():
            errors.append(f"缺少规范文件：{rel(path)}")
            continue
        for target in link_re.findall(read(path)):
            target = target.strip()
            if not target or target.startswith(("#", "http://", "https://", "mailto:")):
                continue
            relative = target.split("#", 1)[0]
            if relative and not (path.parent / relative).exists():
                errors.append(f"{rel(path)}：链接不存在 -> {target}")

    if errors:
        print("\n".join(errors))
        print("\nspec docs check FAILED")
        return 1
    print("spec docs check OK")
    return 0


def main() -> int:
    command = sys.argv[1] if len(sys.argv) > 1 else "check"
    if command == "generate":
        return generate()
    if command == "check":
        return check()
    print(__doc__)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
