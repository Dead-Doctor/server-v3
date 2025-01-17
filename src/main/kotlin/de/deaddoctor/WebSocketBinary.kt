package de.deaddoctor

import de.deaddoctor.modules.LobbyModule.lobby
import de.deaddoctor.modules.games.MusicGuesserGame
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import xyz.mcxross.bcs.Bcs

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

class Handlers {
    val destinations = mutableListOf<suspend (WebSocketEventHandlerContext, ByteArray) -> Unit>()

    inline fun <reified U> destination(crossinline handler: suspend (WebSocketEventHandlerContext, U) -> Unit) {
        destinations.add { context, data: ByteArray -> handler(context, Bcs.decodeFromByteArray<U>(data)) }
    }
}

fun Route.webSocketBinary(
    path: String,
    registerEvents: Handlers.() -> Unit
): WebSocketSender {
    val handlers = Handlers().apply(registerEvents)
    val connections = mutableListOf<Connection>()

    webSocket(path) {
        val user = call.user
        val connection = Connection(this, user)
        connections.add(connection)

        //TODO: migrate to binary
        val webSocketEventHandlerContext = WebSocketEventHandlerContext(connections, connection)

//        handlers.connection?.let { it(webSocketEventHandlerContext) }

        for (frame in incoming) {
            if (frame is Frame.Binary) {
                val data = frame.readBytes()

                val packetType = data[0].toInt()
                val packetData = data.copyOfRange(1, data.size)
                handlers.destinations[packetType](webSocketEventHandlerContext, packetData)
            }

        }
        connections.remove(connection)
//        handlers.disconnection?.let { it(webSocketEventHandlerContext) }
    }

    //TODO: migrate to binary
    return WebSocketSender(connections)
}