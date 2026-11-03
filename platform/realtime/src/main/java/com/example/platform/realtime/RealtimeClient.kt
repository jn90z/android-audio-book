package com.example.platform.realtime

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

data class RealtimeEvent(val text: String)
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class RealtimeClient(
    private val endpoint: URI,
    private val bearerToken: () -> String?,
    private val onEvent: (RealtimeEvent) -> Unit,
    private val onState: (ConnectionState) -> Unit = {}
) : WebSocket.Listener {
    private val client = HttpClient.newHttpClient()
    private var socket: WebSocket? = null
    private val closed = AtomicBoolean(false)

    fun connect() {
        if (closed.get()) return
        onState(ConnectionState.CONNECTING)
        val builder = client.newWebSocketBuilder()
        bearerToken()?.let { builder.header("Authorization", "Bearer $it") }
        builder.buildAsync(endpoint, this).thenAccept { socket = it }
    }
    fun send(text: String) { socket?.sendText(text, true) }
    fun close() { closed.set(true); socket?.sendClose(WebSocket.NORMAL_CLOSURE, "bye") }
    override fun onOpen(webSocket: WebSocket) { socket=webSocket; onState(ConnectionState.CONNECTED); webSocket.request(1) }
    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
        onEvent(RealtimeEvent(data.toString())); webSocket.request(1); return null
    }
    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
        onState(ConnectionState.DISCONNECTED); if (!closed.get()) connect(); return null
    }
    override fun onError(webSocket: WebSocket, error: Throwable) {
        onState(ConnectionState.DISCONNECTED); if (!closed.get()) connect()
    }
}
