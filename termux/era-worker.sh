#!/data/data/com.termux/files/usr/bin/bash
set -eu
WORKER_PROTOCOL_VERSION=1
WORKER_VERSION="1.3.0"
CAPABILITY_RUNTIME_INFO="termux_runtime_info"
CAPABILITY_RUNTIME_INFO_DELAY="termux_runtime_info_delay"
CAPABILITY_LIFECYCLE_PROBE="termux_lifecycle_probe"
CAPABILITY_CURRENT_LOCATION="get_current_location"
TASK_ROOT="${HOME:-/data/data/com.termux/files/home}/.era/tasks"
fail() { printf 'protocolVersion=%s\nstatus=FAILED\nerror=%s\n' "$WORKER_PROTOCOL_VERSION" "$1"; exit "$2"; }
safe_value() { printf '%s' "$1" | tr '\n\r=' '   ' | cut -c 1-160; }
safe_result() { printf '%s' "$1" | tr '\n\r' '  ' | cut -c 1-1024; }
valid_id() { case "$1" in ""|*[!A-Za-z0-9._:-]*) return 1;; esac; }
task_file() { printf '%s/%s.state' "$TASK_ROOT" "$1"; }
read_field() { awk -F= -v key="$1" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$2"; }
PARENT_PID=""; CHILD_PID=""; HEARTBEAT=0; LAST_HEARTBEAT=""; TASK_ID=""
write_state() {
  file="$(task_file "$TASK_ID")"; tmp="${file}.tmp.$$"
  {
    printf 'taskId=%s\nstatus=%s\nexitCode=%s\n' "$(safe_value "$TASK_ID")" "$1" "$2"
    printf 'parentPid=%s\nchildPid=%s\nheartbeat=%s\nlastHeartbeat=%s\n' "$PARENT_PID" "$CHILD_PID" "$HEARTBEAT" "$(safe_value "$LAST_HEARTBEAT")"
    printf 'journal=private_termux\n'
    [ "${3-}" = "" ] || printf 'result=%s\n' "$(safe_result "$3")"
    [ "${4-}" = "" ] || printf 'error=%s\n' "$(safe_value "$4")"
  } > "$tmp"
  mv "$tmp" "$file"
}
proc_alive() { [ -d "/proc/$1" ] && kill -0 "$1" 2>/dev/null; }
kill_tree() {
  pid="$1"; [ -n "$pid" ] || return 0
  children="$(cat "/proc/$pid/task/$pid/children" 2>/dev/null || true)"
  for child in $children; do kill_tree "$child"; done
  kill -TERM "$pid" 2>/dev/null || true
}
cleanup_child() { [ -n "$CHILD_PID" ] && kill_tree "$CHILD_PID" || true; }
emit_state() {
  file="$(task_file "$1")"; [ -f "$file" ] || fail task_not_found 67
  printf 'protocolVersion=%s\n' "$WORKER_PROTOCOL_VERSION"
  printf 'taskId=%s\nstatus=%s\nexitCode=%s\n' "$(read_field taskId "$file")" "$(read_field status "$file")" "$(read_field exitCode "$file")"
  for key in heartbeat lastHeartbeat parentPid childPid journal error; do value="$(read_field "$key" "$file" || true)"; [ -n "$value" ] && printf '%s=%s\n' "$key" "$(safe_value "$value")"; done
  result="$(read_field result "$file" || true)"; [ -n "$result" ] && printf 'result=%s\n' "$(safe_result "$result")"
  parent="$(read_field parentPid "$file" || true)"; child="$(read_field childPid "$file" || true)"
  printf 'parentAlive=%s\ndescendantAlive=%s\n' "$(proc_alive "$parent" && printf true || printf false)" "$(proc_alive "$child" && printf true || printf false)"
}
lifecycle_child() { while :; do sleep 30; done; }
on_term() {
  cleanup_child
  [ "$(read_field status "$(task_file "$TASK_ID")" 2>/dev/null || true)" = "CANCELLED" ] || write_state CANCELLED 130 "" cancelled_by_signal
  exit 130
}
[ "$#" -ge 2 ] || fail invalid_protocol 64
action="$1"; TASK_ID="$2"; valid_id "$TASK_ID" || fail invalid_task_id 65; mkdir -p "$TASK_ROOT"
case "$action" in
  STATUS|RESULT) [ "$#" -eq 2 ] || fail invalid_protocol 64; emit_state "$TASK_ID"; exit 0 ;;
  CANCEL)
    [ "$#" -eq 2 ] || fail invalid_protocol 64; file="$(task_file "$TASK_ID")"; [ -f "$file" ] || fail task_not_found 67
    status="$(read_field status "$file")"; PARENT_PID="$(read_field parentPid "$file" || true)"; CHILD_PID="$(read_field childPid "$file" || true)"; HEARTBEAT="$(read_field heartbeat "$file" || true)"; LAST_HEARTBEAT="$(read_field lastHeartbeat "$file" || true)"
    case "$status" in COMPLETED|FAILED|CANCELLED) ;; *) write_state CANCELLED 130 "" cancel_requested; kill_tree "$PARENT_PID" ;; esac
    emit_state "$TASK_ID"; exit 0 ;;
  RUN)
    [ "$#" -eq 3 ] || fail invalid_protocol 64; capability="$3"
    case "$capability" in "$CAPABILITY_RUNTIME_INFO"|"$CAPABILITY_RUNTIME_INFO_DELAY"|"$CAPABILITY_LIFECYCLE_PROBE"|"$CAPABILITY_CURRENT_LOCATION") ;; *) fail unsupported_capability 66 ;; esac
    file="$(task_file "$TASK_ID")"; [ ! -e "$file" ] || fail duplicate_task 68
    PARENT_PID="$$"; LAST_HEARTBEAT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; write_state RUNNING 0
    if [ "$capability" = "$CAPABILITY_LIFECYCLE_PROBE" ]; then
      trap on_term TERM INT
      lifecycle_child & CHILD_PID=$!; write_state RUNNING 0
      end=$(( $(date +%s) + 120 ))
      while [ "$(date +%s)" -lt "$end" ]; do
        [ "$(read_field status "$file")" = "CANCELLED" ] && { cleanup_child; exit 130; }
        HEARTBEAT=$((HEARTBEAT + 1)); LAST_HEARTBEAT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; write_state RUNNING 0; sleep 1
      done
      cleanup_child; output="workerProtocolVersion=$WORKER_PROTOCOL_VERSION\nworkerVersion=$WORKER_VERSION\ncapability=$CAPABILITY_LIFECYCLE_PROBE\ntaskId=$TASK_ID\nheartbeat=$HEARTBEAT\nstatus=COMPLETED"; write_state COMPLETED 0 "$output"; printf '%b\n' "$output"; exit 0
    fi
    if [ "$capability" = "$CAPABILITY_CURRENT_LOCATION" ]; then
      set +e
      location_output="$(termux-location 2>&1)"
      location_exit=$?
      set -e
      location_output="$(printf '%s' "$location_output" | cut -c 1-1024)"
      if [ "$location_exit" -ne 0 ]; then
        write_state FAILED "$location_exit" "" "termux-location failed"
        printf 'termux-location failed\n' >&2
        exit "$location_exit"
      fi
      write_state COMPLETED 0 "$location_output"
      printf '%s\n' "$location_output"
      exit 0
    fi
    [ "$capability" = "$CAPABILITY_RUNTIME_INFO_DELAY" ] && sleep 5 || true
    runtime_shell="${SHELL:-unknown}"; termux_prefix="${PREFIX:-unknown}"; kernel_summary="$(uname -srmo 2>/dev/null || printf unavailable)"; android_api="$(getprop ro.build.version.sdk 2>/dev/null || printf unknown)"; timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || printf unknown)"; output="workerProtocolVersion=$WORKER_PROTOCOL_VERSION\nworkerVersion=$WORKER_VERSION\nruntime=termux\ntaskId=$(safe_value "$TASK_ID")\nshell=$(safe_value "$runtime_shell")\ntermuxPrefix=$(safe_value "$termux_prefix")\nuname=$(safe_value "$kernel_summary")\nandroidApi=$(safe_value "$android_api")\ntimestamp=$(safe_value "$timestamp")\nstatus=COMPLETED"; write_state COMPLETED 0 "$output"; printf '%b\n' "$output"; exit 0 ;;
  *) fail unsupported_action 64 ;;
esac
