package com.era.assistant.core.voice

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Small text-frame WebSocket transport for the xAI streaming endpoint. */
class SimpleWebSocketClient(
    private val url: String,
    private val authorization: String,
    private val listener: Listener
) {

    interface Listener {
        fun onOpen()
        fun onText(text: String)
        fun onError(error: String)
        fun onClosed()
    }

    private val lock = Any()
    private val random = SecureRandom()
    private val pendingTexts = ArrayList<String>()
    private var socket: SSLSocket? = null
    private var output: OutputStream? = null
    private var closed = false

    fun connect() {
        Thread {
            try {
                val endpoint = URI(url)
                val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(endpoint.host, if (endpoint.port > 0) endpoint.port else 443)
                    as SSLSocket
                sslSocket.soTimeout = 0
                sslSocket.startHandshake()

                val input = sslSocket.inputStream
                val socketOutput = sslSocket.outputStream
                writeHandshake(endpoint, socketOutput)
                validateHandshake(input)

                synchronized(lock) {
                    if (closed) {
                        sslSocket.close()
                        return@Thread
                    }
                    socket = sslSocket
                    output = socketOutput
                    pendingTexts.forEach { writeTextFrame(socketOutput, it) }
                    pendingTexts.clear()
                }

                listener.onOpen()
                readFrames(input, socketOutput)
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                synchronized(lock) {
                    if (!closed) listener.onError(message)
                }
            } finally {
                synchronized(lock) {
                    output = null
                    socket = null
                }
                if (!closed) listener.onClosed()
            }
        }.start()
    }

    fun sendText(text: String) {
        synchronized(lock) {
            if (closed) return
            val socketOutput = output
            if (socketOutput == null) {
                pendingTexts.add(text)
            } else {
                try {
                    writeTextFrame(socketOutput, text)
                } catch (error: Exception) {
                    listener.onError(error.message ?: "WebSocket send failed")
                }
            }
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            try {
                output?.let { writeFrame(it, 0x8, ByteArray(0)) }
            } catch (_: Exception) {
            }
            pendingTexts.clear()
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            output = null
            socket = null
        }
    }

    private fun writeHandshake(endpoint: URI, output: OutputStream) {
        val request = buildString {
            append("GET ${endpoint.rawPath + (if (endpoint.rawQuery != null) "?" + endpoint.rawQuery else "")} HTTP/1.1\r\n")
            append("Host: ${endpoint.host}\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("Sec-WebSocket-Key: ${randomKey()}\r\n")
            append("Authorization: $authorization\r\n")
            append("\r\n")
        }
        output.write(request.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun validateHandshake(input: InputStream) {
        val response = ByteArrayOutputStream()
        var previous = 0
        var current: Int
        while (true) {
            current = input.read()
            if (current < 0) throw Exception("WebSocket handshake closed")
            response.write(current)
            if (previous == '\r'.toInt() && current == '\n'.toInt()) {
                val bytes = response.toByteArray()
                val length = bytes.size
                if (length >= 4 && bytes[length - 4] == '\r'.toInt().toByte() &&
                    bytes[length - 3] == '\n'.toInt().toByte() &&
                    bytes[length - 2] == '\r'.toInt().toByte() &&
                    bytes[length - 1] == '\n'.toInt().toByte()
                ) break
            }
            previous = current
            if (response.size() > 16 * 1024) throw Exception("Invalid WebSocket handshake")
        }
        val status = response.toString(Charsets.US_ASCII.name()).lineSequence().firstOrNull() ?: ""
        if (!status.contains(" 101 ") && !status.endsWith(" 101")) {
            throw Exception("WebSocket handshake failed: $status")
        }
    }

    private fun readFrames(input: InputStream, output: OutputStream) {
        while (true) {
            val first = input.read()
            if (first < 0) return
            val second = input.read()
            if (second < 0) return
            var length = (second and 0x7f).toLong()
            if (length == 126L) length = readUnsigned(input, 2)
            if (length == 127L) length = readUnsigned(input, 8)
            if (length > 4 * 1024 * 1024) throw Exception("WebSocket frame too large")
            val masked = (second and 0x80) != 0
            val mask = if (masked) readFully(input, 4) else null
            val payload = readFully(input, length.toInt())
            if (masked && mask != null) {
                payload.indices.forEach { payload[it] = (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
            }
            when (first and 0x0f) {
                0x1 -> listener.onText(String(payload, Charsets.UTF_8))
                0x8 -> return
                0x9 -> synchronized(lock) { writeFrame(output, 0xA, payload) }
            }
        }
    }

    private fun writeTextFrame(output: OutputStream, text: String) {
        writeFrame(output, 0x1, text.toByteArray(Charsets.UTF_8))
    }

    private fun writeFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
        val mask = ByteArray(4).also { random.nextBytes(it) }
        output.write(0x80 or opcode)
        when {
            payload.size < 126 -> output.write(0x80 or payload.size)
            payload.size <= 65535 -> {
                output.write(0x80 or 126)
                output.write(payload.size shr 8)
                output.write(payload.size)
            }
            else -> {
                output.write(0x80 or 127)
                for (shift in 56 downTo 0 step 8) output.write(payload.size.toLong().ushr(shift).toInt())
            }
        }
        output.write(mask)
        payload.forEachIndexed { index, byte -> output.write(byte.toInt() xor mask[index % 4].toInt()) }
        output.flush()
    }

    private fun readUnsigned(input: InputStream, bytes: Int): Long {
        var result = 0L
        repeat(bytes) { result = (result shl 8) or input.read().toLong() }
        return result
    }

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(result, offset, size - offset)
            if (count < 0) throw Exception("WebSocket connection closed")
            offset += count
        }
        return result
    }

    private fun randomKey(): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
