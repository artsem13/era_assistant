#!/data/data/com.termux/files/usr/bin/bash
set -eu
WORKER_PROTOCOL_VERSION=1
WORKER_VERSION="1.1.0"
CAPABILITY_RUNTIME_INFO="termux_runtime_info"
CAPABILITY_RUNTIME_INFO_DELAY="termux_runtime_info_delay"
TASK_ROOT="${HOME:-/data/data/com.termux/files/home}/.era/tasks"
fail() { printf 'protocolVersion=%s\nstatus=FAILED\nerror=%s\n' "$WORKER_PROTOCOL_VERSION" "$1"; exit "$2"; }
safe_value() { printf '%s' "$1" | tr '\n\r=' '   ' | cut -c 1-160; }
valid_id() { case "$1" in ""|*[!A-Za-z0-9._:-]*) return 1;; esac; }
task_file() { printf '%s/%s.state' "$TASK_ROOT" "$1"; }
write_state() { file="$(task_file "$1")"; tmp="${file}.tmp.$$"; printf 'taskId=%s\nstatus=%s\nexitCode=%s\n' "$(safe_value "$1")" "$2" "$3" > "$tmp"; [ "${4-}" = "" ] || printf 'result=%s\n' "$(safe_value "$4")" >> "$tmp"; [ "${5-}" = "" ] || printf 'error=%s\n' "$(safe_value "$5")" >> "$tmp"; mv "$tmp" "$file"; }
read_field() { awk -F= -v key="$1" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$2"; }
emit_state() { file="$(task_file "$1")"; [ -f "$file" ] || fail task_not_found 67; printf 'protocolVersion=%s\n' "$WORKER_PROTOCOL_VERSION"; printf 'taskId=%s\nstatus=%s\nexitCode=%s\n' "$(read_field taskId "$file")" "$(read_field status "$file")" "$(read_field exitCode "$file")"; result="$(read_field result "$file" || true)"; error="$(read_field error "$file" || true)"; [ -n "$result" ] && printf 'result=%s\n' "$result"; [ -n "$error" ] && printf 'error=%s\n' "$error"; }
[ "$#" -ge 2 ] || fail invalid_protocol 64
action="$1"; task_id="$2"; valid_id "$task_id" || fail invalid_task_id 65; mkdir -p "$TASK_ROOT"
case "$action" in
  STATUS|RESULT) [ "$#" -eq 2 ] || fail invalid_protocol 64; emit_state "$task_id"; exit 0 ;;
  CANCEL) [ "$#" -eq 2 ] || fail invalid_protocol 64; file="$(task_file "$task_id")"; [ -f "$file" ] || fail task_not_found 67; status="$(read_field status "$file")"; case "$status" in COMPLETED|FAILED|CANCELLED) ;; *) write_state "$task_id" CANCELLED 130 "" cancel_requested ;; esac; emit_state "$task_id"; exit 0 ;;
  RUN) [ "$#" -eq 3 ] || fail invalid_protocol 64; capability="$3"; case "$capability" in "$CAPABILITY_RUNTIME_INFO"|"$CAPABILITY_RUNTIME_INFO_DELAY") ;; *) fail unsupported_capability 66 ;; esac; file="$(task_file "$task_id")"; [ ! -e "$file" ] || fail duplicate_task 68; write_state "$task_id" RUNNING 0; [ "$capability" = "$CAPABILITY_RUNTIME_INFO_DELAY" ] && sleep 5 || true; runtime_shell="${SHELL:-unknown}"; termux_prefix="${PREFIX:-unknown}"; kernel_summary="$(uname -srmo 2>/dev/null || printf unavailable)"; android_api="$(getprop ro.build.version.sdk 2>/dev/null || printf unknown)"; timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || printf unknown)"; output="workerProtocolVersion=$WORKER_PROTOCOL_VERSION\nworkerVersion=$WORKER_VERSION\nruntime=termux\ntaskId=$(safe_value "$task_id")\nshell=$(safe_value "$runtime_shell")\ntermuxPrefix=$(safe_value "$termux_prefix")\nuname=$(safe_value "$kernel_summary")\nandroidApi=$(safe_value "$android_api")\ntimestamp=$(safe_value "$timestamp")\nstatus=COMPLETED"; write_state "$task_id" COMPLETED 0 "$output"; printf '%b\n' "$output"; exit 0 ;;
  *) fail unsupported_action 64 ;;
esac
