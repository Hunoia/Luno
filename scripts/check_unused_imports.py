#!/usr/bin/env python3
"""Scan/remove unused imports across the luno source tree.

Run without -w to report only. `getValue`/`setValue`/`componentN`/`plusAssign`
are Kotlin delegate/property operators resolved via `by`, so a textual search
cannot see them; they are skipped as false positives.
"""
import glob
import os
import re
import sys

ROOT = "app/src/main/java/hunoia/luno"
SKIP = {
    "getValue",
    "setValue",
    "component1", "component2", "component3", "component4", "component5",
    "component6", "component7", "component8", "component9", "component10",
    "plusAssign",
}
IMPORT_RE = re.compile(r"^import (?!static )([\w.]+)$")


def scan(path):
    text = open(path, encoding="utf-8").read()
    lines = text.split("\n")
    import_lines = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not import_lines:
        return []
    last = max(import_lines)
    body = "\n".join(lines[last + 1:])
    unused = []
    for i, line in enumerate(lines):
        m = IMPORT_RE.match(line)
        if not m:
            continue
        name = m.group(1).split(".")[-1]
        if name in SKIP:
            continue
        if not re.search(r"\b" + re.escape(name) + r"\b", body):
            unused.append((i + 1, line))
    return unused


def main():
    write = "-w" in sys.argv
    total = 0
    for path in sorted(glob.glob(ROOT + "/**/*.kt", recursive=True)):
        unused = scan(path)
        if not unused:
            continue
        for lineno, line in unused:
            print(f"{os.path.relpath(path, ROOT)}:{lineno}: {line.strip()}")
        total += len(unused)
        if write:
            text = open(path, encoding="utf-8").read()
            for _, line in reversed(unused):
                text = text.replace(line + "\n", "", 1)
            open(path, "w", encoding="utf-8").write(text)
    print("TOTAL unused imports:", total)
    if write:
        print("(rewritten in place)")


if __name__ == "__main__":
    main()
