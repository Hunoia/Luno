#!/usr/bin/env bash
# 维护只存在于本地的 docs 分支 + 独立 worktree。
#
# 规则：
# - docs 分支只放文档，永不 push、永不 merge。
# - docs 分支永远 rebase 到最新的 dev（可用 LUNO_DOCS_BASE=main 切换）。
# - 主 worktree 继续用 dev/main；docs/ 通过软链接指向 docs worktree。
# - pre-push 只允许 dev/main；pre-merge-commit 拒绝把 docs 合入 dev/main。
#
# - docs 分支永远只有 1 个提交：每次保存用 amend，rebase 只重放 1 个提交。
#   需要保留历史时打快照 tag（docs/snapshot-<时间>），分支本身不堆提交。
#
# 用法：
#   scripts/docs-worktree.sh init     # 首次创建 worktree + 安装 hooks
#   scripts/docs-worktree.sh sync     # 拉取并 rebase docs 到基础分支
#   scripts/docs-worktree.sh commit -m "docs: ..."   # 提交（单提交：已有则 amend）
#   scripts/docs-worktree.sh snapshot [name]         # 给当前文档打快照 tag
#   scripts/docs-worktree.sh squash   # 把已有多个 docs 提交压成 1 个
#   scripts/docs-worktree.sh status
#   scripts/docs-worktree.sh hooks    # 只重新安装 hooks
set -euo pipefail

MAIN_ROOT="$(git worktree list --porcelain | awk '/^worktree /{print substr($0,10); exit}')"
DOCS_BRANCH="${LUNO_DOCS_BRANCH:-docs}"
DOCS_WT="${LUNO_DOCS_WORKTREE:-$(dirname "$MAIN_ROOT")/Luno-docs}"
BASE="${LUNO_DOCS_BASE:-dev}"

die() { echo "error: $*" >&2; exit 1; }

base_ref() {
    git -C "$MAIN_ROOT" rev-parse --verify --quiet "$BASE" >/dev/null ||
        git -C "$MAIN_ROOT" rev-parse --verify --quiet main >/dev/null ||
        die "找不到基础分支 $BASE / main"
    git -C "$MAIN_ROOT" rev-parse --verify --quiet "$BASE" >/dev/null && echo "$BASE" || echo main
}

worktree_exists() {
    git -C "$MAIN_ROOT" worktree list --porcelain | grep -Fxq "worktree $DOCS_WT"
}

enable_sparse() {
    # docs worktree 只看文档；代码仍在 docs 分支的提交树里，只是不检出。
    git -C "$DOCS_WT" sparse-checkout init --no-cone >/dev/null 2>&1 || true
    git -C "$DOCS_WT" sparse-checkout set '/.gitignore' '/docs/' >/dev/null 2>&1 || true
}

install_hooks() {
    local hook_dir
    hook_dir="$(git -C "$MAIN_ROOT" rev-parse --git-common-dir)/hooks"
    mkdir -p "$hook_dir"

    cat > "$hook_dir/pre-push" <<'HOOK'
#!/bin/sh
# 本项目只允许 push dev / main；docs 分支永远留在本地。
while read -r local_ref local_sha remote_ref remote_sha; do
    case "$local_ref:$remote_ref" in
        refs/heads/dev:refs/heads/dev|refs/heads/main:refs/heads/main)
            ;;
        *)
            echo "error: 只允许 push dev 或 main（收到 $local_ref -> $remote_ref）" >&2
            echo "  docs 分支只保存在本地，不 push、不合并。" >&2
            echo "  如确需绕过：git push --no-verify" >&2
            exit 1
            ;;
    esac
done

# 本地 CI：远程只做备份，门禁在 push 前跑。
# 紧急跳过：SKIP_CI=1 git push
if [ "${SKIP_CI:-0}" != "1" ]; then
    ./scripts/ci.sh || exit 1
fi
exit 0
HOOK

    cat > "$hook_dir/pre-merge-commit" <<'HOOK'
#!/bin/sh
# docs 分支只存在于本地，不允许合入 dev/main。
branch=$(git branch --show-current 2>/dev/null || true)
[ "$branch" = "dev" ] || [ "$branch" = "main" ] || exit 0

docs_sha=$(git rev-parse --verify --quiet refs/heads/docs || true)
merge_sha=$(git rev-parse --verify --quiet MERGE_HEAD || true)
if [ -n "$docs_sha" ] && [ "$merge_sha" = "$docs_sha" ]; then
    echo "error: 不允许把 docs 分支合入 $branch" >&2
    exit 1
fi

if git diff --cached --name-only | grep -qE '^docs/'; then
    echo "error: $branch 不允许提交 docs/ 变更；请在 docs worktree 里提交" >&2
    exit 1
fi
exit 0
HOOK

    cat > "$hook_dir/post-merge" <<'HOOK'
#!/bin/sh
# dev/main 更新后，把 docs 分支 rebase 到最新基础分支。
branch=$(git branch --show-current 2>/dev/null || true)
[ "$branch" = "dev" ] || [ "$branch" = "main" ] || exit 0

wt="${LUNO_DOCS_WORKTREE:-$(dirname "$(git rev-parse --show-toplevel)")/Luno-docs}"
[ -d "$wt" ] || exit 0

# git 在 hook 里会继承主 worktree 的 GIT_DIR（相对路径 .git）；
# 不 unset 的话 `git -C <docs worktree>` 会去读 docs/.git/index，而那里是 gitdir 指针文件。
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE

if [ -n "$(git -C "$wt" status --porcelain 2>/dev/null)" ]; then
    echo "docs worktree 有未提交改动，跳过自动 rebase：$wt" >&2
    exit 0
fi

base="${LUNO_DOCS_BASE:-dev}"
git -C "$wt" rev-parse --verify --quiet "$base" >/dev/null || base=main
if ! git -C "$wt" rebase "$base" >/dev/null 2>/tmp/luno-docs-rebase.err; then
    git -C "$wt" rebase --abort >/dev/null 2>&1 || true
    echo "docs 自动 rebase 失败，日志：/tmp/luno-docs-rebase.err" >&2
    cat /tmp/luno-docs-rebase.err >&2
fi
exit 0
HOOK

    cp "$hook_dir/post-merge" "$hook_dir/post-commit"
    cp "$hook_dir/post-merge" "$hook_dir/post-rewrite"

    chmod +x "$hook_dir/pre-push" "$hook_dir/pre-merge-commit" "$hook_dir/post-merge" "$hook_dir/post-commit" "$hook_dir/post-rewrite"
    echo "hooks installed: $hook_dir"
}

ensure_ignore() {
    local root="$1"
    local ignore="$root/.gitignore"
    [ -f "$ignore" ] || return 0
    python3 - "$ignore" <<'PY'
import sys
p = sys.argv[1]
lines = open(p, encoding="utf-8").read().splitlines()
out = ["docs" if line in ("docs", "docs/") else line for line in lines]
if "docs" not in out:
    out.append("docs")
open(p, "w", encoding="utf-8").write("\n".join(out) + "\n")
PY
}

link_worktrees() {
    local wt
    git -C "$MAIN_ROOT" worktree list --porcelain | awk '/^worktree /{print substr($0,10)}' | while read -r wt; do
        [ "$wt" = "$DOCS_WT" ] && continue
        [ -d "$wt" ] || continue
        ensure_ignore "$wt"
        if [ -L "$wt/docs" ]; then
            continue
        fi
        if [ -e "$wt/docs" ]; then
            echo "skip: $wt/docs 是真实目录；先备份/移走再跑 init：$wt" >&2
            continue
        fi
        ln -s "$(realpath --relative-to="$wt" "$DOCS_WT/docs")" "$wt/docs"
        echo "linked $wt/docs -> $DOCS_WT/docs"
    done
}

cmd_init() {
    local base
    base="$(base_ref)"

    if ! git -C "$MAIN_ROOT" show-ref --verify --quiet "refs/heads/$DOCS_BRANCH"; then
        worktree_exists && die "worktree 已存在但分支 $DOCS_BRANCH 不存在：$DOCS_WT"
        git -C "$MAIN_ROOT" worktree add -b "$DOCS_BRANCH" "$DOCS_WT" "$base"
        if [ -d "$MAIN_ROOT/docs" ] && [ ! -e "$DOCS_WT/docs" ]; then
            mv "$MAIN_ROOT/docs" "$DOCS_WT/docs"
        fi
        # docs 分支不继承 main/dev 的 docs/ ignore
        python3 - "$DOCS_WT/.gitignore" <<'PY'
import sys
p = sys.argv[1]
lines = open(p, encoding="utf-8").read().splitlines()
out = [line for line in lines if line not in ("docs", "docs/")]
block = [
    "# 模型 / 大文件：可重新下载，不纳入 docs 分支",
    "# 文件仍保留在 worktree 磁盘上，供 main / dev-ws 通过 docs 软链接使用",
    "/docs/ppocrv/",
    "*.tflite",
    "*.onnx",
    "*.zip",
]
if "/docs/ppocrv/" not in out:
    out.extend([""] + block)
open(p, "w", encoding="utf-8").write("\n".join(out) + "\n")
PY
        git -C "$DOCS_WT" add -A .gitignore docs
        git -C "$DOCS_WT" commit -m "docs: track documentation without model assets"
    else
        worktree_exists || git -C "$MAIN_ROOT" worktree add "$DOCS_WT" "$DOCS_BRANCH"
    fi

    link_worktrees
    enable_sparse
    install_hooks
    echo
    echo "docs worktree: $DOCS_WT"
    echo "docs branch:   $DOCS_BRANCH (base: $base)"
    echo "只在 $DOCS_WT 里编辑/提交文档；主 worktree 的 docs 是软链接。"
}

cmd_sync() {
    [ -d "$DOCS_WT" ] || die "docs worktree 不存在，先运行 init"
    git -C "$MAIN_ROOT" fetch origin --prune >/dev/null 2>&1 || true
    if [ -n "$(git -C "$DOCS_WT" status --porcelain)" ]; then
        die "docs worktree 有未提交改动，先提交或 stash：$DOCS_WT"
    fi
    local base
    base="$(base_ref)"
    git -C "$DOCS_WT" rebase "$base"
    echo "docs rebased onto $base"
}

# docs 分支上相对 base 的提交数。
docs_commit_count() {
    local base
    base="$(base_ref)"
    git -C "$DOCS_WT" rev-list --count "$base..$DOCS_BRANCH" 2>/dev/null || echo 0
}

cmd_snapshot() {
    [ -d "$DOCS_WT" ] || die "docs worktree 不存在，先运行 init"
    local name="${1:-snapshot-$(date +%Y%m%d-%H%M%S)}"
    local tag="docs/$name"
    git -C "$DOCS_WT" tag -f "$tag" HEAD >/dev/null
    echo "snapshot tag: $tag -> $(git -C "$DOCS_WT" rev-parse --short HEAD)"
}

# 把 docs 分支上相对 base 的所有提交压成 1 个（保留工作树内容）。
squash_onto_base() {
    local base count
    base="$(base_ref)"
    count="$(docs_commit_count)"
    [ "$count" -le 1 ] && return 0
    git -C "$DOCS_WT" reset --soft "$base"
    git -C "$DOCS_WT" commit -q -m "docs: Luno 规范文档（单提交，快照见 docs/snapshot-* tag）"
    echo "docs squashed: $count -> 1 commit"
}

cmd_squash() {
    [ -d "$DOCS_WT" ] || die "docs worktree 不存在，先运行 init"
    if [ -n "$(git -C "$DOCS_WT" status --porcelain)" ]; then
        die "docs worktree 有未提交改动，先运行 $0 commit 或 stash"
    fi
    local count
    count="$(docs_commit_count)"
    if [ "$count" -le 1 ]; then
        echo "docs 已经是 $count 个提交，无需 squash"
        return 0
    fi
    cmd_snapshot "pre-squash-$(date +%Y%m%d-%H%M%S)"
    squash_onto_base
}

cmd_commit() {
    [ -d "$DOCS_WT" ] || die "docs worktree 不存在，先运行 init"
    local msg=""
    while [ $# -gt 0 ]; do
        case "$1" in
            -m|--message) msg="${2:-}"; shift 2 ;;
            *) die "未知参数：$1（用法：commit -m \"docs: ...\"）" ;;
        esac
    done

    (
        cd "$DOCS_WT"
        git add -A docs .gitignore

        if [ -z "$msg" ]; then
            msg="docs: update specs ($(date +%Y-%m-%d\ %H:%M))"
        fi

        local base count
        base="$(base_ref)"
        count="$(git rev-list --count "$base..HEAD" 2>/dev/null || echo 0)"

        if [ "$count" -eq 0 ]; then
            git commit -q -m "$msg"
            echo "docs: 新建提交（分支保持单提交）"
        else
            # 单提交策略：内容变了就 amend，不再新增提交。
            if git diff --cached --quiet HEAD; then
                git commit -q --amend --no-edit
                echo "docs: 已 amend（内容无变化或仅元信息更新）"
            else
                git commit -q --amend -m "$msg"
                echo "docs: 已 amend 到单提交（$count 个提交压成 1 个）"
            fi
        fi
    )
}

cmd_status() {
    local base
    base="$(base_ref)"
    echo "main worktree: $MAIN_ROOT"
    echo "docs worktree: $DOCS_WT"
    echo "docs branch:   $DOCS_BRANCH"
    echo "base:          $base"
    if [ -d "$DOCS_WT" ]; then
        git -C "$DOCS_WT" status --short --branch
        echo "commits ahead of $base: $(docs_commit_count)（约定为 1）"
        git -C "$DOCS_WT" log --oneline -1
        git -C "$DOCS_WT" tag -l 'docs/*' | tail -3
    else
        echo "docs worktree 尚未创建"
    fi
    echo "linked docs:"
    git -C "$MAIN_ROOT" worktree list --porcelain | awk '/^worktree /{print substr($0,10)}' | while read -r wt; do
        [ "$wt" = "$DOCS_WT" ] && continue
        [ -e "$wt/docs" ] || [ -L "$wt/docs" ] || continue
        ls -ld "$wt/docs" 2>/dev/null || true
    done
}

case "${1:-status}" in
    init) cmd_init ;;
    sync) cmd_sync ;;
    commit) shift; cmd_commit "$@" ;;
    snapshot) shift; cmd_snapshot "$@" ;;
    squash) cmd_squash ;;
    status) cmd_status ;;
    hooks) install_hooks ;;
    *) echo "usage: $0 {init|sync|commit -m <msg>|snapshot [name]|squash|status|hooks}" >&2; exit 2 ;;
esac
