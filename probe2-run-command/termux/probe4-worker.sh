#!/data/data/com.termux/files/usr/bin/bash

# Probe-only PRoot-Distro session-PID bridge. This is not production code.
# The guest command is fixed to a harmless sleep; no arbitrary command,
# container name, or shell text is accepted.
set -u

BASE="$HOME/.era_probe4"
TASK_ROOT="$BASE/tasks"
PD="/data/data/com.termux/files/usr/bin/proot-distro"
PD_RUNTIME="/data/data/com.termux/files/usr/var/lib/proot-distro"
PD_SESSIONS="$PD_RUNTIME/sessions"
CONTAINER="debian"

fail() {
    printf 'probe4_error=%s\n' "$1" >&2
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
    local dir="$1" n=0
    while ! mkdir "$dir/.lock" 2>/dev/null; do
        n=$((n + 1))
        # START holds this lock through the detach handshake and state write.
        # A CANCEL that arrives in that window waits; it never guesses a PID.
        [ "$n" -lt 601 ] || return 1
        sleep 0.05
    done
}

release_lock() {
    rmdir "$1/.lock" 2>/dev/null || true
}

read_field() {
    local dir="$1" key="$2"
    sed -n "s/^${key}=//p" "$dir/state" 2>/dev/null | sed -n '1p'
}

write_event_unlocked() {
    local dir="$1" event="$2"
    printf '%s event=%s\n' "$(date +%s%3N)" "$event" >> "$dir/journal.log"
}

write_state_unlocked() {
    local dir="$1" tmp="$1/state.tmp.$$"
    {
        printf 'taskId=%s\n' "$TASK_ID"
        printf 'attemptId=%s\n' "$ATTEMPT_ID"
        printf 'state=%s\n' "$STATE"
        printf 'container=%s\n' "$CONTAINER"
        printf 'prootSessionPid=%s\n' "$PROOT_SESSION_PID"
        printf 'sessionStartTime=%s\n' "$SESSION_START_TIME"
        printf 'procStart=%s\n' "$PROC_START"
        printf 'identityToken=%s\n' "$IDENTITY_TOKEN"
        printf 'cancelRequested=%s\n' "$CANCEL_REQUESTED"
        printf 'result=%s\n' "$RESULT"
    } > "$tmp" || return 1
    mv -f "$tmp" "$dir/state"
}

load_state() {
    local dir="$1"
    TASK_ID="$(read_field "$dir" taskId)"
    ATTEMPT_ID="$(read_field "$dir" attemptId)"
    STATE="$(read_field "$dir" state)"
    PROOT_SESSION_PID="$(read_field "$dir" prootSessionPid)"
    SESSION_START_TIME="$(read_field "$dir" sessionStartTime)"
    PROC_START="$(read_field "$dir" procStart)"
    IDENTITY_TOKEN="$(read_field "$dir" identityToken)"
    CANCEL_REQUESTED="$(read_field "$dir" cancelRequested)"
    RESULT="$(read_field "$dir" result)"
}

proc_start_for() {
    local pid="$1"
    [ -r "/proc/$pid/stat" ] || return 1
    awk '{print $22}' "/proc/$pid/stat"
}

session_holds_fd() {
    local pid="$1" session_file="$2" wanted fd_path actual
    wanted="$(stat -c '%d:%i' "$session_file" 2>/dev/null || true)"
    [ -n "$wanted" ] || return 1
    for fd_path in /proc/$pid/fd/*; do
        [ -e "$fd_path" ] || continue
        actual="$(stat -c '%d:%i' "$fd_path" 2>/dev/null || true)"
        [ "$actual" = "$wanted" ] && return 0
    done
    return 1
}

ps_has_pid() {
    local pid="$1"
    "$PD" ps -q 2>/dev/null | sed -n "s/^${pid}$/yes/p" | sed -n '1p' | grep -qx yes
}

validate_identity() {
    local pid="$PROOT_SESSION_PID" session_file="$PD_SESSIONS/$PROOT_SESSION_PID.json"
    local json_pid json_container json_kind json_detach json_start current_start
    IDENTITY_REASON=""
    case "$pid" in ''|*[!0-9]*) IDENTITY_REASON=invalid_saved_pid; return 1 ;; esac
    [ -f "$session_file" ] || { IDENTITY_REASON=session_record_missing; return 1; }
    json_pid="$(sed -n 's/.*"pid":[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$session_file" | sed -n '1p')"
    json_container="$(sed -n 's/.*"container":[[:space:]]*"\([^"]*\)".*/\1/p' "$session_file" | sed -n '1p')"
    json_kind="$(sed -n 's/.*"kind":[[:space:]]*"\([^"]*\)".*/\1/p' "$session_file" | sed -n '1p')"
    json_detach="$(sed -n 's/.*"detach":[[:space:]]*\(true\|false\).*/\1/p' "$session_file" | sed -n '1p')"
    json_start="$(sed -n 's/.*"start_time":[[:space:]]*\([^,}]*\).*/\1/p' "$session_file" | sed -n '1p')"
    [ "$json_pid" = "$pid" ] || { IDENTITY_REASON=session_pid_mismatch; return 1; }
    [ "$json_container" = "$CONTAINER" ] || { IDENTITY_REASON=container_mismatch; return 1; }
    [ "$json_kind" = login ] || { IDENTITY_REASON=session_kind_mismatch; return 1; }
    [ "$json_detach" = true ] || { IDENTITY_REASON=not_detached_session; return 1; }
    [ "$json_start" = "$SESSION_START_TIME" ] || { IDENTITY_REASON=session_start_mismatch; return 1; }
    grep -Fq "$IDENTITY_TOKEN" "$session_file" || {
        IDENTITY_REASON=identity_token_mismatch
        return 1
    }
    current_start="$(proc_start_for "$pid" || true)"
    [ -n "$PROC_START" ] && [ "$current_start" = "$PROC_START" ] || {
        IDENTITY_REASON=proc_start_mismatch
        return 1
    }
    kill -0 "$pid" 2>/dev/null || { IDENTITY_REASON=pid_not_alive; return 1; }
    session_holds_fd "$pid" "$session_file" || {
        IDENTITY_REASON=session_fd_not_held_by_pid
        return 1
    }
    ps_has_pid "$pid" || { IDENTITY_REASON=pid_not_in_active_session_registry; return 1; }
    return 0
}

wait_not_active() {
    local pid="$1" n=0
    while ps_has_pid "$pid"; do
        n=$((n + 1))
        [ "$n" -lt 101 ] || return 1
        sleep 0.1
    done
    return 0
}

ACTION="${1:-}"
TASK_ID="${2:-}"
ATTEMPT_ID="${3:-}"
valid_id "$TASK_ID" || fail invalid_task_id
valid_id "$ATTEMPT_ID" || fail invalid_attempt_id
REQUEST_TASK_ID="$TASK_ID"
REQUEST_ATTEMPT_ID="$ATTEMPT_ID"
mkdir -p "$TASK_ROOT"
DIR="$(task_dir_for "$TASK_ID")"

case "$ACTION" in
    START)
        [ ! -e "$DIR" ] || fail task_already_exists
        mkdir "$DIR" || fail task_directory_create_failed
        acquire_lock "$DIR" || fail start_lock_timeout
        trap 'release_lock "$DIR"' EXIT
        : > "$DIR/journal.log" || fail journal_create_failed
        STATE=STARTING
        PROOT_SESSION_PID=""
        SESSION_START_TIME=""
        PROC_START=""
        IDENTITY_TOKEN="p4-${TASK_ID}-${ATTEMPT_ID}"
        CANCEL_REQUESTED=false
        RESULT=""
        write_state_unlocked "$DIR" || fail state_write_failed
        write_event_unlocked "$DIR" STARTING
        launch_stderr="$DIR/launch.stderr"
        launch_stdout="$DIR/launch.stdout"
        if ! "$PD" login "$CONTAINER" --isolated --detach -- \
                /bin/sh -c 'sleep 600' "$IDENTITY_TOKEN" \
                >"$launch_stdout" 2>"$launch_stderr"; then
            STATE=FAILED
            RESULT=detach_launch_failed
            write_state_unlocked "$DIR"
            write_event_unlocked "$DIR" FAILED
            fail detach_launch_failed
        fi
        # --detach's foreground side receives the daemon PID through the
        # proot-distro pipe and prints it as `PID: N` on stderr. This is the
        # only source used for prootSessionPid; ps is validation only.
        pid_count="$(sed -n 's/.*PID:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$launch_stderr" | wc -l | tr -d ' ')"
        [ "$pid_count" = 1 ] || {
            STATE=FAILED
            RESULT=detach_pid_output_invalid
            write_state_unlocked "$DIR"
            write_event_unlocked "$DIR" FAILED
            fail detach_pid_output_invalid
        }
        PROOT_SESSION_PID="$(sed -n 's/.*PID:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$launch_stderr" | sed -n '1p')"
        session_file="$PD_SESSIONS/$PROOT_SESSION_PID.json"
        [ -f "$session_file" ] || {
            STATE=FAILED
            RESULT=session_record_missing_after_launch
            write_state_unlocked "$DIR"
            write_event_unlocked "$DIR" FAILED
            fail session_record_missing_after_launch
        }
        SESSION_START_TIME="$(sed -n 's/.*"start_time":[[:space:]]*\([^,}]*\).*/\1/p' "$session_file" | sed -n '1p')"
        PROC_START="$(proc_start_for "$PROOT_SESSION_PID" || true)"
        [ -n "$SESSION_START_TIME" ] && [ -n "$PROC_START" ] || {
            STATE=FAILED
            RESULT=identity_capture_failed
            write_state_unlocked "$DIR"
            write_event_unlocked "$DIR" FAILED
            fail identity_capture_failed
        }
        if ! validate_identity; then
            STATE=FAILED
            RESULT="identity_capture_mismatch:$IDENTITY_REASON"
            write_state_unlocked "$DIR"
            write_event_unlocked "$DIR" "FAILED $RESULT"
            fail identity_capture_mismatch
        fi
        STATE=RUNNING
        write_state_unlocked "$DIR" || fail state_write_failed
        write_event_unlocked "$DIR" "RUNNING prootSessionPid=$PROOT_SESSION_PID source=detach-output"
        printf 'taskId=%s attemptId=%s state=RUNNING prootSessionPid=%s\n' \
            "$TASK_ID" "$ATTEMPT_ID" "$PROOT_SESSION_PID"
        ;;
    STATUS)
        [ -f "$DIR/state" ] || fail task_not_found
        load_state "$DIR"
        [ "$REQUEST_ATTEMPT_ID" = "$ATTEMPT_ID" ] || fail attempt_mismatch
        printf 'taskId=%s attemptId=%s state=%s prootSessionPid=%s cancelRequested=%s result=%s\n' \
            "$TASK_ID" "$ATTEMPT_ID" "$STATE" "$PROOT_SESSION_PID" "$CANCEL_REQUESTED" "$RESULT"
        ;;
    CANCEL)
        [ -d "$DIR" ] || fail task_not_found
        acquire_lock "$DIR" || fail cancel_lock_timeout
        trap 'release_lock "$DIR"' EXIT
        [ -f "$DIR/state" ] || { write_event_unlocked "$DIR" CANCEL_ABORTED_START_NOT_COMMITTED; fail start_in_progress_retry; }
        load_state "$DIR"
        [ "$REQUEST_ATTEMPT_ID" = "$ATTEMPT_ID" ] || fail attempt_mismatch
        case "$STATE" in
            COMPLETED|CANCELLED|FAILED)
                printf 'taskId=%s attemptId=%s state=%s prootSessionPid=%s cancelRequested=%s result=%s\n' \
                    "$TASK_ID" "$ATTEMPT_ID" "$STATE" "$PROOT_SESSION_PID" "$CANCEL_REQUESTED" "$RESULT"
                exit 0
                ;;
            STARTING)
                write_event_unlocked "$DIR" CANCEL_ABORTED_STARTING
                fail start_in_progress_retry
                ;;
            RUNNING) : ;;
            *) write_event_unlocked "$DIR" "CANCEL_ABORTED state=$STATE"; fail not_cancellable_state ;;
        esac
        if ! validate_identity; then
            write_event_unlocked "$DIR" "CANCEL_ABORTED identity_mismatch=$IDENTITY_REASON"
            fail "identity_mismatch:$IDENTITY_REASON"
        fi
        CANCEL_REQUESTED=true
        STATE=CANCELLING
        write_state_unlocked "$DIR" || fail state_write_failed
        write_event_unlocked "$DIR" "CANCEL_REQUESTED prootSessionPid=$PROOT_SESSION_PID"
        release_lock "$DIR"
        trap - EXIT
        # The only destructive operation in this probe. Never use a
        # container-name target and never use --all.
        kill_output="$DIR/kill.output"
        if "$PD" kill "$PROOT_SESSION_PID" >"$kill_output" 2>&1; then
            RESULT=kill_command_returned_zero
        else
            RESULT=kill_command_failed
        fi
        if wait_not_active "$PROOT_SESSION_PID"; then
            STATE=CANCELLED
            write_event_unlocked "$DIR" "CANCELLED prootSessionPid=$PROOT_SESSION_PID result=$RESULT"
        else
            STATE=FAILED
            RESULT=session_still_active
            write_event_unlocked "$DIR" FAILED
        fi
        write_state_unlocked "$DIR" || fail state_write_failed
        printf 'taskId=%s attemptId=%s state=%s prootSessionPid=%s cancelRequested=true result=%s\n' \
            "$TASK_ID" "$ATTEMPT_ID" "$STATE" "$PROOT_SESSION_PID" "$RESULT"
        ;;
    *)
        fail unsupported_action
        ;;
esac
