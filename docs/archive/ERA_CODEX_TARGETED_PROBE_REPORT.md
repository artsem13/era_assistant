# ERA_CODEX_TARGETED_PROBE_REPORT

Date: 2026-08-17 UTC

## Scope and safety

This continuation did not modify production source, Termux configuration,
Codex auth, or any Search, Voice, Memory, RAW, or Usage implementation.
No commit, push, reset, checkout, restore, clean, or build was performed.

The pre-existing dirty Git state is the baseline for this continuation. The
pre-existing production edits are not attributed to these probes.

## Restored environment snapshot

From the existing architecture-audit context:

- Android 16 / API 36
- TECNO CM5, arm64/aarch64
- Termux 0.118.3, versionCode 1002
- termux-tools 1.45.0
- Termux:API 0.53.0, versionCode 1002
- proot-distro 5.6.0
- Debian 13.6 trixie
- Codex CLI 0.147.0
- canonical workspace `/mnt/sdcard/Era/Era_From_Zip`
- Era compile/target SDK 29
- `CODEX_HOME` was not inspected for auth contents

Current runtime confirmation was limited to version/path and kernel metadata:
`codex-cli 0.147.0`, `/data/data/com.termux/files/usr/bin/proot-distro`,
Linux `6.17.0-PRoot-Distro` on aarch64. The current shell did not expose
Termux environment variables.

## Runtime observations

### Pre-existing unexpected Killed

The prior continuation stopped after an `unexpected Killed` observation. It is
recorded as a standalone runtime observation only. No OOM, Android LMK,
phantom-process, OEM/HiOS, Termux-service, or Codex-crash classification is
made: no signal record, RSS/memory sample, logcat/system evidence, or supervisor
journal was available.

### Probe 1 — sandbox enforcement matrix

Status: **INCONCLUSIVE / BLOCKED BY RUNTIME**.

In a disposable `/tmp` fixture, the installed built-in read-only profile
`:read-only` was accepted. Workspace read, outside read, outside write, and
network checks all returned no output with exit code 182. A separate `/bin/true`
control showed that the outer shell survived and recorded the child result as
`post_sandbox_shell_rc=182`.

This proves only that the current `codex sandbox` invocation fails in this
runtime. It does not prove an escape, sandbox denial, OOM, Android kill, or
HiOS kill. The sandbox enforcement success criterion therefore remains open.

An initial attempt with profile name `read-only` failed earlier with the CLI
configuration error `default_permissions requires a [permissions] table`; the
built-in `:read-only` retry removed that configuration ambiguity but still did
not produce a sandboxed canary result.

### Probe 2 — RUN_COMMAND lifecycle/control

Status: **PARTIALLY VERIFIED — SNAPSHOT FIX PENDING MANUAL RETEST**.

The device run verified normal START execution, a durable private journal, live
CANCEL while the process was still alive, and successful SIGINT termination
with terminal state `CANCELLED` and exit code 130. The callback is not treated
as authoritative; journal and final state are the evidence. The late-cancel
race from the previous run was reproduced earlier and fixed.

This run also discovered two snapshot inconsistencies: `cancelAck=false` after
a journaled `CANCEL_ACK signal=SIGINT`, and an empty `heartbeatTs` despite an
existing heartbeat file. The isolated worker snapshot fix is pending manual
retest; Probe 2 is not fully VERIFIED until that retest succeeds.

### Probes 3–5

Not run after reaching the required manual setup boundary:

- killed/resume;
- background endurance and diagnostics;
- output/path security.

Their results remain unknown; no pass is inferred from the Probe 1 runtime
failure or the inherited `unexpected Killed` observation.

## Manual action required before Probe 2 can continue

On the target device, the user must manually:

1. In Termux, set `allow-external-apps=true` in
   `~/.termux/termux.properties` and reload Termux settings.
2. Grant `com.termux.permission.RUN_COMMAND` to a dedicated disposable probe
   sender, or provide an already-installed sender with that declaration.
3. Ensure the fixed harmless worker used for the probe is explicitly identified
   and writes only a Termux-private heartbeat/journal.
4. Start the test from a visible user action; do not grant arbitrary shell
   execution and do not change Era production code for this continuation.

If no disposable sender exists, Probe 2 is blocked until the user explicitly
provides one or separately authorizes a test-only manifest/sender change. After
manual setup, continue with STATUS → CANCEL and record callback, journal,
process-tree, and late-result evidence before attempting Probes 3–5.

## Git and migration

Production source was not edited by this continuation. The new file itself is a
non-production probe report and remains untracked until the user decides what
to do with it. No subsystem passport was updated. Migration impact: none.


## Probe 4 — addressable PRoot session PID

Status: **PREPARED / DEVICE CRITICAL TEST PENDING**.

The isolated artifact is `probe2-run-command/termux/probe4-worker.sh`. It is
not production bridge code and does not modify production `app/`,
`MainActivity`, or any production manifest. Its guest command is fixed to a
harmless 600-second sleep; it accepts only `START`, `STATUS`, and `CANCEL`
with bounded task and attempt identifiers.

### Mechanism selected

The selected launch is:

~~~sh
proot-distro login debian --isolated --detach -- /bin/sh -c 'sleep 600' <identity-token>
~~~

In the installed local package `proot-distro-5.6.0`, `--detach` uses an
internal pipe from the detached grandchild back to the foreground launcher.
The detached process registers the session immediately before `exec` into
PRoot, writes its own PID to that pipe, and the foreground command prints
`PID: <N>` on stderr. Probe 4 captures that launch stderr and accepts exactly
one decimal `PID:` record. It never discovers the PID from `proot-distro ps`.

`--get-proot-cmd` only prints the assembled command and exits without creating
a session. `run --detach` uses the same detach path but is not selected
because `run` requires an OCI image entrypoint/Cmd; Debian is exercised
through `login --detach`. `proot-distro ps -q` is used only after PID capture
for validation and postcondition checks.

### State and identity validation

After launch PID capture, Probe 4 atomically saves these task-local fields in
`~/.era_probe4/tasks/<taskId>/state`:

~~~text
prootSessionPid=<PID>
sessionStartTime=<registry start_time>
procStart=<Linux /proc/<PID>/stat starttime>
identityToken=p4-<taskId>-<attemptId>
~~~

Before CANCEL, all of the following must match; otherwise the worker returns
an identity mismatch and does not signal anything:

- saved PID is decimal and the registry file
  `/data/data/com.termux/files/usr/var/lib/proot-distro/sessions/<PID>.json`
  exists;
- registry `pid`, `container=debian`, `kind=login`, `detach=true`, and
  `start_time` match saved state;
- registry command contains the task-specific identity token;
- `/proc/<PID>/stat` starttime equals saved `procStart`;
- PID is alive and still appears in `proot-distro ps -q`;
- the registry file inode is held by an open descriptor of the saved PID.

The inode check uses the session registry lock inherited by proot. A recycled
PID does not hold the old registry inode. Saved process starttime, registry
start time, and task token provide additional stale-state protection.

CANCEL has exactly one destructive call:

~~~sh
proot-distro kill "$prootSessionPid"
~~~

It does not use a container-name target and never uses `--all`.

### START/CANCEL race

START creates the task directory, acquires its lock, writes `STARTING`,
launches the detached session, captures and validates the PID, and commits
`RUNNING` with `prootSessionPid` before releasing the lock. CANCEL waits on the
same task lock. A CANCEL arriving after session creation but before PID/state
commit therefore waits for the commit and then validates the saved PID; it
never guesses a PID. If START fails before commit, CANCEL returns a safe
start/lock error and does not signal. A bounded lock timeout is also a safe
failure, not a fallback to `ps`.

### Stale state and PID reuse

A terminal task is never killed again. A later CANCEL returns the terminal
state before any kill command. A stale non-terminal state must pass the task
token, session registry start time, `/proc` starttime, registry inode
ownership, and active-session checks. Any mismatch aborts without signalling.
The manual test exercises the late-CANCEL terminal guard and an unrelated
parallel session; actual numeric PID reuse remains a separate stress case and
must not be claimed from one run.

### Device evidence obtained before the critical test

Read-only inspection on the target runtime confirmed:

- package metadata reports `proot-distro` version `5.6.0`;
- executable path is `/data/data/com.termux/files/usr/bin/proot-distro`;
- installed modules implement `login --detach`, `spawn_detached`, the
  session registry under `.../var/lib/proot-distro/sessions`, and PID-targeted
  kill;
- prior manual device evidence remains valid: session PID 5338 with node 5343
  and Codex child 5359 was terminated by `proot-distro kill 5338`, while
  `proot-distro kill --all` was not used.

No Probe 4 A/B device run has been performed. Parallel-session isolation, new
worker state contents, race outcome, and new cancellation process-tree result
are not device-confirmed. The fixed Probe 4 guest is a controlled process tree
rather than Codex itself; Codex-specific behavior after this PID handshake
remains unconfirmed.

### Exact manual critical test

Run these commands in one native Termux shell, not inside Debian/PRoot. Do not
kill by container name and do not use `--all`.

~~~sh
set -u
W=/data/data/com.termux/files/home/.era_probe4/worker.sh
mkdir -p /data/data/com.termux/files/home/.era_probe4/tasks
cp /sdcard/Era/Era_From_Zip/probe2-run-command/termux/probe4-worker.sh "$W"
chmod 700 "$W"

# Control session A, created first and intentionally left alive.
A_LOG=/data/data/com.termux/files/home/.era_probe4/control-A.stderr
/data/data/com.termux/files/usr/bin/proot-distro login debian --isolated --detach -- \
  /bin/sh -c 'sleep 600' probe4-control-A \
  >/data/data/com.termux/files/home/.era_probe4/control-A.stdout 2>"$A_LOG"
A_COUNT=$(sed -n 's/.*PID:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$A_LOG" | wc -l | tr -d ' ')
[ "$A_COUNT" = 1 ]
A_PID=$(sed -n 's/.*PID:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$A_LOG" | sed -n '1p')
printf 'A_PID=%s\n' "$A_PID"
/data/data/com.termux/files/usr/bin/proot-distro ps

# Session B is created by the isolated worker; state is the PID source.
TASK=p4-$(date +%s)
"$W" START "$TASK" a1 | tee "$HOME/.era_probe4/${TASK}-start.out"
B_PID=$(sed -n 's/^prootSessionPid=//p' "$HOME/.era_probe4/tasks/$TASK/state" | sed -n '1p')
printf 'B_PID=%s\n' "$B_PID"
cat "$HOME/.era_probe4/tasks/$TASK/state"
/data/data/com.termux/files/usr/bin/proot-distro ps

# Confirm A and B are simultaneously registered before CANCEL.
/data/data/com.termux/files/usr/bin/proot-distro ps -q | grep -qx "$A_PID"
/data/data/com.termux/files/usr/bin/proot-distro ps -q | grep -qx "$B_PID"

# Cancel only B and preserve worker evidence.
"$W" CANCEL "$TASK" a1 | tee "$HOME/.era_probe4/${TASK}-cancel.out"
cat "$HOME/.era_probe4/tasks/$TASK/journal.log"
cat "$HOME/.era_probe4/tasks/$TASK/kill.output"

# PASS checks: B is gone and A remains.
! /data/data/com.termux/files/usr/bin/proot-distro ps -q | grep -qx "$B_PID"
/data/data/com.termux/files/usr/bin/proot-distro ps -q | grep -qx "$A_PID"
cat "$HOME/.era_probe4/tasks/$TASK/state"

# Late repeated CANCEL must be terminal/no-op and A must remain.
"$W" CANCEL "$TASK" a1 | tee "$HOME/.era_probe4/${TASK}-late-cancel.out"
/data/data/com.termux/files/usr/bin/proot-distro ps -q | grep -qx "$A_PID"
~~~

For the START/CANCEL race, use a fresh task and launch START in the
background. Wait until its `state` file exists and says `state=STARTING`, then
launch CANCEL before START returns:

~~~sh
TASK=p4-race-$(date +%s)
"$W" START "$TASK" a1 >"$HOME/.era_probe4/${TASK}-start.out" 2>"$HOME/.era_probe4/${TASK}-start.err" &
START_JOB=$!
while [ ! -f "$HOME/.era_probe4/tasks/$TASK/state" ]; do sleep 0.05; done
"$W" CANCEL "$TASK" a1 >"$HOME/.era_probe4/${TASK}-cancel.out" 2>"$HOME/.era_probe4/${TASK}-cancel.err" &
CANCEL_JOB=$!
wait "$START_JOB" || true
wait "$CANCEL_JOB" || true
cat "$HOME/.era_probe4/tasks/$TASK/state"
cat "$HOME/.era_probe4/tasks/$TASK/journal.log"
~~~

Expected safe result: CANCEL does not report an identity mismatch or kill an
unrelated PID; it either waits and cancels the committed B session, or returns
an explicit bounded start/lock error. In both cases A must remain in
`proot-distro ps -q`. If the race test leaves B running, read its
`prootSessionPid` from that task state, validate the state, and invoke only the
worker `CANCEL` for that same task.

After all evidence is saved, clean up only the explicitly recorded control
session:

~~~sh
/data/data/com.termux/files/usr/bin/proot-distro kill "$A_PID"
~~~

### Probe 4 result

- Mechanism selection: **PASS by local 5.6.0 source inspection; device output pending**.
- PID saved from launch mechanism rather than last `ps` row: **implemented;
  device evidence pending**.
- Identity validation and PID-reuse defense: **implemented; device mismatch
  and reuse stress evidence pending**.
- START/CANCEL race handling: **implemented with per-task commit lock; device
  evidence pending**.
- Parallel A/B isolation and B process-tree teardown: **NOT YET RUN**.
- Codex-specific teardown in the new probe: **NOT YET RUN**.

No production implementation was made. No production source, production
manifest, `MainActivity`, Gradle configuration, dependency, or subsystem
passport was changed. Migration impact: none.

## Probe 4 source references

- Installed package metadata: `/data/data/com.termux/files/usr/lib/python3.14/site-packages/proot_distro-5.6.0.dist-info/METADATA`
- Installed detach implementation: `/data/data/com.termux/files/usr/lib/python3.14/site-packages/proot_distro/commands/login/detach.py`
- Installed session registry: `/data/data/com.termux/files/usr/lib/python3.14/site-packages/proot_distro/session.py`
- Installed PID-targeted teardown: `/data/data/com.termux/files/usr/lib/python3.14/site-packages/proot_distro/commands/kill.py`
- Upstream documentation: https://github.com/termux/proot-distro/blob/master/README.md


## Probe 4 follow-up after unexpected Killed

The previous `unexpected Killed` is classified only as **UNKNOWN runtime termination**. No OOM, Android LMK, phantom-process, OEM/HiOS, Termux-service, or Codex-crash classification is made.

New device evidence: detached session B PID was captured correctly; identity validation failed with `identity_token_mismatch`; the worker failed closed and did not execute `CANCEL`; after a full restart, the old PIDs were no longer valid.

Exact cause: `proot-distro` stores the normal Debian guest command as one `shlex.join(login_cmd)` string inside the JSON `command` array. The old worker searched for `"<token>"`, which requires the token to be a standalone JSON string element. The token was embedded in the command string, so the assertion always missed it. The worker now checks the task-derived token as a fixed literal substring; all other identity checks and fail-closed behavior are unchanged.

This worker/report-only correction was not device-tested automatically. Manual retest must start from a full restart and fresh task/session IDs.
