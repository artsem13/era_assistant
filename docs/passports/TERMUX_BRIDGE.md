# Era ↔ Termux bridge passport

**Status:** authoritative current bridge description. Code remains the source of truth. This passport deliberately excludes the abandoned Era → Codex architecture.

## Current architecture

```text
Era typed capability
  → ExternalCapabilityDispatcher
  → TermuxExecutor
  → official com.termux.RUN_COMMAND / RunCommandService
  → ~/.era/era-worker.sh
  → private task state under ~/.era/tasks/
  → RUN / STATUS / RESULT / CANCEL
  → TermuxResultReceiver / registry
  → bounded parsed result
```

`ExternalExecutor` is neutral and contains no shell/process representation. `TermuxExecutor` owns Android/Termux details. The worker is a fixed allowlisted script; no arbitrary shell command, executable path, environment or filesystem request crosses the boundary.

## Components and current constants

- `ExternalExecutor.kt`, `ExternalCapabilityDispatcher.kt`: neutral contract and allowlisted dispatch.
- `TermuxExecutor.kt`, `TermuxExecutorConfig.kt`, `TermuxResultReceiver.kt`: transport, callback, control and parsing.
- `termux/era-worker.sh`: repository worker; installed copy is `/data/data/com.termux/files/home/.era/era-worker.sh`.
- `TermuxDeviceTestActivity`: debug-only diagnostic UI, not a production API.
- Protocol version: `1`. **Current repository worker version: `1.3.0`** (`WORKER_VERSION` in the script).
- Current allowlist: `termux_runtime_info`, `termux_runtime_info_delay`, `termux_lifecycle_probe`, `get_current_location`; only the runtime and location paths are production-facing through the dispatcher, while lifecycle probing is debug-only.

## Worker protocol

```text
era-worker.sh RUN    TASK_ID CAPABILITY
era-worker.sh STATUS TASK_ID
era-worker.sh RESULT TASK_ID
era-worker.sh CANCEL TASK_ID
```

Task IDs are restricted to `[A-Za-z0-9._:-]`. The worker writes a private state file at `~/.era/tasks/<taskId>.state` with task status, exit code, parent/child identifiers, heartbeat, journal marker and bounded result/error. `RUN` starts a fixed capability; `STATUS` and `RESULT` read that state; `CANCEL` records terminal cancellation and terminates the recorded process tree where applicable.

The Android adapter sends `RUN_COMMAND` with the fixed worker path, the typed argument array and a one-shot `PendingIntent`. Results may arrive in Termux's nested `result` Bundle or the tolerated top-level form. The parser requires exit/error fields, validates output and bounds it.

## Lifecycle and authority

`RUNNING` means Termux accepted the launch, not that the worker state file is already registered. `STATUS`, `RESULT` and `CANCEL` retry `task_not_found` only for a task recorded as just launched: three retries with 50 ms delay. Unknown task IDs retain immediate failure semantics.

The callback can be lost or time out. Transport timeout is capped at 15 seconds; callers reconcile with `STATUS`/`RESULT`, and the location capability polls for up to 45 seconds with bounded control reconciliation. Once `CANCEL` returns `CANCELLED`, the adapter records cancellation as terminal authority and maps late RUN/RESULT observations to `CANCELLED`.

Output is bounded in both layers: worker values are clipped (state fields 160 chars, result 1024 chars), and adapter parsing applies its 2048-character result bound. Task files are private Termux state, not Era user memory.

## `get_current_location`

```text
typed capability
  → ExternalCapabilityDispatcher
  → TermuxExecutor / fixed worker
  → termux-location (Termux:API optional dependency)
  → JSON output
  → TermuxLocationCapability validation and parsing
  → LocationCapabilityResult
```

The parser requires valid latitude/longitude ranges and accepts optional accuracy, provider, altitude, bearing, speed and elapsed time. Device test is confirmed `DEVICE_PASS`: `state=COMPLETED`, valid coordinates, accuracy and `provider=gps`. Coordinates are intentionally absent from this document.

## Preconditions, deployment and security

Termux must be installed, `RunCommandService` resolvable, `com.termux.permission.RUN_COMMAND` granted, external app execution enabled, and the worker deployed at `~/.era/era-worker.sh`. `Termux:API` is an optional adapter dependency required by the location capability, not by the neutral core.

After changing `termux/era-worker.sh` in the repository, the user must deploy that copy to `~/.era/era-worker.sh` before a device test. The repository copy and installed copy are independent. Do not put deployment paths or OEM behavior into core.

## Reliability and portability

The current TECNO/HiOS host can suspend or delay background Termux work and callbacks. It is a temporary host, not a guarantee of durable background execution. Termux is an optional trusted local capability executor, not a filesystem sandbox, not a PRoot/Codex host, and not a portable data store. New executors must be able to replace it behind the neutral contract.

## Future agent mode (planned)

Future Era agent mode is planned to use this existing Termux bridge and `RUN_COMMAND` infrastructure as its transport/executor; no parallel bridge is defined for that purpose.

## Required verification

For bridge changes: targeted source read-back, `bash -n termux/era-worker.sh`, and `git diff --check`. Device tests must separately verify worker deployment, permission/availability, callback, `RUN → STATUS → RESULT`, cancellation and any changed capability. Do not claim a device pass from static inspection.
