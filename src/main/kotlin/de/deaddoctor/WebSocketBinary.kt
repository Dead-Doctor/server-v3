package de.deaddoctor

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

class WebSocketBinaryEventRegistrant {
    var connection: (suspend (WebSocketEventHandlerContext) -> Unit)? = null
    var disconnection: (suspend (WebSocketEventHandlerContext) -> Unit)? = null
    var message: (suspend (WebSocketEventHandlerContext, ByteArray) -> Unit)? = null

    inline fun connection(crossinline handler: suspend WebSocketEventHandlerContext.() -> Unit) {
        connection = { it.handler() }
    }

    inline fun disconnection(crossinline handler: suspend WebSocketEventHandlerContext.() -> Unit) {
        disconnection = { it.handler() }
    }

    inline fun message(
        crossinline handler: suspend WebSocketEventHandlerContext.(ByteArray) -> Unit
    ) {
        message = { ctx, data -> ctx.handler(data) }
    }
}

fun Route.webSocketBinary(
    path: String,
    registerEvents: WebSocketBinaryEventRegistrant.() -> Unit
): WebSocketSender {
    val handlers = WebSocketBinaryEventRegistrant().apply(registerEvents)
    val connections = mutableListOf<Connection>()

    webSocket(path) {
        val user = call.user
        val connection = Connection(this, user)
        connections.add(connection)

        //TODO: migrate to binary
        val webSocketEventHandlerContext = WebSocketEventHandlerContext(connections, connection)

        handlers.connection?.let { it(webSocketEventHandlerContext) }

        for (frame in incoming) {
            if (frame is Frame.Text) {
                val data = frame.readBytes()
                handlers.message?.let { it(webSocketEventHandlerContext, data) }
            }
        }
        connections.remove(connection)
        handlers.disconnection?.let { it(webSocketEventHandlerContext) }
    }

    //TODO: migrate to binary
    return WebSocketSender(connections)
}