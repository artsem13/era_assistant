package com.era.assistant

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ResearchNoteController(
    private val activity: AppCompatActivity,
    private val notesStore: ResearchNotesStore,
    private val conversationIdProvider: () -> String,
    private val messageIdProvider: () -> Long?
) {

    private val dialog =
        ResearchNoteDialog(
            activity
        )

    fun openNote() {

        dialog.show { noteText ->

            val conversationId =
                conversationIdProvider()

            val messageId =
                messageIdProvider()

            val rowId =
            notesStore.saveNote(
                    conversationId = conversationId,
                    messageId = messageId,
                    text = noteText
                )

            if (
                rowId != -1L
            ) {

                Toast.makeText(
                    activity,
                    "Заметка сохранена — ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }.format(Date())}",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                Toast.makeText(
                    activity,
                    "Не удалось сохранить заметку",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
