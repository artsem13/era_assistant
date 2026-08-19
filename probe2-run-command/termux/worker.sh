#!/data/data/com.termux/files/usr/bin/bash

# Probe-only fixed worker. It accepts only START, STATUS, RESULT, CANCEL.
# It never evaluates arguments as shell code and never touches project files.
set -u

BASE="$HOME/.era_probe2"
TASK_ROOT="$BASE/tasks"
mkdir -p "$TASK_ROOT"

fail() {
    printf 'worker_error=%s\n' "$1" >&2
    exit 64
}

valid_id() {
    case "$1" in
        ''|*[!A-Za-z0-9_-]*) return 1 ;;
    esac
    [ "${#1}" -le 48 ]
}

task_dir_for() {
    printf '%s/%s' "$TASK_ROOT" "$1"
}

acquire_lock() {
    local dir="$1" lock="$1/.lock" n=0
    while ! mkdir "$lock" 2>/dev/null; do
        n=$((n + 1))
        [ "$n" -lt 100 ] || return 1
        sleep 0.02
    done
}

release_lock() {
    rmdir "$1/.lock" 2>/dev/null || true
}

write_event_unlocked() {
    local dir="$1" event="$2"
    printf '%s event=%s\n' "$(date +%s%3N)" "$event" >> "$dir/journal.log"
}

write_event() {
    local dir="$1" event="$2"
    acquire_lock "$dir" || return 1
    write_event_unlocked "$dir" "$event"
    release_lock "$dir"
}

write_state_unlocked() {
    local dir="$1" next_state="$2" tmp="$1/state.tmp.$$"
    local current_task_id="" current_attempt_id="" current_pid=""
    local current_proc_start="" current_start_ts="" current_end_ts=""
    local current_exit_code="" current_cancel_requested="false"
    local current_cancel_ack="false" current_heartbeat_ts=""
    if [ -f "$dir/state" ]; then
        current_task_id="$(read_field "$dir" taskId)"
        current_attempt_id="$(read_field "$dir" attemptId)"
        current_pid="$(read_field "$dir" pid)"
        current_proc_start="$(read_field "$dir" procStart)"
        current_start_ts="$(read_field "$dir" startTs)"
        current_end_ts="$(read_field "$dir" endTs)"
        current_exit_code="$(read_field "$dir" exitCode)"
        current_cancel_requested="$(read_field "$dir" cancelRequested)"
        current_cancel_ack="$(read_field "$dir" cancelAck)"
        current_heartbeat_ts="$(read_field "$dir" heartbeatTs)"
    fi
    [ "${TASK_ID+x}" = x ] && current_task_id="$TASK_ID"
    [ "${ATTEMPT_ID+x}" = x ] && current_attempt_id="$ATTEMPT_ID"
    [ "${CHILD_PID+x}" = x ] && current_pid="$CHILD_PID"
    [ "${PROC_START+x}" = x ] && current_proc_start="$PROC_START"
    [ "${START_TS+x}" = x ] && current_start_ts="$START_TS"
    [ "${END_TS+x}" = x ] && current_end_ts="$END_TS"
    [ "${EXIT_CODE+x}" = x ] && current_exit_code="$EXIT_CODE"
    [ "${CANCEL_REQUESTED+x}" = x ] && current_cancel_requested="$CANCEL_REQUESTED"
    [ "${CANCEL_ACK+x}" = x ] && current_cancel_ack="$CANCEL_ACK"
    [ "${HEARTBEAT_TS+x}" = x ] && current_heartbeat_ts="$HEARTBEAT_TS"
    {
        printf 'taskId=%s\n' "$current_task_id"
        printf 'attemptId=%s\n' "$current_attempt_id"
        printf 'state=%s\n' "$next_state"
        printf 'pid=%s\n' "$current_pid"
        printf 'procStart=%s\n' "$current_proc_start"
        printf 'startTs=%s\n' "$current_start_ts"
        printf 'endTs=%s\n' "$current_end_ts"
        printf 'exitCode=%s\n' "$current_exit_code"
        printf 'cancelRequested=%s\n' "$current_cancel_requested"
        printf 'cancelAck=%s\n' "$current_cancel_ack"
        printf 'heartbeatTs=%s\n' "$current_heartbeat_ts"
    } > "$tmp"
    mv -f "$tmp" "$dir/state"
}

write_state() {
    local dir="$1" next_state="$2"
    acquire_lock "$dir" || return 1
    write_state_unlocked "$dir" "$next_state"
    release_lock "$dir"
}

read_field() {
    local dir="$1" key="$2"
    sed -n "s/^${key}=//p" "$dir/state" 2>/dev/null | sed -n '1p'
}

read_heartbeat_ts() {
    local dir="$1"
    [ -f "$dir/heartbeat" ] || return 0
    sed -n '1p' "$dir/heartbeat" 2>/dev/null
}

proc_start_for() {
    local pid="$1"
    [ -r "/proc/$pid/stat" ] || return 1
    awk '{print $22}' "/proc/$pid/stat"
}

wait_gone() {
    local pid="$1" n=0
    while kill -0 "$pid" 2>/dev/null; do
        n=$((n + 1))
        [ "$n" -lt 21 ] || return 1
        sleep 0.1
    done
    return 0
}

ACTION="${1:-}"
TASK_ID="${2:-}"
ATTEMPT_ID="${3:-}"
DURATION="${4:-}"
valid_id "$TASK_ID" || fail invalid_task_id
valid_id "$ATTEMPT_ID" || fail invalid_attempt_id
DIR="$(task_dir_for "$TASK_ID")"

case "$ACTION" in
    START)
        case "$DURATION" in 5|45|60) : ;; *) fail invalid_fixed_duration ;; esac
        mkdir "$DIR" 2>/dev/null || fail task_already_exists
        START_TS="$(date +%s%3N)"
        END_TS=""
        EXIT_CODE=""
        CANCEL_REQUESTED=false
        CANCEL_ACK=false
        HEARTBEAT_TS=""
        CHILD_PID=""
        PROC_START=""
        write_state "$DIR" STARTING || fail state_lock_timeout
        write_event "$DIR" "STARTING" || fail state_lock_timeout
        (
            sleep_pid=""
            stop_child() {
                [ -n "$sleep_pid" ] && kill "$sleep_pid" 2>/dev/null || true
                [ -n "$sleep_pid" ] && wait "$sleep_pid" 2>/dev/null || true
            }
            trap 'stop_child; exit 130' INT
            trap 'stop_child; exit 143' TERM
            child_start="$(date +%s%3N)"
            while [ "$(( $(date +%s%3N) - child_start ))" -lt "$((DURATION * 1000))" ]; do
                heartbeat_tmp="$DIR/heartbeat.tmp.$$"
                printf '%s\n' "$(date +%s%3N)" > "$heartbeat_tmp"
                mv -f "$heartbeat_tmp" "$DIR/heartbeat"
                sleep 2 & sleep_pid=$!
                wait "$sleep_pid"
            done
            exit 0
        ) &
        CHILD_PID=$!
        PROC_START="$(proc_start_for "$CHILD_PID" || true)"
        write_state "$DIR" RUNNING || fail state_lock_timeout
        write_event "$DIR" "RUNNING pid=$CHILD_PID" || fail state_lock_timeout
        wait "$CHILD_PID"
        EXIT_CODE=$?
        END_TS="$(date +%s%3N)"
        if acquire_lock "$DIR"; then
            CANCEL_REQUESTED="$(read_field "$DIR" cancelRequested)"
            CANCEL_ACK="$(read_field "$DIR" cancelAck)"
            heartbeat_ts_from_file="$(read_heartbeat_ts "$DIR")"
            if [ -n "$heartbeat_ts_from_file" ]; then
                HEARTBEAT_TS="$heartbeat_ts_from_file"
            else
                unset HEARTBEAT_TS
            fi
            if [ "$CANCEL_REQUESTED" = true ] || [ "$EXIT_CODE" -eq 130 ] || [ "$EXIT_CODE" -eq 143 ]; then
                state=CANCELLED
            else
                state=COMPLETED
            fi
            write_state_unlocked "$DIR" "$state"
            write_event_unlocked "$DIR" "$state exitCode=$EXIT_CODE"
            release_lock "$DIR"
        else
            fail state_lock_timeout
        fi
        printf 'taskId=%s attemptId=%s state=%s exitCode=%s\n' "$TASK_ID" "$ATTEMPT_ID" "$state" "$EXIT_CODE"
        ;;
    STATUS|RESULT)
        [ -f "$DIR/state" ] || fail task_not_found
        state="$(read_field "$DIR" state)"
        [ "$(read_field "$DIR" attemptId)" = "$ATTEMPT_ID" ] || fail attempt_mismatch
        status_heartbeat_ts="$(read_heartbeat_ts "$DIR")"
        [ -n "$status_heartbeat_ts" ] || status_heartbeat_ts="$(read_field "$DIR" heartbeatTs)"
        printf 'taskId=%s attemptId=%s state=%s pid=%s heartbeatTs=%s cancelRequested=%s cancelAck=%s exitCode=%s\n' \
            "$TASK_ID" "$ATTEMPT_ID" "$state" "$(read_field "$DIR" pid)" \
            "$status_heartbeat_ts" "$(read_field "$DIR" cancelRequested)" \
            "$(read_field "$DIR" cancelAck)" "$(read_field "$DIR" exitCode)"
        if [ "$ACTION" = RESULT ]; then
            printf 'journalEvents=%s\n' "$(wc -l < "$DIR/journal.log" 2>/dev/null || printf 0)"
        fi
        ;;
    CANCEL)
        [ -f "$DIR/state" ] || fail task_not_found
        acquire_lock "$DIR" || fail state_lock_timeout
        state="$(read_field "$DIR" state)"
        [ "$(read_field "$DIR" attemptId)" = "$ATTEMPT_ID" ] || {
            release_lock "$DIR"
            fail attempt_mismatch
        }
        if [ "$state" = COMPLETED ] || [ "$state" = CANCELLED ] || [ "$state" = FAILED ]; then
            terminal_pid="$(read_field "$DIR" pid)"
            terminal_heartbeat="$(read_field "$DIR" heartbeatTs)"
            terminal_cancel_requested="$(read_field "$DIR" cancelRequested)"
            terminal_cancel_ack="$(read_field "$DIR" cancelAck)"
            terminal_exit_code="$(read_field "$DIR" exitCode)"
            release_lock "$DIR"
            printf 'taskId=%s attemptId=%s state=%s pid=%s heartbeatTs=%s cancelRequested=%s cancelAck=%s exitCode=%s\n' \
                "$TASK_ID" "$ATTEMPT_ID" "$state" "$terminal_pid" "$terminal_heartbeat" \
                "$terminal_cancel_requested" "$terminal_cancel_ack" "$terminal_exit_code"
            exit 0
        fi
        [ "$state" = RUNNING ] || {
            cancel_requested_state="$(read_field "$DIR" cancelRequested)"
            cancel_ack_state="$(read_field "$DIR" cancelAck)"
            release_lock "$DIR"
            printf 'taskId=%s attemptId=%s state=%s cancelRequested=%s cancelAck=%s\n' \
                "$TASK_ID" "$ATTEMPT_ID" "$state" "$cancel_requested_state" "$cancel_ack_state"
            exit 0
        }
        pid="$(read_field "$DIR" pid)"
        stored_proc_start="$(read_field "$DIR" procStart)"
        current_proc_start="$(proc_start_for "$pid" || true)"
        if [ -z "$pid" ] || [ -z "$stored_proc_start" ] || [ "$current_proc_start" != "$stored_proc_start" ]; then
            write_event_unlocked "$DIR" "CANCEL_ABORTED process_identity_mismatch"
            release_lock "$DIR"
            fail process_identity_mismatch
        fi
        CANCEL_REQUESTED=true
        CANCEL_ACK=false
        heartbeat_ts_from_file="$(read_heartbeat_ts "$DIR")"
        if [ -n "$heartbeat_ts_from_file" ]; then
            HEARTBEAT_TS="$heartbeat_ts_from_file"
        else
            unset HEARTBEAT_TS
        fi
        write_state_unlocked "$DIR" CANCELLING
        write_event_unlocked "$DIR" "CANCEL_REQUESTED pid=$pid"
        if kill -INT "$pid" 2>/dev/null; then
            CANCEL_ACK=true
            write_state_unlocked "$DIR" CANCELLING
            write_event_unlocked "$DIR" "CANCEL_ACK signal=SIGINT"
        else
            write_event_unlocked "$DIR" "CANCEL_ACK signal=SIGINT result=not-delivered"
        fi
        if ! wait_gone "$pid"; then
            write_event_unlocked "$DIR" "CANCEL_ESCALATION signal=SIGTERM"
            if kill -TERM "$pid" 2>/dev/null; then
                CANCEL_ACK=true
                state="$(read_field "$DIR" state)"
                if [ "$state" = CANCELLING ]; then
                    write_state_unlocked "$DIR" CANCELLING
                    write_event_unlocked "$DIR" "CANCEL_ACK signal=SIGTERM"
                fi
            fi
            if ! wait_gone "$pid"; then
                write_event_unlocked "$DIR" "CANCEL_ESCALATION signal=SIGKILL"
                if kill -KILL "$pid" 2>/dev/null; then
                    CANCEL_ACK=true
                    state="$(read_field "$DIR" state)"
                    if [ "$state" = CANCELLING ]; then
                        write_state_unlocked "$DIR" CANCELLING
                        write_event_unlocked "$DIR" "CANCEL_ACK signal=SIGKILL"
                    fi
                fi
                wait_gone "$pid" || true
            fi
        fi
        state="$(read_field "$DIR" state)"
        if [ "$state" = CANCELLING ]; then
            write_state_unlocked "$DIR" CANCELLING
        fi
        release_lock "$DIR"
        printf 'taskId=%s attemptId=%s state=CANCELLING cancelRequested=true cancelAck=%s\n' "$TASK_ID" "$ATTEMPT_ID" "$CANCEL_ACK"
        ;;
    *)
        fail unsupported_action
        ;;
esac
