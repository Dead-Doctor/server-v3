package de.deaddoctor

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import xyz.mcxross.bcs.Bcs

class WebSocketBinaryEventHandler {
    var handleConnection: suspend (WebSocketEventHandlerContext) -> Unit = {}
    val handleDisconnection: suspend (WebSocketEventHandlerContext) -> Unit = {}
    val receivers = mutableListOf<suspend (WebSocketEventHandlerContext, ByteArray) -> Unit>()

    suspend fun handleDestination(context: WebSocketEventHandlerContext, data: ByteArray) {
        val packetType = data[0].toInt()
        val packetData = data.copyOfRange(1, data.size)
        receivers[packetType](context, packetData)
    }

    fun connection(handler: suspend (WebSocketEventHandlerContext) -> Unit) {
        handleConnection = handler
    }

    fun disconnection(handler: suspend (WebSocketEventHandlerContext) -> Unit) {
        handleConnection = handler
    }

    fun receiver(handler: suspend (WebSocketEventHandlerContext) -> Unit) {
        receivers.add { context, _: ByteArray -> handler(context) }
    }

    inline fun <reified U> receiver(crossinline handler: suspend (WebSocketEventHandlerContext, U) -> Unit) {
        receivers.add { context, data: ByteArray -> handler(context, Bcs.decodeFromByteArray<U>(data)) }
    }

    fun rawReceiver(handler: suspend (WebSocketEventHandlerContext, ByteArray) -> Unit) {
        receivers.add { context, data: ByteArray -> handler(context, data) }
    }
}

fun Route.webSocketBinary(
    path: String,
    registerEvents: WebSocketBinaryEventHandler.() -> Unit
): WebSocketSender {
    val handler = WebSocketBinaryEventHandler().apply(registerEvents)
    val connections = mutableListOf<Connection>()

    webSocket(path) {
        val user = call.user
        val connection = Connection(this, user)
        connections.add(connection)

        //TODO: migrate to binary
        val context = WebSocketEventHandlerContext(connections, connection)

        handler.handleConnection(context)

        for (frame in incoming) {
            if (frame is Frame.Binary) {
                val data = frame.readBytes()
                handler.handleDestination(context, data)
            }
        }

        connections.remove(connection)
        handler.handleDisconnection(context)
    }

    //TODO: migrate to binary
    return WebSocketSender(connections)
}