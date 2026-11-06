#!/usr/bin/env bash
# 本地 CI：远程仓库只做备份，门禁在 push 前跑。
#
# 用法：
#   scripts/ci.sh              # 全量：构建 + 单测 + 未用 import + 文档检查
#   SKIP_CI=1 git push         # 紧急跳过（不推荐）
#
# 由 pre-push hook 调用（scripts/docs-worktree.sh hooks 安装）。
set -euo pipefail

cd "$(dirname "$0")/.."

step() { printf '\n== %s ==\n' "$1"; }

step "Gradle 构建 + 单测（含 luno.arch 依赖门禁、Konsist 门禁）"
# 本机内存紧：限制并发与 Kotlin daemon 堆，避免构建到一半被 OOM 杀掉。
./gradlew --max-workers=2 -Dkotlin.daemon.jvmargs=-Xmx1536m \
    :app:assembleDebug :app:testDebugUnitTest

step "未使用 import"
unused="$(python3 scripts/check_unused_imports.py)"
echo "$unused" | tail -5
if ! grep -q "TOTAL unused imports: 0" <<<"$unused"; then
    echo "error: 存在未使用的 import，请先清理" >&2
    exit 1
fi

if [ -d docs ]; then
    step "文档一致性"
    python3 scripts/spec_docs.py generate >/dev/null
    python3 scripts/spec_docs.py check
else
    echo "skip: 未找到 docs/（文档 worktree 不在本机）"
fi

printf '\n本地 CI 通过。\n'
