package de.deaddoctor

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

open class ChannelEvents {
    var handleConnection: suspend (Channel.Context) -> Unit = {}
    val handleDisconnection: suspend (Channel.Context) -> Unit = {}
    val receivers = mutableListOf<suspend (Channel.Context, ByteArray) -> Unit>()

    suspend fun handleReceiver(context: Channel.Context, data: ByteArray) {
        val packetType = data[0].toInt()
        val packetData = data.copyOfRange(1, data.size)
        receivers[packetType](context, packetData)
    }

    fun connection(handler: suspend (Channel.Context) -> Unit) {
        handleConnection = handler
    }

    fun disconnection(handler: suspend (Channel.Context) -> Unit) {
        handleConnection = handler
    }

    fun receiver(handler: suspend (Channel.Context) -> Unit) {
        receivers.add { context, _: ByteArray -> handler(context) }
    }

    inline fun <reified U> receiver(crossinline handler: suspend (Channel.Context, U) -> Unit) {
        receivers.add { context, data: ByteArray -> handler(context, Bcs.decodeFromBytes<U>(data)) }
    }

    fun rawReceiver(handler: suspend (Channel.Context, ByteArray) -> Unit) {
        receivers.add(handler)
    }
}

class Channel : ChannelEvents() {
    val connections = mutableListOf<Connection>()

    private var destinationCount: UByte = 0u

    inline fun <reified T> destination(): Destination<T> {
        return destination(serializer<T>())
    }

    fun <T> destination(serializer: KSerializer<T>): Destination<T> {
        val i = destinationCount++
        return Destination(this, i, serializer)
    }

    class Destination<T>(val socket: Channel, private val i: UByte, private val serializer: KSerializer<T>) {
        fun sendToAll(content: T) =
            sendToAll(socket.connections, content)

        fun sendToUser(user: User, content: T) =
            sendToAll(socket.connections.filter { it.user == user }, content)

        fun encodePacket(content: T): ByteArray {
            return byteArrayOf(i.toByte()) + Bcs.encodeToBytes(serializer, content)
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

    class Context(private val socket: Channel, val connection: Connection) {
        val user = connection.user

        fun countConnections(user: User = connection.user) = socket.connections.count { it.user == user }

        suspend fun closeConnection(connection: Connection, reason: CloseReason) {
            connection.session.close(reason)
        }
    }
}

fun Route.openChannel(path: String, channel: Channel) {
    webSocket(path) {
        val user = call.user
        val connection = Connection(this, user)
        channel.connections.add(connection)

        val context = Channel.Context(channel, connection)

        channel.handleConnection(context)

        for (frame in incoming) {
            if (frame is Frame.Binary) {
                val data = frame.readBytes()
                channel.handleReceiver(context, data)
            }
        }

        channel.connections.remove(connection)
        channel.handleDisconnection(context)
    }
}