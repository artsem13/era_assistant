package com.era.assistant.core.ai

import android.content.Context

class StreamingResponseController(
    private val streamingClient: OpenAiStreamingClient
) {

    fun sendMessage(
        context: Context,
        apiKeyUriString: String,
        model: String,
        message: String,
        instructions: String,
        onDelta: (String) -> Unit,
        onCompleted: (OpenAiResponse) -> Unit,
        onError: (String) -> Unit
    ) {

        streamingClient.sendMessage(
            context = context,
            apiKeyUriString = apiKeyUriString,
            model = model,
            message = message,
            instructions = instructions,

            onDelta = { delta ->

                onDelta(
                    delta
                )
            },

            onCompleted = { response ->

                onCompleted(
                    response
                )
            },

            onError = { error ->

                onError(
                    error
                )
            }
        )
    }

    fun resetConversation() {

        streamingClient
            .resetConversation()
    }
}