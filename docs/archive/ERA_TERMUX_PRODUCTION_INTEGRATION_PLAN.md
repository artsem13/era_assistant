# ERA TERMUX PRODUCTION INTEGRATION PLAN

Audit scope: targeted architecture audit of the production `app/` and the
already-tested `probe2-run-command/` bridge. This is a design report only.
No production integration code, manifest change, probe, build, commit, or push
was performed.

## 1. Executive verdict

`TERMUX_INTEGRATION_READY:` **YES as an optional adapter design; NO as a
production runtime feature today.** The Android → Termux `RUN_COMMAND` path,
fixed worker, task journal and bounded control protocol have already been
experimentally exercised by the separate probe. The production APK does not
currently contain the bridge.

`PRODUCTION_CODE_ALREADY_COUPLED:` **NO.** Production `app/src/main` has no
Termux, PRoot, Debian, `RUN_COMMAND`, shell or Termux path reference. Termux
references are limited to build-environment instructions and the disposable
probe.

`MAIN_ACTIVITY_IMPACT:` **Composition/wiring only.** The eventual change should
instantiate a neutral executor/tool coordinator and pass it to the existing
message orchestration boundary. No Termux Intent, worker protocol, task state
machine or model reasoning belongs in `MainActivity.kt`.

`PORTABILITY_IMPACT:` **Preserved.** Termux is one optional local executor;
absence must produce an ordinary unavailable/unsupported result. A future
native, remote or other local executor must be selectable in composition code
without changing conversation/archive/OpenAI logic.

`SECURITY_MODEL:` **Capability/action allowlist.** ERA may request a named,
validated capability with bounded typed arguments. Only the Termux adapter maps
that capability to a fixed worker action. There is no `executeShell(command)`
surface.

`RECOMMENDED_IMPLEMENTATION_SCOPE:` **V0.1: a small neutral task contract,
one Termux adapter, the existing fixed-worker transport, and one harmless
bounded capability. Defer autonomous Codex, PRoot session management,
approval relay, recovery orchestration, and a large tool framework.**

The portability architecture and portability audit remain the source of the
boundary: core must not depend on Termux, PRoot, Debian, Codex, host paths or
OEM behavior. This plan does not reopen the completed portability audit or
propose a portable data package.

## 2. Current ERA request/response architecture

### Production request path

The current production path is:

```text
MainActivity UI send button
  or VoiceModeController.onVoiceMessage
    -> MainActivity.sendTextToSphere(text, source)
    -> ConversationArchive.saveUserMessage()
    -> memoryEmbeddingIndexer.indexMissingAsync()
    -> memoryRetriever.retrieve()
    -> SearchOrchestrator.run() [optional xAI search/evidence]
    -> MainActivity.sendOpenAiWithInstructions()
    -> StreamingResponseController
    -> OpenAiStreamingClient
    -> POST https://api.openai.com/v1/responses, stream=true
    -> SSE response.output_text.delta events
    -> MainActivity updates the live assistant bubble
    -> response.completed / authoritative final response
    -> ConversationArchive.saveAssistantMessage()
    -> RawBlockCoordinator.onAssistantMessageSaved()
    -> saveSessionUsage(response)
    -> final UI state and Voice/TTS callbacks
```

The text entry handler is `sendMessageToSphere()` in `MainActivity.kt`. Voice
uses the same path through the callback created for `VoiceModeController`.
`MainActivity` currently owns the orchestration state (`isSendingMessage`,
`sendGeneration`, active search/stream handles), UI rendering and composition
of memory/search/OpenAI controllers.

### OpenAI and response state

`OpenAiStreamingClient` owns the HTTP/SSE transport and a process-local
`previousResponseId`, which it places in the next Responses request. It parses
streaming deltas and treats the final `response.completed` object as
authoritative; an incomplete connection is an error and is not archived as a
complete assistant message. `StreamingResponseController` is a thin callback
boundary around that client.

This is important for the executor design: the current OpenAI client is a
transport for the model response, not a tool dispatcher. Adding Termux details
there would couple the Responses protocol to one external runtime and would
also make non-OpenAI or future executor use unnecessarily difficult.

### Conversation and RAW/archive flow

`ConversationSessionManager` persists the current conversation ID in
`SharedPreferences`. `ConversationRestoreController` loads the current
conversation and last message ID from `ConversationArchive`.

`ConversationArchive` stores user and assistant messages in SQLite. The user
message is persisted before retrieval/OpenAI work starts; the assistant message
is persisted only after a valid completed response. Successful archive writes
trigger `LocalMemoryBackup`. `RawBlockCoordinator` is notified after the
assistant message is saved. Existing memory, Research Notes, Usage, voice and
RAW behavior should remain unchanged for the first executor integration.

### Existing controllers and boundaries

Relevant existing boundaries are:

- `ConversationSessionManager`, `ConversationRestoreController` and
  `ConversationArchive` for conversation state and persistence;
- `MemoryEmbeddingIndexer`, `SemanticMemoryRetriever` and
  `MemoryContextBuilder` for memory context;
- `SearchOrchestrator` and search controllers for optional xAI evidence;
- `StreamingResponseController` and `OpenAiStreamingClient` for OpenAI
  Responses streaming;
- `VoiceModeController`/`VoiceSessionController` for voice lifecycle;
- `ConversationViewportController` and message view factory for UI.

There is currently no production tool registry, tool-call parser, external
executor abstraction or Termux manifest permission. The production manifest
contains Internet, exact-alarm and audio permissions, but no
`com.termux.permission.RUN_COMMAND`.

## 3. Recommended integration point

The smallest useful new boundary is a capability dispatcher placed in the
conversation/application orchestration layer, after the user request has been
accepted and before (or alongside) the final model request. Conceptually:

```text
UI / Voice
  -> existing conversation orchestrator boundary
     -> capability/tool dispatcher (neutral)
        -> ExternalExecutor (neutral)
           -> TermuxExecutor (optional adapter)
              -> RUN_COMMAND -> fixed worker
     -> OpenAI Responses transport
  -> existing archive/UI completion path
```

For the first implementation, do not make the model directly emit a shell
request. The coordinator should accept only an application-level capability
request created by explicit ERA policy. If later Responses tool calls are
introduced, a separate parser/dispatcher may translate a model-selected
allowlisted capability into that same neutral request; the model output must
never become a shell string.

The practical production insertion point is the orchestration currently split
between `MainActivity.sendTextToSphere()`,
`sendMessageWithMemoryContext()` and `sendOpenAiWithInstructions()`. Extracting
or adding a small `ConversationRequestController`/`CapabilityDispatcher` is
preferable to placing logic in `OpenAiStreamingClient`. In V0.1 it may be
invoked explicitly by a test UI or a fixed internal capability path; the report
does not authorize implementing that UI or changing current behavior.

The first production code pass should preserve the existing OpenAI path when no
capability is requested. Termux absence, disabled configuration, callback loss
or worker failure must become a bounded capability result/error, not an
exception that breaks ordinary chat.

## 4. Minimal executor abstraction

The names below are proposed Kotlin concepts, not implementation requirements.
Keep them in a neutral package with no Android Intent or Termux import.

```text
ExternalTaskRequest
  capability: CapabilityId
  arguments: bounded typed/serialized arguments
  clientRequestId: opaque ERA-generated ID
  timeout: bounded duration

ExternalTaskHandle
  taskId: opaque ID
  executorId: string

ExternalTaskStatus
  taskId
  state: CREATED | STARTING | RUNNING | COMPLETED | FAILED |
         CANCELLED | UNAVAILABLE | SUSPENDED_OR_UNREACHABLE
  updatedAt
  detail: bounded, non-secret diagnostic

ExternalTaskResult
  taskId
  state: terminal state
  exit/status code: optional normalized value
  payload: bounded typed result or bounded text
  error: normalized error kind/detail, optional
  truncated: Boolean
```

The neutral operation surface should be approximately:

```text
interface ExternalExecutor {
    fun start(request): StartOutcome
    fun getStatus(handle): ExternalTaskStatus
    fun getResult(handle): ExternalTaskResult
    fun cancel(handle): CancelOutcome
}
```

The actual implementation may use callbacks because the project targets its
existing legacy Kotlin/Android toolchain. The contract must still define that
`start` means accepted/created, not completed; a callback is an observation,
not proof of durable task completion. `getStatus` and `getResult` must be safe
to call after callback loss. `cancel` is idempotent for terminal tasks and must
not imply that a process was killed unless the adapter has confirmed that
meaning.

`CapabilityId` belongs above the executor. It should identify an allowlisted
operation, not contain a command line. The executor contract must not expose
raw paths, shell text, PIDs, worker paths, Termux package names or PRoot fields.

V0.1 does not need a persistent database or complex recovery state machine.
An in-memory handle plus the worker's private task record is sufficient for a
bounded foreground-initiated task, provided lost callbacks map to
`SUSPENDED_OR_UNREACHABLE`/unavailable rather than false completion.

## 5. TermuxExecutor design

### Responsibilities

`TermuxExecutor` is the only production class that should know these details:

- Termux package and exported `RunCommandService` component;
- `com.termux.RUN_COMMAND` action and all Termux extra keys;
- user-granted `com.termux.permission.RUN_COMMAND` and the external-apps
  configuration prerequisite;
- the fixed worker path and worker working directory;
- task/attempt ID transport and unique one-shot `PendingIntent` setup;
- bounded timeouts, result size limits and callback receiver registration;
- mapping Intent acceptance, callback bundle, exit code and worker output to
  neutral statuses/results;
- Termux-specific unavailable, permission, component, timeout and transport
  errors.

It must not contain model reasoning, prompt construction, conversation archive
writes, memory logic, arbitrary command construction, PRoot session management,
Codex orchestration, OEM workarounds or user-facing business policy.

### Configuration

Centralize Termux constants in an adapter-owned `TermuxExecutorConfig` (or
equivalent). It should contain the package/component, action, extra keys,
worker identifier/path, worker working directory, max argument/output sizes and
timeouts. Keep it out of `MainActivity`, core conversation classes and shared
portable data. Treat the worker path as adapter configuration for the installed
worker version, not as a core contract.

Availability should be a bounded check: resolve the explicit service/package,
verify the app is present/enabled, and record configuration/permission failure
as `UNAVAILABLE`. Do not assume that an accepted Intent means the worker ran.
Avoid broad package discovery or silently selecting an unrelated external app.

### Intent and result flow

For `START`, the adapter creates an explicit Intent to
`com.termux.app.RunCommandService`, uses `com.termux.RUN_COMMAND`, passes only
the fixed worker path and a fixed action plus validated task/attempt data, and
requests background execution. It attaches a unique one-shot mutable-compatible
PendingIntent only as required by the tested Android/Termux path.

The adapter records `CREATED`/`STARTING` locally only after its own validation;
it maps `startService()` return/exception to accepted or unavailable/failed.
The worker callback is bounded and may be absent or late. The adapter should
then query `STATUS` or `RESULT` through the same fixed worker, subject to a
bounded poll/deadline, and return a neutral state. It must never synthesize
`COMPLETED` from Intent acceptance or PID presence.

### Error mapping

At minimum, normalize:

- Termux not installed/disabled or service not resolvable → `UNAVAILABLE`;
- SecurityException/permission or external-app policy rejection → `UNAVAILABLE`
  with a configuration detail;
- worker rejected invalid capability/arguments → `FAILED` with bounded
  validation detail;
- no callback and no authoritative status by deadline →
  `SUSPENDED_OR_UNREACHABLE`;
- worker terminal non-zero/failure state → `FAILED`;
- confirmed worker cancellation → `CANCELLED`;
- completed fixed operation with bounded payload → `COMPLETED`.

Do not expose raw stderr, full environment, secrets, absolute private paths or
unbounded worker logs to the conversation layer.

## 6. Worker protocol

### Recommended V0.1 protocol

Reuse the already exercised four-action shape:

```text
START   taskId attemptId capabilityId boundedArgs
STATUS  taskId attemptId
RESULT  taskId attemptId
CANCEL  taskId attemptId
```

The names are suitable because they distinguish task creation, observation,
terminal retrieval and cancellation. The protocol is request/response and
bounded; it is not a general shell channel.

The task journal/state should remain Termux-private. The Android result bundle
contains only a bounded normalized snapshot: task ID, attempt ID, state, exit
status, bounded result tail/payload, bounded error and truncation marker. A
full transcript should not be routed through Intent extras or conversation
archive by default.

The worker should validate action, task ID, attempt ID, capability ID and every
argument before touching a task. State writes should be atomic/locked as in the
probe. `START` must reject duplicate task IDs; `STATUS`/`RESULT`/`CANCEL` must
require the matching attempt ID. `CANCEL` must be terminal-safe and
idempotent.

The status model should be intentionally small:

```text
CREATED -> STARTING -> RUNNING -> COMPLETED
                              -> FAILED
                              -> CANCELLED
                              -> SUSPENDED_OR_UNREACHABLE (adapter observation)
```

`UNAVAILABLE` describes executor availability/configuration rather than a
worker process state. Do not infer progress from `PID alive`; use worker state,
heartbeat and terminal result. The existing probe demonstrated why callback,
PID and state evidence must be treated separately.

### Reuse and non-reuse from the probe

Reuse conceptually:

- explicit `RUN_COMMAND` component/action and background extra;
- fixed worker path, fixed work directory and no external shell input;
- unique task/attempt IDs;
- one-shot PendingIntent result callback;
- `START`/`STATUS`/`RESULT`/`CANCEL` split;
- private task directory, journal, bounded state fields, atomic writes and
  cancellation terminal guard.

Do not copy directly:

- probe package names, probe storage locations, probe UI or SharedPreferences
  log;
- probe duration buttons and sleep implementation;
- probe-specific PIDs, PRoot session fields, Debian paths or Codex commands;
- Probe 4's addressable PRoot cancellation as a production capability;
- any assumption that the callback is durable or that a process/PID proves
  progress.

The worker protocol should carry an allowlisted capability ID, not a shell
command, executable, arbitrary path or free-form environment. A future richer
bidirectional bridge is a separate versioned design, not a reason to enlarge
V0.1.

## 7. Security boundary

The non-negotiable boundary is:

```text
ERA policy / explicit user intent
  -> named capability + typed bounded arguments
  -> neutral ExternalExecutor
  -> TermuxExecutor maps only known capability IDs
  -> fixed worker maps only known actions
  -> fixed utility implementation
```

Forbidden surfaces include `executeShell(String)`, `bash -c`, arbitrary
executable names, arbitrary filesystem paths, raw `rm`, `pkill`,
`proot-distro kill`, free-form environment variables and model-generated
worker paths. The model may not directly select a command line. The first
capability should be hardcoded in the adapter/worker and return structured
data.

Validate IDs and argument lengths in both Android and worker layers. Use
allowlists, not sanitization of a command string. Cap output, runtime,
concurrency and stored result size. Redact token-like values and do not pass
OpenAI keys, Termux auth, Codex auth, keystores or environment dumps through
the bridge.

Explicit user confirmation is required for any capability with meaningful
side effects. V0.1 should have no project mutation, file deletion, package
management, arbitrary script execution, Codex execution, PRoot control or
approval relay.

## 8. Portability

Portable core remains the conversation/session model, archive, memory/search
orchestration, OpenAI transport and capability policy. It depends only on the
neutral task types and an optional executor interface.

Termux adapter owns the Android IPC and Termux-specific configuration. Android
platform concerns such as `Context`, package resolution, `Intent`,
`PendingIntent` and receiver lifecycle may exist in that adapter/composition
layer, not in neutral core types.

A device without Termux gets an unavailable executor or a no-op/unsupported
provider. Ordinary ERA chat continues. A device with another local executor
gets another adapter implementing the same contract. A remote executor may use
the same task/result types with a different transport and authentication
policy.

No data migration or portable package is part of this integration. Do not put
Termux task journals into the existing conversation SQLite schema or durable
portable ERA state in V0.1; keep them executor-private unless a separate task
history specification is approved.

## 9. MainActivity impact

Expected impact in a future implementation:

- add one composition field for a neutral capability dispatcher or request
  controller, not a Termux field;
- construct it through a small composition/factory/provider, passing the
  application context and selected executor;
- route one existing callback/event into that controller if a user-facing
  capability is explicitly scoped;
- preserve the current `sendTextToSphere()` → memory/search/OpenAI/archive flow
  when no executor capability is requested.

`MainActivity.kt` must not gain Intent constants, PendingIntent code, worker
arguments, Termux availability checks, task polling, status mapping, shell
validation or capability implementations. If extraction is needed, it should
be a narrowly scoped move of orchestration currently in MainActivity, not a
general refactor.

Because the current activity already contains substantial UI/controller
wiring, a safe implementation should first add the neutral boundary and
composition factory, then add one call-site. It should not mix this work with
memory, RAW archive, voice, search, lock-screen or portability-package changes.

## 10. Files to create/change

The following is the proposed file scope for the *next implementation task*,
not a list of files changed by this audit.

### CREATE

Neutral/core-facing (names provisional):

- `app/src/main/java/com/era/assistant/core/executor/ExternalExecutor.kt` —
  neutral interface and task operations;
- `app/src/main/java/com/era/assistant/core/executor/ExternalTaskModels.kt` —
  request, handle, status, result, normalized error and capability ID;
- `app/src/main/java/com/era/assistant/core/executor/CapabilityDispatcher.kt`
  (or a similarly small application-layer coordinator) — allowlisted
  capability policy and executor dispatch, without Termux knowledge.

Termux adapter/composition (names provisional):

- `app/src/main/java/com/era/assistant/integration/termux/TermuxExecutor.kt` —
  explicit Intent transport and adapter mapping;
- `app/src/main/java/com/era/assistant/integration/termux/TermuxExecutorConfig.kt`
  — centralized constants/limits/timeouts;
- `app/src/main/java/com/era/assistant/integration/termux/TermuxResultReceiver.kt`
  — bounded callback extraction and handoff;
- `app/src/main/java/com/era/assistant/integration/termux/TermuxWorkerProtocol.kt`
  — adapter-owned action/extra/result keys, if not kept private inside the
  adapter;
- `app/src/main/java/com/era/assistant/integration/ExecutorFactory.kt` —
  chooses Termux when available and otherwise an unavailable implementation.

The exact split can be reduced if a file would contain only constants; avoid
creating a framework solely to match these names.

### MODIFY

- `app/src/main/AndroidManifest.xml` — only in the implementation task, to
  declare the narrowly required RUN_COMMAND permission and any receiver
  component required by the chosen callback design. Keep export/permission
  exposure minimal and do not add a Termux service to ERA.
- `app/src/main/java/com/era/assistant/MainActivity.kt` — minimal composition
  and call-site wiring only; no business logic. The implementation report must
  include its line-count delta.
- Possibly one new/existing conversation orchestration controller file, if a
  small extraction is required to keep MainActivity wiring-only.

No current production file should be modified merely to “prepare” the bridge
in this audit.

### DO NOT TOUCH for V0.1

- `OpenAiStreamingClient.kt`, `StreamingResponseController.kt` and the existing
  Responses request/response semantics;
- `ConversationArchive.kt`, `ConversationSessionManager.kt`,
  `ConversationRestoreController.kt`, `LocalMemoryBackup.kt` and raw/memory
  schema;
- `Memory*`, `ResearchNote*`, `Search*`, Usage, Voice/STT/TTS and Black Box
  subsystems;
- `EraAccessibilityService`, lock-screen components and OEM/device behavior;
- `probe2-run-command/` source and worker as production code;
- `gradle.properties`, build scripts, Gradle/Kotlin/SDK/dependencies;
- portable-package documents and migration implementation;
- Codex/PRoot/session manager, arbitrary shell or approval orchestration.

## 11. V0.1 implementation sequence

### Phase 1 — neutral contract

Create the smallest neutral task models and `ExternalExecutor` interface.
Add an unavailable/no-op implementation and unit-test status/error mapping.
No Termux permission, UI behavior or OpenAI change yet.

### Phase 2 — composition and availability

Add an executor factory/provider that returns the optional adapter or
unavailable implementation. Implement bounded Termux availability detection
inside the adapter. Verify ordinary ERA chat is unchanged when Termux is absent,
disabled or permission-rejected.

### Phase 3 — fixed-worker transport

Add the explicit `RUN_COMMAND` Intent transport, fixed worker configuration,
unique IDs, PendingIntent receiver and bounded callback mapping. Permit only
the four protocol actions. Do not expose a general shell API.

### Phase 4 — one harmless capability

Wire exactly one hardcoded capability through the neutral dispatcher and the
fixed worker. Keep it short, bounded, read-only and outside project mutation.
Verify the returned structured result reaches the application layer without
being mixed into OpenAI/archive flow unless explicitly requested.

### Phase 5 — status/result/cancel

Exercise the adapter's `STATUS`, `RESULT` and `CANCEL` operations, including
missing callback, late callback, timeout, duplicate cancel, terminal task and
unavailable Termux. Map uncertain liveness to
`SUSPENDED_OR_UNREACHABLE`, never to successful completion.

Only after these phases should a separate decision be made about user-facing
tool calls or Responses tool-call integration. Autonomous Codex, PRoot session
control, resume orchestration and durable task history remain out of V0.1.

## 12. First end-to-end capability recommendation

Recommended capability: **`termux_runtime_info`**, a fixed, read-only,
bounded worker operation that returns a small structured record such as a
worker protocol version, Termux adapter version, current execution context
label and elapsed time. It must not return the full environment, secrets,
filesystem paths, package inventory or process tree.

Why this is the safest first capability:

- it crosses the complete ERA → neutral executor → Termux Intent → fixed worker
  → bounded result path;
- it is short and does not modify the ERA project or delete/read user files;
- it does not require Codex, PRoot, Debian session control or arbitrary Python;
- it tests availability, action allowlisting, result callback, status and
  cancellation plumbing (cancellation can be tested against a separately
  bounded delay variant, but the capability itself remains harmless);
- it has a stable structured result suitable for adapter tests.

The worker should implement this as a named operation, not by accepting a
caller-provided command. If a runtime-info operation would expose more host
detail than the security review permits, the fallback first capability is a
fixed `bounded_compute` operation with constant inputs and a deterministic
result; it still must not accept a script or command string.

## Final replacement question

If Termux is replaced tomorrow, production changes should be limited to:

1. a new executor adapter implementing `ExternalExecutor`;
2. executor configuration and availability/composition selection;
3. the manifest/permission only if the replacement transport requires a
   different Android surface;
4. adapter-specific tests.

The conversation request path, model/OpenAI transport, memory/search logic,
archive/RAW flow and capability policy should remain unchanged. If replacing
Termux requires edits to those core files, the abstraction has leaked and the
design is not acceptable.

