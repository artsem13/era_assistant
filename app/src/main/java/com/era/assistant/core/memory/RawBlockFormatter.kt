package com.era.assistant.core.memory

import com.era.assistant.ArchivedMessage

class RawBlockFormatter {

    fun format(
        messages: List<ArchivedMessage>
    ): String {

        if (
            messages.isEmpty()
        ) {

            return ""
        }

        val result =
            StringBuilder()

        for (
            message in messages
        ) {

            result.append(
                "MESSAGE_ID: "
            )

            result.append(
                message.id
            )

            result.append(
                "\n"
            )

            result.append(
                "ROLE: "
            )

            result.append(
                message.role
            )

            result.append(
                "\n"
            )

            result.append(
                "TEXT:\n"
            )

            result.append(
                message.text
            )

            result.append(
                "\n\n"
            )

            result.append(
                "--------------------"
            )

            result.append(
                "\n\n"
            )
        }

        return result
            .toString()
            .trim()
    }
}