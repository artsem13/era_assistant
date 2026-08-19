# Documentation audit disposition

Archive documents are preserved historical evidence. They are not current architecture instructions. The authoritative replacements are in `docs/passports/`.

## Disposition

| Existing document | Classification | Disposition |
|---|---|---|
| `PROJECT_STATE.md` | MERGE_INTO_PASSPORT / ARCHIVE | State facts merged into `passports/PROJECT_STATE.md`; legacy copy preserved here |
| `ARCHITECTURE_RULES.md` | MERGE_INTO_PASSPORT / ARCHIVE | Rules merged into project state and AGENTS; legacy copy preserved here |
| `docs/ERA_TERMUX_PASSPORT.md` | MERGE_INTO_PASSPORT / ARCHIVE | Replaced by `passports/TERMUX_BRIDGE.md`; legacy copy preserved here |
| `VOICE_PASSPORT.md` | MERGE_INTO_PASSPORT | Moved to `passports/VOICE.md` as the large subsystem passport |
| `docs/agent-context/VOICE.md` | DUPLICATE / ARCHIVE | Superseded by `passports/VOICE.md`; preserved here |
| `memory12_08_26.md` | MERGE_INTO_PASSPORT / HISTORICAL_AUDIT | Memory facts merged into `passports/MEMORY.md`; preserved here |
| `VOICE_AUDIT_CURRENT.md` | HISTORICAL_AUDIT | Audit evidence; not current instructions |
| `VOICE_AUDIT_14_08_26.md` | HISTORICAL_AUDIT | Dated voice audit |
| `ERA_UI_AUDIT_14_08_26.md` | HISTORICAL_AUDIT | Dated UI audit |
| `ERA_PORTABILITY_AUDIT.md` | HISTORICAL_AUDIT | Portability evidence |
| `ERA_PORTABILITY_ARCHITECTURE.md` | MERGE_INTO_PASSPORT / ARCHIVE | Portability rules merged into current passports |
| `ERA_TERMUX_PRODUCTION_INTEGRATION_PLAN.md` | HISTORICAL_AUDIT / ARCHIVE | Superseded implementation plan; current bridge is code-backed |
| `CODEX_BRIDGE_ARCHITECTURE_AUDIT.md` | HISTORICAL_AUDIT | Codex path abandoned; evidence retained |
| `ERA_CODEX_ORCHESTRATION_DEEP_AUDIT-1.md` | HISTORICAL_AUDIT | Codex orchestration research; historical only |
| `ERA_CODEX_TARGETED_PROBE_REPORT.md` | HISTORICAL_AUDIT | Codex probe evidence; historical only |
| `INTERNET_SEARCH_REGRESSION_REPORT.md` | HISTORICAL_AUDIT | Search regression evidence |
| `XAI_SEARCH_PRODUCTION_IMPLEMENTATION_REPORT.md` | MERGE_INTO_PASSPORT / ARCHIVE | Search implementation facts merged into `passports/SEARCH.md` and `USAGE.md` |
| `xai_probe/XAI_SEARCH_EMPIRICAL_PROBE_REPORT.md` | HISTORICAL_AUDIT | Empirical provider research |
| `PASSPORT_2026-08-09.md` | DELETE_CANDIDATE | Dated unclassified passport; preserve until manual review confirms no unique facts |
| `probe2-run-command/README.md` | ARCHIVE | Probe-specific material, not current architecture |
| `BlackBox/BLACKBOX.md`, `BlackBox/FORMAT.md` | ARCHIVE | Specialized historical/format notes; no conflict with current passports |
| `video/processed/LOOP_REPORT.md` | ARCHIVE | Media preparation report, unrelated to current architecture |

`PASSPORT_2026-08-09.md` is intentionally not deleted: its unique content has not been fully classified in this pass. No other deletion is required; uncertain material is archived rather than discarded.

## Codex disposition

Codex-related audits and probes remain available only as historical evidence. The Codex path is abandoned and must not be copied into current passports, AGENTS.md or new capability design.
