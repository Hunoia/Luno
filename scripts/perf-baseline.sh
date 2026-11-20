#!/usr/bin/env bash
# 一键采集 §15.1 性能基线（只读、需真机、不进 CI）。
#
# 用法：
#   scripts/perf-baseline.sh                     # 20 s 窗口，自动选唯一在线设备
#   scripts/perf-baseline.sh --window 60         # 加长窗口，噪声更小
#   scripts/perf-baseline.sh --serial 192.168.3.71:4277
#
# 退出码：0 通过；1 有指标越过阈值；2 无法测量（无设备 / 服务未运行 / 采样中进程重启）。
#
# 口径与阈值来源 docs/architecture.md §15.1：
#   - CPU 用 /proc/<pid>/stat 的 utime+stime 差分，采样期间不跑 dumpsys（它会抬升目标进程）；
#   - 空闲与面板打开两种状态都要求 :service ≤ 0.5% 单核；
#   - Activities 必须为 0（搜索 Activity 泄漏的回归点）。
set -euo pipefail

readonly PKG_SERVICE="hunoia.luno:service"
readonly CPU_LIMIT_PCT="0.50"
readonly PANEL_WINDOW_PATTERN="fillxfill.*pkg = hunoia.luno.*APPLICATION_OVERLAY"
readonly ADB_TIMEOUT=15

WINDOW=20
SERIAL="${ANDROID_SERIAL:-}"

usage() {
    sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
}

while [ $# -gt 0 ]; do
    case "$1" in
        --window) WINDOW="${2:-}"; shift 2 ;;
        --serial) SERIAL="${2:-}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "error: 未知参数 $1" >&2; usage >&2; exit 2 ;;
    esac
done

if ! [[ "$WINDOW" =~ ^[1-9][0-9]*$ ]]; then
    echo "error: --window 需要正整数（收到 '$WINDOW'）" >&2
    exit 2
fi

command -v adb >/dev/null || { echo "error: 找不到 adb" >&2; exit 2; }

# 设备：显式 --serial > ANDROID_SERIAL > 唯一在线设备。多设备时拒绝猜测。
if [ -z "$SERIAL" ]; then
    mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    case "${#devices[@]}" in
        0) echo "error: 没有在线设备（先连接手机 / 启动无线调试）" >&2; exit 2 ;;
        1) SERIAL="${devices[0]}" ;;
        *) echo "error: 有 ${#devices[@]} 台设备，请用 --serial 指定" >&2; exit 2 ;;
    esac
fi

adb_shell() { timeout "$ADB_TIMEOUT" adb -s "$SERIAL" shell "$@"; }

pid="$(adb_shell "pidof $PKG_SERVICE" | tr -d '\r' || true)"
if [ -z "$pid" ]; then
    echo "error: $PKG_SERVICE 未运行（先打开 Luno 悬浮球）" >&2
    exit 2
fi

# 状态标签：面板开着时球被软隐藏，两条路径的基线都是 ≤0.5%，但分开记录便于对照。
panel_open="$(adb_shell "dumpsys window windows | grep -c '$PANEL_WINDOW_PATTERN'" | tr -d '\r' || true)"
if [ "${panel_open:-0}" -gt 0 ] 2>/dev/null; then
    state="panel-open"
else
    state="idle"
fi

# 采样：整段窗口在设备端一次完成（单次 adb 往返），期间不碰 dumpsys。
ticks="$(timeout $((WINDOW + ADB_TIMEOUT)) adb -s "$SERIAL" shell \
    "a=\$(awk '{print \$14+\$15}' /proc/$pid/stat); sleep $WINDOW; \
     b=\$(awk '{print \$14+\$15}' /proc/$pid/stat); echo \$a \$b" | tr -d '\r')"
read -r t0 t1 <<<"$ticks"
if [ -z "${t1:-}" ]; then
    echo "error: 采样失败（pid=$pid 已退出？）" >&2
    exit 2
fi

pid_after="$(adb_shell "pidof $PKG_SERVICE" | tr -d '\r' || true)"
if [ "$pid_after" != "$pid" ]; then
    echo "error: 采样期间 $PKG_SERVICE 重启（$pid → ${pid_after:-无}），数据作废" >&2
    exit 2
fi

delta=$((t1 - t0))
cpu_pct="$(awk -v d="$delta" -v w="$WINDOW" 'BEGIN { printf "%.2f", d / (w * 100) * 100 }')"

# 内存/泄漏指标在 CPU 采样之后取，避免污染 tick。
meminfo="$(timeout 30 adb -s "$SERIAL" shell "dumpsys meminfo $pid" | tr -d '\r' || true)"
# 行格式形如 `Views: 16  ViewRootImpl: 8` / `AppContexts: 8  Activities: 0`（当前值在前、峰值在后），
# 以及 `Java Heap: 21472 49884` / `TOTAL PSS: 148051 TOTAL RSS: …`（单位 KB）。
field() { awk -v k="$1:" '{ for (i = 1; i <= NF; i++) if ($i == k) { print $(i + 1); exit } }' <<<"$meminfo"; }
pss="$(awk '/TOTAL PSS/ { print $3; exit }' <<<"$meminfo")"
activities="$(field Activities)"
views="$(field Views)"
view_roots="$(field ViewRootImpl)"
java_heap="$(field Heap)"  # `Java Heap:` 被空格拆成 `Java` `Heap:`

fail=0
cpu_ok="$(awk -v c="$cpu_pct" -v l="$CPU_LIMIT_PCT" 'BEGIN { print (c <= l) ? 1 : 0 }')"
acts_ok=0
[ "${activities:-x}" = "0" ] && acts_ok=1

verdict() { if [ "$1" = "1" ]; then printf '%s' "OK"; else printf '%s' "FAIL"; fi; }
report() {
    # $1 指标 $2 实测 $3 是否通过 $4 基线/说明。判定紧贴实测值，避免中文宽度把列撑歪。
    [ "$3" = "1" ] || fail=1
    printf '  %-24s %-14s [%-4s] %s\n' "$1" "$2" "$(verdict "$3")" "$4"
}

pss_mb="$(awk -v k="${pss:-0}" 'BEGIN { printf "%.1f", k / 1024 }')"

printf 'state=%s  pid=%s  serial=%s  window=%ss\n\n' "$state" "$pid" "$SERIAL" "$WINDOW"
printf '  %-24s %-14s %s\n' "指标" "实测" "基线 / 说明"
report ":service CPU" "${cpu_pct}%" "$cpu_ok" "基线 ≤ ${CPU_LIMIT_PCT}%（球静置、面板开关都算）"
report "Activities" "${activities:-?}" "$acts_ok" "必须 0（搜索 Activity 泄漏回归点）"
report "Views / VRI" "${views:-?} / ${view_roots:-?}" "1" "参考值，不判定"
report "Java Heap" "${java_heap:-?} KB" "1" "参考值，不判定"
report "TOTAL PSS" "${pss_mb} MB" "1" "参考值，不判定"
printf '  %-24s %-14s\n' "tick 计数" "$delta / $((WINDOW * 100))"
echo

if [ "$fail" -ne 0 ]; then
    echo "=> FAIL"
    # 常见误判：球面动图正在播放时 :service 本来就有 5~6%（§15.1），不属于空闲基线。
    echo "提示：球面动图播放中（未静置）约 5~6% 属预期；等球静置 / 面板打开后再测空闲基线。" >&2
    exit 1
fi
echo "=> PASS"
