package de.deaddoctor

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.webSocketAddressable(
    s: String,
    registerEvents: WebSocketEventRegistrant.() -> Unit
): WebSocketSender {
    val handler = WebSocketEventRegistrant().apply(registerEvents)
    val connections = mutableListOf<Connection>()

    webSocket(s) {
        val user = call.user
        val connection = Connection(this, user)
        val webSocketEventHandlerContext = WebSocketEventHandlerContext(connections, connection)

        connections.add(connection)
        handler.connection?.let { it(webSocketEventHandlerContext) }

        for (frame in incoming) {
            if (frame is Frame.Text) {
                val text = frame.readText()
                val callback = handler.destinations[text.substringBefore('\n')] ?: continue
                callback(webSocketEventHandlerContext, text.substringAfter('\n'))
            }
        }
        connections.remove(connection)
        handler.disconnection?.let { it(webSocketEventHandlerContext) }
    }

    return WebSocketSender(connections)
}

class WebSocketEventRegistrant {
    var connection: (suspend (WebSocketEventHandlerContext) -> Unit)? = null
    var disconnection: (suspend (WebSocketEventHandlerContext) -> Unit)? = null
    val destinations = mutableMapOf<String, suspend (WebSocketEventHandlerContext, String) -> Unit>()

    inline fun connection(crossinline handler: suspend WebSocketEventHandlerContext.() -> Unit) {
        connection = { it.handler() }
    }

    inline fun destination(
        destination: String,
        crossinline handler: suspend WebSocketEventHandlerContext.() -> Unit
    ) {
        destinations[destination] = { ctx, _ -> ctx.handler() }
    }

    inline fun <reified T> destination(
        destination: String,
        crossinline handler: suspend WebSocketEventHandlerContext.(T) -> Unit
    ) {
        destinations[destination] = { ctx, body -> ctx.handler(Json.decodeFromString<T>(body)) }
    }

    inline fun disconnection(crossinline handler: suspend WebSocketEventHandlerContext.() -> Unit) {
        disconnection = { it.handler() }
    }
}

class WebSocketEventHandlerContext(connections: MutableList<Connection>, val connection: Connection) :
    WebSocketSender(connections) {
    val user = connection.user

    fun countConnections(user: User = connection.user) = connections.count { it.user == user }

    inline fun <reified T> sendBack(content: T) = sendToConnection(connection, content)
}

open class WebSocketSender(val connections: MutableList<Connection>) {
    inline fun <reified T> sendToAll(content: T) =
        sendToAll(connections, content)

    inline fun <reified T> sendToUser(user: User, content: T) =
        sendToAll(connections.filter { it.user == user }, content)

    inline fun <reified T> sendToAll(connections: List<Connection>, content: T) =
        with(Json.encodeToString(content)) { connections.forEach { sendMessageTo(it, this) } }

    inline fun <reified T> sendToConnection(connection: Connection, content: T) =
        sendMessageTo(connection, Json.encodeToString(content))

    fun sendMessageTo(connection: Connection, text: String) {
        val result = connection.session.outgoing.trySend(Frame.Text(text))
        assert(result.isSuccess) { "Sending following message to $connection failed: $result" }
    }

    suspend fun closeConnection(connection: Connection, reason: CloseReason) {
        connection.session.close(reason)
    }
}

data class Connection(val session: DefaultWebSocketServerSession, val user: User)