package de.deaddoctor

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import xyz.mcxross.bcs.Bcs
import xyz.mcxross.bcs.Bcs.encodeToByteArray

open class WebSocketBinaryEventHandler {
    var handleConnection: suspend (WebSocketBinaryContext) -> Unit = {}
    val handleDisconnection: suspend (WebSocketBinaryContext) -> Unit = {}
    val receivers = mutableListOf<suspend (WebSocketBinaryContext, ByteArray) -> Unit>()

    suspend fun handleReceiver(context: WebSocketBinaryContext, data: ByteArray) {
        val packetType = data[0].toInt()
        val packetData = data.copyOfRange(1, data.size)
        receivers[packetType](context, packetData)
    }

    fun connection(handler: suspend (WebSocketBinaryContext) -> Unit) {
        handleConnection = handler
    }

    fun disconnection(handler: suspend (WebSocketBinaryContext) -> Unit) {
        handleConnection = handler
    }

    fun receiver(handler: suspend (WebSocketBinaryContext) -> Unit) {
        receivers.add { context, _: ByteArray -> handler(context) }
    }

    inline fun <reified U> receiver(crossinline handler: suspend (WebSocketBinaryContext, U) -> Unit) {
        receivers.add { context, data: ByteArray -> handler(context, Bcs.decodeFromByteArray<U>(data)) }
    }

    fun rawReceiver(handler: suspend (WebSocketBinaryContext, ByteArray) -> Unit) {
        receivers.add(handler)
    }
}

class WebSocketBinary : WebSocketBinaryEventHandler() {
    val connections = mutableListOf<Connection>()

    private var destinationCount: UByte = 0u

    inline fun <reified T> destination(): WebSocketBinaryDestination<T> {
        return destination(serializer<T>())
    }

    fun <T> destination(serializer: KSerializer<T>): WebSocketBinaryDestination<T> {
        val i = destinationCount++
        return WebSocketBinaryDestination(this, i, serializer)
    }
}

class WebSocketBinaryDestination<T>(val socket: WebSocketBinary, private val i: UByte, private val serializer: KSerializer<T>) {
    fun sendToAll(content: T) =
        sendToAll(socket.connections, content)

    fun sendToUser(user: User, content: T) =
        sendToAll(socket.connections.filter { it.user == user }, content)

    fun encodePacket(content: T): ByteArray {
        return byteArrayOf(i.toByte()) + encodeToByteArray(serializer, content)
    }

    fun sendToAll(connections: List<Connection>, content: T) = with(encodePacket(content)) {
        connections.forEach { sendMessageTo(it, this) }
    }

    fun sendToConnection(connection: Connection, content: T) =
        sendMessageTo(connection, encodePacket(content))

    fun sendMessageTo(connection: Connection, data: ByteArray) {
        try {
            val result = connection.session.outgoing.trySend(Frame.Binary(true, data))
            assert(result.isSuccess) { "Sending message to $connection failed: $result" }
        } catch (e: Exception) {
            println("Exception while trying to send:")
            throw e
        }
    }
}

class WebSocketBinaryContext(private val socket: WebSocketBinary, val connection: Connection) {
    val user = connection.user

    fun countConnections(user: User = connection.user) = socket.connections.count { it.user == user }

    suspend fun closeConnection(connection: Connection, reason: CloseReason) {
        connection.session.close(reason)
    }
}

fun Route.webSocketBinary(
    path: String
): WebSocketBinary {
    val socket = WebSocketBinary()

    webSocket(path) {
        val user = call.user
        val connection = Connection(this, user)
        socket.connections.add(connection)

        val context = WebSocketBinaryContext(socket, connection)

        socket.handleConnection(context)

        for (frame in incoming) {
            if (frame is Frame.Binary) {
                val data = frame.readBytes()
                socket.handleReceiver(context, data)
            }
        }

        socket.connections.remove(connection)
        socket.handleDisconnection(context)
    }
    return socket
}