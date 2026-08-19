# Codex repository rules for Era / Sphere

## Scope and source of truth

- Canonical repository: `/mnt/sdcard/Era/Era_From_Zip`. Do not use another copy.
- Code and Git are the primary facts. Current documentation is under `docs/passports/`; audits and plans under `docs/archive/` are historical evidence only.
- Before a task, read this file, `docs/passports/INDEX.md`, the one or two relevant passports, then only the relevant source files. Do not audit the whole repository for a local task.
- Do not add user features, start a new capability slice, or restore abandoned architecture during a documentation or bugfix task.

## Architecture invariants

- Current line: `Sphere intent → typed capability → policy → concrete executor/adapter → bounded result`.
- Era core is executor-neutral. Adapters may be Android-native, provider-specific, Termux or a future specialized executor.
- Era ↔ Codex is removed from the target architecture. Codex is not an executor, backend, sandbox host or orchestration layer. Do not add Codex probes or Codex-facing APIs.
- Termux is a trusted local typed-capability executor through the fixed worker and official `RUN_COMMAND` transport. It is not arbitrary shell, a filesystem sandbox or a guaranteed durable background host.
- Do not pass shell commands, arbitrary executable paths, arbitrary packages or arbitrary URIs through a low-level executor. Add a typed contract, validation and fixed adapter behavior.
- TECNO/HiOS, OEM settings, `Usf_Hiber`, device identifiers and concrete host paths are device-specific. Keep them out of portable core.
- Keep `MainActivity.kt` as minimal wiring. Put business logic, storage, parsing, network, voice, memory, Usage and integrations in small classes/controllers/adapters.
- Never commit secrets, API keys, private keys or personal data.

## Dirty worktree and safety

- At the start run `git status --short` and identify pre-existing dirty scope. Do not claim unrelated changes, restore/reset/checkout them, run `git clean`, or delete untracked files without explicit permission.
- Do not commit, push, reset, checkout, restore or install anything unless the user explicitly requests and authorizes that exact action.
- Before moving documentation, make a KEEP/MERGE_INTO_PASSPORT/MOVE_TO_ARCHIVE/DELETE_CANDIDATE plan. Preserve uncertain material in `docs/archive/`.
- Do not edit production code during a documentation audit. If a code problem is found, record it as a known issue with evidence.

## Build and install prohibition

Codex must not run any Android or Gradle build command, including `gradle`, `./gradlew`, `gradlew`, `assembleDebug`, `assembleRelease`, `tools/build-debug.sh` or `bash tools/build-debug.sh`. Codex must not install, update, remove or reinstall the APK and must not run `adb install`, `pm install`, package-install/update commands or user update aliases.

The user performs the build manually (`complit` or the user’s current build workflow) and performs installation/device update. Normal end of an implementation task is:

```text
code changes → static validation → STOP → USER BUILD REQUIRED
```

Always report `BUILD_RUN_BY_CODEX: NO` and `USER_BUILD_REQUIRED: YES` unless the user explicitly changes this rule.

## Evidence-driven workflow

- For a bug: collect minimal evidence, identify the failure boundary, then make the smallest fix. Do not change multiple layers before the boundary is known.
- Reuse established device facts unless new evidence contradicts them. Do not repeat a confirmed Termux/location/2GIS fact without a reason.
- After every key edit, perform one verify-write read-back with targeted `rg`, a short `sed` excerpt or `git diff` for the changed file. Do not claim a fix is on disk without this evidence.
- Do not repeat identical reads/searches/status/diffs when the file and state have not changed.

## Documentation rules

- `docs/passports/INDEX.md` is the map. `PROJECT_STATE.md` is the architectural snapshot. `FUNCTIONS.md` is the registry for normal capabilities. `TERMUX_BRIDGE.md` is the sole current Era ↔ Termux bridge passport.
- Create a subsystem passport only when it has independent contracts, state, storage, dependencies and lifecycle. Do not create one for a button, provider or small capability.
- Passport sections must distinguish current state, known traps/lessons and required verification. Write confirmed facts, not plans or transcripts.
- `IMPLEMENTED` means code exists; `DEVICE_PASS` requires real device evidence. Do not record personal coordinates, secrets or unsupported behavior.
- Update only the relevant passport after a confirmed change. Update `PROJECT_STATE.md` only for an architectural change. Keep historical research in `docs/archive/` and mark abandoned Codex material historical-only.
- If Era ↔ Termux components, transport, protocol, worker, permissions, security boundary, portability or diagnostics change, update `docs/passports/TERMUX_BRIDGE.md` after code evidence is confirmed.

## Validation budget

- Documentation/Kotlin/XML: `git diff --check` plus targeted read-back when needed.
- Changed shell script: `bash -n <script>` plus targeted read-back.
- Do not run broad audits, full Git history, repeated grep/sed variants or build commands for ordinary tasks.
- Human/device tests are separate from static validation and remain the user’s responsibility.

## Shared storage editing

If a direct edit on Android shared storage returns `RC=182`, do not retry the same edit repeatedly. Copy only the explicitly scoped files to `/tmp`, edit and validate the copies, compare original versus edited content, then return each confirmed file once. Do not copy the whole repository. `RC=182` is an environment condition, not a Kotlin/code diagnosis.

## Final report

Keep the report short and factual:

```text
WHAT_CHANGED:
FILES_CHANGED:
VALIDATION:
BUILD_RUN_BY_CODEX: NO
USER_BUILD_REQUIRED: YES
DEVICE_TEST_REQUIRED:
PASSPORT_UPDATE:
NEXT_ACTION:
```
