package com.era.assistant.core.memory

import android.content.Context
import com.era.assistant.ConversationArchive
import com.era.assistant.LocalMemoryBackup

class RawBlockCoordinator(
    private val context: Context,
    private val archive: ConversationArchive
) {

    companion object {

        private const val PREFS_NAME =
            "era_preferences"

        private const val KEY_API_KEY_URI =
            "api_key_uri"
    }

    private val rawBlockManager =
        RawBlockManager(
            archive
        )

    private val rawBlockFormatter =
        RawBlockFormatter()

    private val memoryCompiler =
        MemoryCompiler()

    private val memoryCompilerRunStore =
        MemoryCompilerRunStore(
            archive
        )

    private val memoryItemStore =
        MemoryItemStore(
            archive
        )

    fun onAssistantMessageSaved(
        conversationId: String
    ) {

        Thread {

            val blockId =
                rawBlockManager
                    .tryCreateNextBlock(
                        conversationId
                    )

            if (
                blockId == null
            ) {

                return@Thread
            }

            LocalMemoryBackup
                .backupInBackground(
                    context,
                    archive
                )

            val messages =
                rawBlockManager
                    .getBlockMessages(
                        blockId
                    )

            if (
                messages.isEmpty()
            ) {

                return@Thread
            }

            val rawBlockText =
                rawBlockFormatter
                    .format(
                        messages
                    )

            if (
                rawBlockText.isBlank()
            ) {

                return@Thread
            }

            val apiKeyUri =
                context
                    .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                    .getString(
                        KEY_API_KEY_URI,
                        null
                    )

            if (
                apiKeyUri == null
            ) {

                return@Thread
            }

            val existingTopics =
                memoryItemStore
                    .getTopics()

            val runId =
                memoryCompilerRunStore
                    .createRun(
                        rawBlockId =
                            blockId,
                        inputText =
                            rawBlockText
                    )

            if (
                runId ==
                    -1L
            ) {

                return@Thread
            }

            LocalMemoryBackup
                .backupInBackground(
                    context,
                    archive
                )

            memoryCompiler.compile(
                context = context,
                apiKeyUriString = apiKeyUri,
                rawBlockText = rawBlockText,
                existingTopics = existingTopics,

                onSuccess = { compilerOutput ->

                    memoryCompilerRunStore
                        .markSuccess(
                            runId =
                                runId,
                            summary =
                                compilerOutput
                        )

                    try {

                        memoryItemStore
                            .saveCompilerOutput(
                                compilerOutput =
                                    compilerOutput,
                                rawBlockId =
                                    blockId,
                                compilerRunId =
                                    runId
                            )

                    } catch (
                        _: Exception
                    ) {
                    }

                    LocalMemoryBackup
                        .backupInBackground(
                            context,
                            archive
                        )
                },

                onError = { error ->

                    memoryCompilerRunStore
                        .markError(
                            runId =
                                runId,
                            error =
                                error
                        )

                    LocalMemoryBackup
                        .backupInBackground(
                            context,
                            archive
                        )
                }
            )

        }.start()
    }
}