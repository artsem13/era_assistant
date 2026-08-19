# ERA PORTABILITY AUDIT

Audit date: 2026-08-18. Repository: `/mnt/sdcard/Era/Era_From_Zip`. The starting Git worktree contained pre-existing user changes and untracked audit/probe/search materials; they were preserved and not rewritten. Scope covered the production `app/src`, manifest/resources, Gradle/build tooling, storage/settings/network integrations, accessibility/voice/lock-screen components, and the separate Termux probe harness.

## 1. Executive verdict

PORTABILITY_STATUS: **CONDITIONAL — no direct TECNO/HiOS lock-in in the production APK, but migration is not yet a one-step operation**

DEVICE_LOCK_IN_LEVEL: **LOW direct / MEDIUM behavioral risk**

TERMUX_COUPLING_LEVEL: **NONE in production runtime; HIGH in current development/build tooling and disposable probes**

OEM_COUPLING_LEVEL: **NONE found in production source; Android capability reliability remains device-dependent**

STORAGE_PORTABILITY: **PARTIAL**

PRODUCTION_MIGRATION_READINESS: **NOT READY for a complete device migration package**

The production app does not reference TECNO, Transsion, HiOS, `Usf_Hiber`, Termux, PRoot, Debian, shell commands, absolute Termux paths, or a vendor settings screen. The principal portability risks are: a hardcoded optional ChatGPT integration and its accessibility UI labels; durable state split across SQLite, SharedPreferences and an app-private raw-search directory; API-key URI grants that are device/provider-specific; and Android service/permission behavior that must be configured again on a new device.

The current architecture is therefore suitable for incremental migration-first work, but the durable data contract and external-capability boundaries are not yet formalized in code.

## 2. Current portable core

The following behavior is portable in intent and does not inspect the current phone:

- Conversation, research-note and memory domain records, IDs, timestamps, model/source fields, memory compiler state and embedding records. They are currently implemented on Android SQLite, so the data model is portable but the storage implementation is Android-bound.
- Pure or mostly pure parsing/calculation pieces such as search response models/parser, usage calculation, embedding math, text chunking/expression processing and Black Box sanitization.
- OpenAI and xAI provider protocols, request parsing and model-selection logic. These are provider/network dependencies, not device dependencies.
- Voice session state, turn buffering and the existing MediaRecorder baseline. Capture quality and microphone routing are Android/device runtime concerns, not TECNO assumptions.
- App-private storage through `Context.filesDir`, `cacheDir` and `getExternalFilesDir`, and public export through MediaStore/SAF. These are standard Android abstractions; the returned physical paths are not architecture contracts.

Important boundary: the package `core` is not currently a platform-independent module. Many classes under `core` accept `Context`, `Uri`, `SharedPreferences`, Android SQLite, `Handler`, views or media APIs. This is migration debt, not evidence of OEM lock-in.

## 3. Device/OEM-specific dependencies

No direct TECNO/Transsion/HiOS/`Usf_Hiber` implementation or model check was found in `app/src/main`.

| FILE | DEPENDENCY | TYPE | RISK | MIGRATION_IMPACT | RECOMMENDED_ACTION |
|---|---|---|---|---|---|
| `app/src/main/java/com/era/assistant/core/blackbox/BlackBoxController.kt:84-86` | Records `Build.MANUFACTURER` and `Build.MODEL` in diagnostic metadata | DEVICE/OEM-SPECIFIC | Diagnostic artifacts carry current-host provenance; this must not become a behavior switch | Device-specific metadata is not portable core state | Keep as explicitly labeled diagnostic metadata; do not use it for control flow |
| `app/src/main/java/com/era/assistant/EraAccessibilityService.kt:105-139,216-269` | Watches package `com.openai.chatgpt`, Russian labels “Начать голосовой чат” / “Завершить голосовое обсуждение”, clickable-parent traversal and retry timing | ANDROID PLATFORM DEPENDENCY (external-app/UI-specific) | ChatGPT package, locale, version and accessibility hierarchy can change on any device | Optional capability may be unavailable after migration | Keep outside core; later isolate as an `ExternalVoiceAppAdapter` with capability detection and unsupported/fallback state |
| `app/src/main/java/com/era/assistant/MainActivity.kt:2399-2424`, `LockScreenTestReceiver.kt`, `LockScreenWakeActivity.kt` | Exact alarm, keyguard wake and launch behavior | ANDROID PLATFORM DEPENDENCY with device-policy variability | Exact alarms, lock-screen launch and background activity rules vary by Android release/OEM | Reconfigure and retest on the new device; this is a diagnostic/test path, not portable data | Preserve standard APIs; do not add OEM-specific settings automation |
| `app/src/main/java/com/era/assistant/core/voice/*` and `EraVoiceInteractionService.kt` | MediaRecorder, audio focus, microphone route, VoiceInteraction and keyguard | ANDROID PLATFORM DEPENDENCY with hardware/runtime variability | Capture profiles and background/service reliability differ between devices | Request permissions and validate route/capture behavior again | Keep the existing portable baseline; isolate future routes/providers if needed |

No battery-manager, notification-channel, foreground-service, `Build`-based workaround, Bluetooth-device identifier, launcher assumption or OEM settings intent was found in production code.

## 4. Termux-specific dependencies

Termux is not in the ERA APK production path: `app/src/main/AndroidManifest.xml` has no Termux permission/service/action, and `app/src/main/java` has no Termux/PRoot/Debian/shell reference.

| FILE | DEPENDENCY | TYPE | RISK | MIGRATION_IMPACT | RECOMMENDED_ACTION |
|---|---|---|---|---|---|
| `gradle.properties:6` | `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2` | TERMUX-SPECIFIC / TEMPORARY DEVELOPMENT DEPENDENCY | Build depends on the current Debian-in-Termux environment | New build host needs an equivalent configured toolchain, not an ERA runtime change | Keep under build-environment instructions; never expose it to app core |
| `tools/build-debug.sh:6-23` | `/opt/java11`, `/opt/android-sdk/android-sdk`, Termux AAPT2 path | PATH/FILESYSTEM-SPECIFIC / TEMPORARY DEVELOPMENT DEPENDENCY | Canonical build script is host-specific by design | Recreate or replace the build adapter on another development host | Keep out of runtime architecture; do not move these paths into app code |
| `probe2-run-command/app/src/main/java/com/era/probe2/MainActivity.java:18-28` | `com.termux`, `RunCommandService`, RUN_COMMAND extras, Termux-private worker path | TERMUX-SPECIFIC / TEMPORARY DEVELOPMENT DEPENDENCY | Powerful external execution surface; not part of ERA APK | Probe must be revalidated or replaced for another host | Retain as disposable probe evidence; do not include it from `settings.gradle` |
| `probe2-run-command/termux/probe4-worker.sh:1,10-13,...` | Termux bash/proot-distro paths, Debian container and PID/session files | TERMUX-SPECIFIC / PATH/FILESYSTEM-SPECIFIC | Worker is tied to one Termux installation and container layout | Reimplement an adapter per executor; do not migrate it as core state | Keep only as probe tooling; no production bridge implementation in this task |
| `CODEX_BRIDGE_ARCHITECTURE_AUDIT.md`, probe README/scripts | Descriptions of Termux RUN_COMMAND, PRoot, Debian and current workspace paths | TEMPORARY DEVELOPMENT DEPENDENCY / DOCUMENTATION | Documentation can be mistaken for an implemented production contract | Future bridge must start from a neutral executor interface | Treat as research/operations documentation; the new architecture rule supersedes runtime coupling |

The current known TECNO/HiOS `Usf_Hiber` behavior belongs to the device adapter/operations layer. It must not be encoded in ERA core, and Termux must remain optional.

## 5. Hardcoded paths / packages / intents

### Production APK

- `com.openai.chatgpt` is hardcoded in `MainActivity.kt`, `EraAccessibilityService.kt` and `LockScreenWakeActivity.kt`. This is an optional third-party integration, not an OEM dependency, but it is not a portable core contract.
- The app identity `com.era.assistant` is the application package in `app/build.gradle`, the manifest and the instrumentation test. It is a normal app identity, not device lock-in; migration tooling must nevertheless define package/version compatibility.
- No production absolute path matching `/data/data/...`, `/sdcard/...`, `/storage/emulated/0/...`, `/mnt/sdcard/...`, a Debian path or a Termux path was found.
- Production logical locations are `filesDir/xai_search_raw`, cache files for voice/TTS, and MediaStore relative folders `Download/Era`, `Download/Era/BlackBox` and `Download/Era/memory/raw`. These use Android APIs, not hardcoded physical device paths.
- Production standard intents/capabilities include `ACTION_OPEN_DOCUMENT` for API-key files, `Settings.ACTION_ACCESSIBILITY_SETTINGS`, exact `AlarmManager` scheduling, `VoiceInteractionService`, `AccessibilityService`, lock-screen window flags and internal activity intents. These are Android platform dependencies, not OEM assumptions.
- Provider endpoints are fixed to OpenAI/xAI URLs in their clients. They are external provider dependencies and may require future provider adapters, but they do not couple ERA to a phone or host runtime.

### Repository/build/probe material

- `/data/data/com.termux/files/usr/bin/aapt2` in `gradle.properties` and `tools/build-debug.sh`.
- `/opt/java11` and `/opt/android-sdk/android-sdk` in `tools/build-debug.sh` and the corresponding canonical build instructions.
- `/data/data/com.termux/files/home/.era_probe2/worker.sh`, `/data/data/com.termux/files/home`, `/data/data/com.termux/files/usr/bin/proot-distro` and `/data/data/com.termux/files/usr/var/lib/proot-distro` in the separate probe harness.
- `/mnt/sdcard/Era/Era_From_Zip` appears in build/operations documentation and probe design, not production app code.
- `/storage/emulated/0/Download/Era/memory/raw/...` appears in historical research documentation, not production source.

No `com.termux`, `com.termux.api`, TECNO or Transsion package appears in the production manifest or production Kotlin/Java source.

## 6. Storage and migration state

### Current durable state

| State | Current implementation | Migration classification |
|---|---|---|
| RAW conversation archive | `ConversationArchive` SQLite database `era_conversation_archive.db`; writes trigger `LocalMemoryBackup` | PORTABLE CORE DATA, but only a manual/file backup shape exists |
| Research Notes | `research_notes` table in the same SQLite database | PORTABLE CORE DATA, coupled to DB copy |
| Structured memory | `raw_blocks`, `memory_topics`, `memory_items`, `memory_compiler_runs` and related tables in the same SQLite database | PORTABLE CORE DATA, no versioned package/import contract |
| Embeddings | `memory_embeddings` table in the same SQLite database, with model/version/content hash | PORTABLE DERIVED DATA; can be rebuilt, but current DB copy preserves it |
| Usage | Long-lived counters in `era_preferences` and xAI search usage keys | PORTABLE CANDIDATE, not included in an explicit ERA package |
| Sphere instructions | `era_preferences:sphere_instructions` | PORTABLE CANDIDATE, not explicitly exported/imported |
| Selected model and session/cost counters | `era_preferences` | PORTABLE CANDIDATE; session/action state should be reviewed separately |
| Current conversation ID | `era_conversation_session:current_conversation_id` | PORTABLE CANDIDATE; migrating it is a policy decision |
| Research/search raw archive | `filesDir/xai_search_raw/*.json` | PORTABLE CANDIDATE, currently omitted from `LocalMemoryBackup` |
| API keys | The app stores only `content://` URI strings in preferences; key bytes remain in the selected document | NOT PORTABLE as-is; reselect key and re-grant URI permission on the new device |
| Voice M4A and TTS MP3 | `cacheDir`, deleted after use | TEMPORARY / NON-PORTABLE by design |
| Black Box, accessibility, lock-screen diagnostic reports | MediaStore `Download/Era...` or app external-files fallback | Export artifacts; copyable by the user but not a canonical importable ERA state |

`LocalMemoryBackup` checkpoints and copies the SQLite main file to `Download/Era/memory/raw/era_conversation_archive.db`, which is useful evidence of the intended portable archive. It does not create a manifest, include the app preferences or `xai_search_raw`, validate a complete package, or provide import/restore. `android:allowBackup="true"` is enabled, but no explicit backup/data-extraction rules were found; OS/OEM backup behavior must not be treated as the ERA migration contract.

### State that is not portable

Android permissions and system state are intentionally outside portable ERA state: microphone permission, enabled AccessibilityService, VoiceInteraction/default-assistant selection, exact-alarm permission/policy, lock-screen behavior, notification/battery policy, URI grants, audio routes and hardware calibration. These must be configured again on a new device.

### Unknowns to resolve in a dedicated migration task

- Whether the chosen SQLite version and all ad-hoc `CREATE TABLE IF NOT EXISTS` tables are sufficient as a long-term interchange schema.
- Whether the application should preserve current conversation/session IDs and usage totals across import.
- A versioned package manifest, integrity checks, import conflict policy and handling for stale provider URI references.
- Whether raw search records should be included, redacted, or treated as rebuildable diagnostics.
- API-key re-entry/secret-management policy. Do not copy key bytes into the package.

## 7. Android-platform dependencies

These are normal Android dependencies and are not automatically portability defects:

- SQLite/`SQLiteOpenHelper`, `SharedPreferences`, `Context` app-private directories and standard Java file streams.
- SAF `ACTION_OPEN_DOCUMENT`, persisted read grants, `ContentResolver`, MediaStore and `Environment` logical directories.
- `MediaRecorder`, audio focus, TTS/media playback, microphone permission and the existing Voice/STT controllers.
- `AccessibilityService` and `VoiceInteractionService`, including their user-enabled system settings.
- `AlarmManager`, `BroadcastReceiver`, keyguard/window flags and standard activity/launcher intents.
- Android UI/AppCompat/material resources and standard API-level compatibility checks.

The following are external integration assumptions layered on top of Android and should remain replaceable: ChatGPT package/UI automation; OpenAI/xAI network endpoints and models; and any future external executor. There is no production foreground service, WorkManager job, notification channel, background shell worker or battery-optimization exemption flow in the current source.

## 8. Safe refactors performed now

- Added `ERA_PORTABILITY_ARCHITECTURE.md` as a root-level permanent instruction for future agents and changes.
- Added this audit report with production, build-tooling and probe classifications.
- No production Kotlin/Java/XML, manifest, Gradle, database schema, preference key or user-facing behavior was changed. No code refactor was safe and necessary enough to justify behavior risk during an audit-only pass.

## 9. Deferred refactors

These are intentionally not performed now:

1. Do not rewrite `MainActivity` or move unrelated existing logic merely to improve shape.
2. Define a small storage/repository and portable-package boundary before adding export/import; do not change the current DB schema without a migration specification.
3. Centralize settings/secret resolution before changing API-key handling; preserve URI permissions and require re-selection on migration.
4. Isolate the ChatGPT accessibility integration behind a capability adapter with label/version detection and an unsupported fallback.
5. If an executor feature is later requested, introduce a small neutral task interface (`TaskRequest`, `TaskStatus`, `TaskResult`, cancellation) and put Termux/remote/native implementations behind it. Do not add a production Termux bridge as part of this audit.
6. Decide whether Black Box and accessibility reports are export artifacts or portable diagnostic data before relocating their storage.
7. Do not add TECNO/HiOS battery or background hacks to core. Validate device behavior through a separate adapter/device test plan.

## 10. Migration checklist

- Define and version one portable ERA package containing the approved durable SQLite/data records, settings, Sphere instructions, Usage, Research Notes and selected raw archives.
- Export/import with integrity checks, schema compatibility, atomic restore and a clear policy for duplicate IDs, stale derived embeddings and current-session state.
- Exclude API-key bytes and system state. On the new device, select key documents again and re-grant SAF permissions.
- Install the ERA APK and configure microphone, AccessibilityService, VoiceInteraction/default assistant, exact alarms and lock-screen permissions as applicable.
- Revalidate the optional ChatGPT integration: package installed, supported accessibility labels/hierarchy, locale and capability state. If unavailable, ERA must remain usable without it.
- Validate built-in microphone capture, audio focus, TTS/STT, background/lock-screen behavior and exact-alarm behavior on the target Android version.
- Do not require Termux, Debian, PRoot, Codex or the current shared-storage layout for core ERA operation. If an executor is present, test its adapter independently.
- Perform a human migration test on a clean Pixel/AOSP-like device, including missing optional apps, denied permissions, invalid URI grants and interrupted restore.

## 11. Architecture rules added

`ERA_PORTABILITY_ARCHITECTURE.md` now permanently requires:

- portability between Android devices and a temporary-host interpretation of TECNO/HiOS;
- isolation of device/OEM behavior and replaceable external-tool adapters;
- no hardcoded device paths, package assumptions or OEM workarounds in core;
- optional Termux executor semantics;
- a unified portable data-package direction, with Android permissions/settings restored separately;
- explicit portability classification, fallback and unsupported state for non-portable features;
- a small, wiring-only `MainActivity` boundary;
- reliability/low-latency priority and no permanent current-phone workaround without justification.

