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


# 尺寸 token 的事实源：Dimension.kt 为主，DockDimension.kt 单独承载双球停靠栏的纯尺寸
# （拆开的原因见该文件头注释：形状库的字节码版本高于单测 JVM）。
TOKEN_SOURCES = (
    "ui/design/src/main/java/hunoia/luno/ui/theme/Dimension.kt",
    "ui/design/src/main/java/hunoia/luno/ui/theme/DockDimension.kt",
)


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
    lines = [
        "# UI token 清单（自动生成）",
        "",
        AUTO + "> 来源：`ui/design/.../theme/Dimension.kt`、`DockDimension.kt`（唯一事实源）。",
        "",
        "| token | 值 | 源码位置 |",
        "|---|---|---|",
    ]
    token_re = re.compile(r"^(?:const )?val\s+(\w+)\s*=\s*(.+?)\s*$")
    for relative in TOKEN_SOURCES:
        source = ROOT / relative
        for lineno, line in enumerate(read(source).splitlines(), start=1):
            match = token_re.match(line)
            if not match:
                continue
            name, value = match.group(1), match.group(2).replace("|", r"\|")
            lines.append(f"| `{name}` | `{value}` | `{source.name}:{lineno}` |")
    lines.append("")
    return "\n".join(lines)


def ui_components_doc() -> str:
    root = ROOT / "ui/design/src/main/java/hunoia/luno/ui/component"
    # 允许 `fun <T> Name(...)`（泛型）与 `fun Modifier.name(...)`（扩展）：只捕获真正的名字，
    # 否则索引会漏泛型（WheelColumn 踩过）或把接收者 `Modifier` / `ColumnScope` 当组件名。
    # `private` 是文件内实现细节，不进组件索引。
    composable_re = re.compile(
        r"@Composable\s+(?:(internal|public)\s+)?fun\s+(?:<[^<>]*>\s*)?(?:[\w.<>]+\.)?(\w+)"
    )
    lines = [
        "# UI 组件索引（自动生成）",
        "",
        AUTO + "> 来源：`ui/design/src/main/java/hunoia/luno/ui/component/**/*.kt`（只列非 `private` 顶层 Composable）。",
        "",
        "| 文件 | 顶层 Composable |",
        "|---|---|",
    ]
    for path in sorted(root.rglob("*.kt")):
        names = list(dict.fromkeys(name for _vis, name in composable_re.findall(read(path))))
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


STRING_KEY_RE = re.compile(r'<string\s+name="([^"]+)"')
QUALIFIED_REF_RE = re.compile(r"([\w.]+?)\.R\.string\.([A-Za-z0-9_]+)")
PLAIN_REF_RE = re.compile(r"(?<![\w.])R\.string\.([A-Za-z0-9_]+)")
ALIAS_IMPORT_RE = re.compile(r"import\s+([\w.]+)\.R\s+as\s+(\w+)")
AT_STRING_RE = re.compile(r"@string/([A-Za-z0-9_]+)")

MODULE_NAMESPACES = {
    "app": "hunoia.luno",
    "core:common": "hunoia.luno.core.common",
    "core:domain": "hunoia.luno.core.domain",
    "core:bridge": "hunoia.luno.core.bridge",
    "core:data": "hunoia.luno.core.data",
    "core:runtime": "hunoia.luno.core.runtime",
    "core:facade": "hunoia.luno.core.facade",
    "ui:design": "hunoia.luno.design",
    "capability:launch": "hunoia.luno.quickaccess",
    "capability:ocr": "hunoia.luno.ocr",
    "feature:execute": "hunoia.luno.feature.execute",
    "feature:actions": "hunoia.luno.actions",
    "feature:link": "hunoia.luno.link",
    "feature:search": "hunoia.luno.search",
    "feature:quickaccess": "hunoia.luno.quickaccess.app",
    "feature:gesture": "hunoia.luno.gesture",
    "feature:floatball": "hunoia.luno.floatball",
    "feature:gridpanel": "hunoia.luno.gridpanel",
    "feature:annotate": "hunoia.luno.annotate",
    "feature:a11y": "hunoia.luno.a11y",
    "feature:picker": "hunoia.luno.picker",
}


def _module_namespace(module: Path) -> str | None:
    build = module / "build.gradle.kts"
    if not build.is_file():
        return None
    match = re.search(r'namespace\s*=\s*"([^"]+)"', read(build))
    return match.group(1) if match else None


def _string_keys(path: Path) -> set[str]:
    return set(STRING_KEY_RE.findall(read(path))) if path.is_file() else set()


def _string_refs_by_namespace() -> dict[str, set[str]]:
    """namespace -> 被引用到的 string key。

    三种引用形式都要认：
    - 本模块的 `R.string.x`（`AnnotateR.string.x` 这种别名不算本模块）
    - `import <ns>.R as Foo` 之后写的 `Foo.string.x`
    - XML 里的 `@string/x`（归属所在模块）
    """
    modules = [ROOT / "app"] + [
        p for group in ("core", "feature", "ui", "capability")
        for p in sorted((ROOT / group).glob("*")) if (p / "build.gradle.kts").is_file()
    ]
    namespace_of_file: dict[Path, str] = {}
    for module in modules:
        ns = _module_namespace(module)
        if ns is None:
            continue
        for path in (module / "src/main").rglob("*"):
            if path.is_file() and path.suffix in {".kt", ".xml"} and "/build/" not in path.as_posix():
                namespace_of_file.setdefault(path, ns)

    refs: dict[str, set[str]] = {}
    known = set(namespace_of_file.values())
    for path, own_ns in namespace_of_file.items():
        text = read(path)
        aliases = {alias: ns for ns, alias in ALIAS_IMPORT_RE.findall(text)}
        bucket = refs.setdefault(own_ns, set())
        for qualifier, key in QUALIFIED_REF_RE.findall(text):
            if qualifier in aliases:
                refs.setdefault(aliases[qualifier], set()).add(key)
            elif qualifier in known:
                refs.setdefault(qualifier, set()).add(key)
        for alias, ns in aliases.items():
            if alias.endswith("R"):
                refs.setdefault(ns, set()).update(
                    re.findall(re.escape(alias) + r"\.string\.([A-Za-z0-9_]+)", text),
                )
        bucket.update(PLAIN_REF_RE.findall(text))
        bucket.update(AT_STRING_RE.findall(text))
    return refs


def _unused_strings(module: Path, refs: dict[str, set[str]]) -> list[str]:
    """模块自己的 string 必须被全仓引用，否则就是搬迁后没删的旧副本。"""
    ns = _module_namespace(module)
    if ns is None:
        return []
    keys = _string_keys(module / "src/main/res/values/strings.xml")
    return sorted(keys - refs.get(ns, set()))


def _settings_modules() -> set[str]:
    text = read(ROOT / "settings.gradle.kts")
    return set(re.findall(r'include\("(:[^"]+)"\)', text))


def _test_class_names() -> set[str]:
    names: set[str] = set()
    for group in ("app", "core", "feature", "ui", "capability"):
        base = ROOT / group
        if not base.is_dir():
            continue
        for path in base.rglob("*.kt"):
            posix = path.as_posix()
            if "/src/test/" in posix and "/build/" not in posix:
                names.add(path.stem)
    return names


def check_architecture_drift() -> list[str]:
    """规范正文漂移检查：实现路径、模块名、门禁类、章节引用。"""
    path = DOCS / "architecture.md"
    text = read(path)
    errors: list[str] = []

    # 正文不写实现文件路径；事实源用模块名 / 链接指代。
    for match in sorted(set(re.findall(r"`([^`]+\.kts?)`", text))):
        errors.append(f"{rel(path)}：正文出现实现文件路径 `{match}`")

    modules = _settings_modules()
    # 只校验真实模块组，避免把进程名（:service）和反例（:shared:*）当模块。
    module_re = re.compile(r":(?:core|feature|capability|ui|app)(?::[a-z][a-z0-9-]*)?(?![\w*:])")
    for module in sorted(set(module_re.findall(text))):
        if module not in modules:
            errors.append(f"{rel(path)}：引用了不存在的模块 {module}")

    tests = _test_class_names()
    for name in sorted(set(re.findall(r"\b([A-Z][A-Za-z0-9]*Test)\b", text))):
        if name not in tests:
            errors.append(f"{rel(path)}：门禁测试不存在 {name}")

    headings = set(re.findall(r"^#{2,3}\s+(\d+(?:\.\d+)?)", text, re.M))
    for ref in sorted(set(re.findall(r"§(\d+(?:\.\d+)?)", text))):
        if ref not in headings:
            errors.append(f"{rel(path)}：引用了不存在的章节 §{ref}")
    return errors


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


    # 资源去重：字符串只在真正使用它的模块里定义。
    # 页面搬到 feature 后，app 里那份必须删掉，否则改一处漏一处。
    modules = [ROOT / "app"]
    for group in ("core", "feature", "ui", "capability"):
        modules += sorted(p for p in (ROOT / group).glob("*") if (p / "build.gradle.kts").is_file())
    string_refs = _string_refs_by_namespace()
    for module in modules:
        unused = _unused_strings(module, string_refs)
        if unused:
            errors.append(
                f"{rel(module)}：strings.xml 里有未被本模块引用的条目 {len(unused)} 个"
                f"（示例：{', '.join(unused[:5])}）——搬到 feature 后 app 的旧副本要删掉",
            )

    if errors:
        print("\n".join(errors))
        print("\nspec docs check FAILED")
        return 1

    drift = check_architecture_drift()
    if drift:
        print("\n".join(drift))
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
