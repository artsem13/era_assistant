# Era ↔ Termux architecture passport

Current truth after the confirmed Phase 3, Phase 4, and Phase 5 implementation and real Android E2E test. Code and Git remain authoritative; this passport records only the existing, verified architecture.

## Purpose

Era may execute narrowly defined local operations through an optional external executor. The AI never receives unrestricted shell access.

## Architecture

```text
Era policy/debug entry -> ExternalCapabilityDispatcher -> ExternalExecutor
  -> TermuxExecutor -> Android RUN_COMMAND -> com.termux.app.RunCommandService
  -> ~/.era/era-worker.sh -> allowlisted capability -> PendingIntent callback
  -> TermuxResultReceiver -> TermuxResultRegistry -> parseResult()
  -> ExternalTaskResult
```

Neutral types are in `com.era.assistant.executor`; Android/Termux details are in `com.era.assistant.executor.termux`. The Phase 4 slice is exercised by the debug device-test path and is not mixed into OpenAI, archive, memory, voice, Usage, or MainActivity.

## Component map

| Path | Responsibility | Role/details |
|---|---|---|
| `app/src/main/java/com/era/assistant/executor/ExternalExecutor.kt` | `ExternalExecutor`, `ExternalTaskRequest`, `ExternalTaskHandle`, `ExternalTaskStart`, `ExternalTaskStatus`, `ExternalTaskState`, `ExternalTaskResult`, `ExternalTaskError` | Production-neutral contract |
| `app/src/main/java/com/era/assistant/executor/ExternalCapabilityDispatcher.kt` | Allowlisted `termux_runtime_info` policy boundary | Production-neutral; no command surface |
| `app/src/main/java/com/era/assistant/executor/termux/TermuxExecutor.kt` | Availability, Intent transport, callback, timeout, parsing | Production adapter; device-specific details isolated here |
| `app/src/main/java/com/era/assistant/executor/termux/TermuxExecutorConfig.kt` | Termux constants, worker path, limits and protocol | Adapter configuration |
| `app/src/main/java/com/era/assistant/executor/termux/TermuxResultReceiver.kt` | PendingIntent receiver and Bundle handoff; registry | Production callback bridge |
| `app/src/main/java/com/era/assistant/executor/termux/TermuxDiagnosticSink.kt` | Narrow diagnostic interface | Used by debug diagnostics only |
| `app/src/debug/java/com/era/assistant/executor/termux/TermuxDeviceTestActivity.kt` | Runs runtime info and copies bounded output | Debug-only |
| `app/src/main/AndroidManifest.xml` | RUN_COMMAND permission and non-exported receiver | Android boundary |
| `app/src/debug/AndroidManifest.xml` | Registers diagnostic Activity | Debug-only |
| `termux/era-worker.sh` | Fixed worker protocol/capability | Installed in Termux; not APK code |
| `app/src/test/java/com/era/assistant/executor/ExternalExecutorTest.kt` | Contract/status smoke tests | Static/unit test |

`MainActivity.kt` is unchanged by this slice. The absolute Termux path is not a neutral-core dependency.

## Transport contract

Package: `com.termux`; service: `com.termux.app.RunCommandService`; action: `com.termux.RUN_COMMAND`; permission: `com.termux.permission.RUN_COMMAND`. The adapter sends `/data/data/com.termux/files/home/.era/era-worker.sh`, arguments `RUN`, task ID, capability ID, background execution, and a unique one-shot PendingIntent.

The confirmed callback contains nested `result` → `Bundle`; the receiver also tolerates a top-level Bundle. Confirmed fields are `err`, `errmsg`, `exitCode`, `stdout`, `stderr`, `stdout_original_length`, and `stderr_original_length`. Parser completion requires `err` and `exitCode`, validates the fixed output, and bounds result text. The nested shape is compatibility handling, not an unconditional promise about every Termux version.

## Worker protocol

Protocol version `1`, worker version `1.2.0`; actual current form:

```text
era-worker.sh RUN    TASK_ID CAPABILITY       # exactly 3 arguments
era-worker.sh STATUS TASK_ID                  # exactly 2 arguments
 era-worker.sh RESULT TASK_ID                 # exactly 2 arguments
 era-worker.sh CANCEL TASK_ID                 # exactly 2 arguments
```

`RUN` is the legacy Phase 3/4 action; controls operate on its task state. Task IDs accept non-empty `[A-Za-z0-9._:-]`; capabilities are allowlisted; values are bounded to 160 characters. Exit codes: `0` success, `64` invalid protocol/unsupported action, `65` invalid task ID, `66` unsupported capability, `67` task not found, `68` duplicate task. RUN success is bounded line-oriented output with `workerProtocolVersion=1`, `workerVersion=1.1.0`, runtime fields and `status=COMPLETED`; control responses are bounded state snapshots with `protocolVersion=1`, `taskId`, `status`, `exitCode`, and optional `result`/`error`. The planned `START taskId attemptId capabilityId boundedArgs` shape is not the current CLI.

## Capability registry

| ID | Arguments | Side effects/classification | Implementation | Device test |
|---|---|---|---|---|
| `termux_runtime_info` | none | Read-only, bounded runtime metadata; low risk | dispatcher, `TermuxExecutorConfig`, `termux/era-worker.sh` | PASS |
| `termux_lifecycle_probe` | none | Debug-only fixed 120-second heartbeat task; private state; identity-scoped cancellation | debug Activity, `TermuxExecutorConfig`, `termux/era-worker.sh` | IMPLEMENTED; device test required |

`ARBITRARY_SHELL = NOT EXPOSED`; unknown capability policy is `DENY BY DEFAULT`. Every new capability must be added to this table and both validation layers before device PASS.

## Security model

Trust boundaries: `AI/Era core → neutral executor → Termux adapter → Android permission boundary → fixed worker → local tools`. No arbitrary command, executable, path, environment, or script may cross it. Worker and adapter validate IDs, output/result parsing is bounded, diagnostics must not expose secrets, and debug hooks are not production APIs. Capability arguments must never become a hidden shell channel. Boundary changes require an explicit security decision.

## Android / Termux prerequisites

Termux installed; `RunCommandService` resolvable; `com.termux.permission.RUN_COMMAND` granted to Era; `allow-external-apps=true`; worker installed at `~/.era/era-worker.sh`. Current Android path is `/data/data/com.termux/files/home/.era/era-worker.sh`. Package/UI/lifecycle/OEM behavior is adapter or installation-layer state. TECNO/HiOS is not architecture.

## Portability model

The phone is a temporary host. Termux is an optional adapter/executor, not an Era core dependency or AI/core property. A new phone or another executor must not require core rewrites. Permissions, package quirks, UI, absolute paths, background restrictions, and lifecycle are device-specific. Termux task state is not portable Era data and is not in conversation SQLite.

## Result contract

Current `ExternalTaskState`: `CREATED`, `STARTING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `UNAVAILABLE`, `SUSPENDED_OR_UNREACHABLE`. Accepted/running states never prove completion; `COMPLETED` requires validated output; `FAILED` covers request/worker/transport/invalid-result failure; `UNAVAILABLE` covers host configuration; `SUSPENDED_OR_UNREACHABLE` covers bounded callback timeout. Cancellation is terminal-safe/idempotent worker state control. The diagnostic `termux_lifecycle_probe` additionally records parent/descendant PIDs and kills only that recorded process tree; production capabilities retain their existing control behavior. After a confirmed `CANCELLED` control result, the adapter treats that state as authoritative for late RUN callbacks and RESULT observations. Timeout is capped at 15 seconds; no durable task store exists.

`ExternalTaskStart(RUNNING)` means that `RunCommandService.startService()` accepted the worker launch; it does not promise that the worker has already persisted its `.state` file. `TermuxExecutor.getStatus()`, `getResult()`, and `cancelTask()` therefore retry `task_not_found` only for a taskId recorded as just launched by that executor, with 3 bounded retries at 50 ms. Other task IDs retain immediate unknown-task failure semantics. A confirmed `CANCELLED` response records cancellation authority before the callback is delivered, so late RUN/RESULT observations remain terminally `CANCELLED`. Diagnostics classify the outcome as `just_launched_registration_exhausted` or `unknown_task` and expose CANCEL retry/success plus final STATUS/RESULT observations.

## Debug diagnostics

`TermuxDeviceTestActivity` is debug-only and registered only by the debug manifest. Its Probe 2 button runs the fixed `termux_lifecycle_probe`, observes STATUS after heartbeats, requests CANCEL, then queries final STATUS/RESULT; output includes bounded heartbeat, private-journal marker, PID liveness, callback IDs, and late-result rejection classification. The current neutral protocol has taskId identity but no attemptId. Open it in a debug build, use either lifecycle button, then `COPY DIAGNOSTIC OUTPUT`. It reports bounded availability, RUN launch, each control action/task ID/args array, callback Bundle and parsed result details. Normal lifecycle (`RUN → STATUS → RESULT`) and cancellation (`RUN → CANCEL → STATUS → RESULT`) are separate diagnostic flows. STATUS and RESULT registration races are logged separately from unknown task IDs, including bounded retry attempts and final classification. It must not silently become production UI or AI API.

## Confirmed tests

Real Android 16 / API 36 E2E: Termux package, service resolution, granted permission, external-app setting, service start, worker acceptance, callback, nested Bundle, `err=-1`, `exitCode=0`, stdout, parser and `ExternalTaskResult(COMPLETED)` all PASS. Direct worker runtime-info PASS; unknown capability rejection PASS with exit 66.

```text
PHASE3_COMPLETE=YES
PHASE4_COMPLETE=YES
PHASE5_COMPLETE=YES
PHASE5_DEVICE_TEST=PASS
PROBE2_IMPLEMENTED=YES
PROBE2_DEVICE_TEST_REQUIRED=YES
PROBE2_COMPLETE=NO
RUN_TRANSPORT=PASS
WORKER_EXECUTION=PASS
STATUS=PASS
RESULT=PASS
RUN_CALLBACK=PASS
FINAL_WORKER_STATUS=COMPLETED
EXIT_CODE=0
TERMUX_ERR=-1
STATUS_REGISTRATION_RACE_HANDLED=YES
RESULT_REGISTRATION_RACE_HANDLED=YES
CANCEL_REGISTRATION_RACE_HANDLED=YES
CANCEL_TERMINAL_AUTHORITY=PASS
SECURITY_BOUNDARY_PRESERVED=YES
PORTABILITY_PRESERVED=YES
```

## Worker deployment rule

`termux/era-worker.sh` in the repository and the installed `$HOME/.era/era-worker.sh` are different copies. After any repository worker change, update the installed copy before a device test. APK build does not update the installed worker.

Safe manual deployment:

```sh
cp /mnt/sdcard/Era/Era_From_Zip/termux/era-worker.sh ~/.era/era-worker.sh
chmod +x ~/.era/era-worker.sh
bash -n ~/.era/era-worker.sh
```

## Known limitations

- The production dispatcher exposes only `termux_runtime_info`; Probe 2 is debug-only and arbitrary shell is intentionally absent.
- Short-operation cancellation and durable external task storage are not implemented. Probe 2 process-tree and late-callback claims remain device-test pending.
- Permission/configuration/worker installation/callback reachability depend on each host.
- Worker installation is separate from the Era APK; diagnostics are not production capability UI.

## Recovery / installation

APK/repository supply the neutral contract, adapter, receiver, permission, and debug diagnostics. On another host install Termux, enable external apps, grant RUN_COMMAND, install `termux/era-worker.sh` as `~/.era/era-worker.sh`, then run the checklist. Android permissions and Termux settings are not portable Era user data.

## Verification checklist

1. `bash -n termux/era-worker.sh`.
2. `~/.era/era-worker.sh RUN test-001 termux_runtime_info` and verify exit 0.
3. Unknown capability and verify exit 66.
4. `git diff --check` and executor unit tests.
5. `bash tools/build-debug.sh`.
6. Debug Activity → runtime info → parsed `ExternalTaskResult.state=COMPLETED`.
7. Reconfirm no arbitrary shell, unbounded output, or portability leak.

## Change log

- Phase 3: RUN_COMMAND transport, bounded worker, corrected callback/result parsing, and real Android E2E confirmed.
- Phase 4: neutral dispatcher for the fixed `termux_runtime_info` capability and debug-only device path.

- Phase 5: STATUS/RESULT/CANCEL controls, recently-launched registration-race retries, authoritative CANCELLED semantics, and confirmed real-device E2E PASS.
- Probe 2 implementation: fixed long-running heartbeat capability, private PID journal fields, identity-scoped process-tree cancellation, bounded debug flow, and late-result rejection logging; device confirmation remains pending.
