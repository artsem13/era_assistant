# ERA Portability Architecture

This is a permanent architecture rule for ERA. Read it before every change.

1. ERA must be portable between Android devices. The current TECNO CM5 / HiOS host is temporary execution infrastructure, not part of ERA architecture.
2. Core ERA must not depend on TECNO, Transsion, HiOS, `Usf_Hiber`, a phone model, vendor behavior, Termux, PRoot, Debian, Codex, an external executor package, or an absolute host path.
3. Device/OEM behavior belongs behind a replaceable Android device adapter/provider. Do not add a TECNO/HiOS workaround to core; an exceptional workaround requires an explicitly scoped device adapter, a fallback, and an unsupported state where necessary.
4. External tools and runtimes are optional replaceable adapters. Termux is the current device adapter/executor only; it is never a mandatory core dependency.
5. Core-facing executor concepts must remain runtime-neutral (`TaskRequest`, `TaskStatus`, `TaskResult`, `CancelTask` or equivalent). Executor details such as `proot-distro`, worker scripts, PIDs, shell commands and private paths stay in the adapter.
6. Do not add hardcoded device paths, Termux paths, third-party package assumptions, launcher/file-manager assumptions, or OEM settings intents to core. Standard Android APIs are allowed platform dependencies.
7. ERA data must be designed for migration through one versioned portable ERA data package. Treat RAW conversation archive, structured memory, settings, Sphere instructions, Usage, Research Notes and other durable local ERA state as portable candidates.
8. Android permissions, enabled services, default-assistant selection, notification/battery settings, URI grants, hardware routes and other system/device state are restored separately on the new device; they are not portable ERA state.
9. Every new feature must answer: “What happens when ERA moves to another Android device?” If it is not portable, classify the dependency, isolate it, provide a fallback/unsupported state, and keep core usable.
10. Keep `MainActivity` as UI wiring. Put new business logic, storage, network, voice, memory, dialog, parsing and integration behavior in small controllers/providers/adapters.
11. Reliability and low latency take priority over decorative complexity. Do not add a permanent workaround only for the current phone without explicit architectural justification.
12. Do not build a large new framework during a feature task. Introduce the smallest boundary that makes the dependency replaceable, preserve existing behavior, and defer schema/export changes to an explicit migration task.

Required review labels: `PORTABLE CORE`, `ANDROID PLATFORM DEPENDENCY`, `DEVICE/OEM-SPECIFIC`, `TERMUX-SPECIFIC`, `PATH/FILESYSTEM-SPECIFIC`, `TEMPORARY DEVELOPMENT DEPENDENCY`, or `UNKNOWN`.
