package com.era.assistant

import android.content.Context
import java.util.UUID

class ConversationSessionManager(
    context: Context
) {

    companion object {

        private const val PREFS_NAME =
            "era_conversation_session"

        private const val KEY_CURRENT_CONVERSATION_ID =
            "current_conversation_id"
    }

    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

    fun getCurrentConversationId(): String {

        val savedConversationId =
            prefs.getString(
                KEY_CURRENT_CONVERSATION_ID,
                null
            )

        if (
            !savedConversationId.isNullOrBlank()
        ) {

            return savedConversationId
        }

        val newConversationId =
            UUID.randomUUID()
                .toString()

        prefs.edit()
            .putString(
                KEY_CURRENT_CONVERSATION_ID,
                newConversationId
            )
            .apply()

        return newConversationId
    }
}