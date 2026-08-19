# Memory subsystem passport

## Current State

Conversation RAW is stored in the SQLite database `era_conversation_archive.db` by `ConversationArchive`. The `messages` table is the source record; `research_notes` is a related local table. `RawBlockManager` creates `raw_blocks` references over message ranges after an assistant response closes a block. The current target is 4000 estimated tokens, using an approximate character-based estimate, and a block closes only on an assistant message.

`RawBlockCoordinator` formats a ready block, loads existing topics, runs `MemoryCompiler` against OpenAI `gpt-5-mini`, stores compiler runs/items, and asynchronously indexes missing embeddings. `MemoryContextBuilder`/`SemanticMemoryRetriever` provide relevant long-term context to the main OpenAI request. `LocalMemoryBackup` is triggered after relevant writes.

The memory path is separate from the current OpenAI conversation context: RAW is retained so compiled memory can be checked against source messages. Memory and embedding tables are created by their stores on the shared archive database connection.

## Storage and portability

Memory is local app data. SQLite archive, structured memory, embeddings, settings and research notes are portable candidates, but there is no implemented export/import package. API-key URI grants, Android permissions, local task state and device/OEM state are not portable memory.

## Known Traps / Lessons

- Do not treat a compiler summary as the RAW source of truth.
- `estimated_tokens` is an approximation, not a provider tokenizer.
- Compiler and embedding work is asynchronous and depends on API-key/network availability.
- Do not put memory implementation into `MainActivity`; it is wired through dedicated stores/controllers.

## Required Verification

For memory changes, inspect the affected store/manager and the archive schema, verify table/threshold facts with targeted source read-back, and run `git diff --check`. Device/runtime validation is required for claims about API execution or persisted behavior.
