package com.era.assistant.core.ai

import java.net.HttpURLConnection

class StreamingRequestHandle {

    @Volatile
    private var cancelled = false

    @Volatile
    private var connection: HttpURLConnection? = null

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }

    fun isCancelled(): Boolean = cancelled

    fun attach(connection: HttpURLConnection) {
        this.connection = connection
        if (cancelled) connection.disconnect()
    }

    fun detach() {
        connection = null
    }
}
