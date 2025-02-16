package de.deaddoctor

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory

open class ChannelEvents {
    private val logger = LoggerFactory.getLogger(javaClass)

    var handleConnection: suspend (Channel.Context) -> Unit = {}
    var handleDisconnection: suspend (Channel.Context) -> Unit = {}
    val receivers = mutableMapOf<UByte, suspend (Channel.Context, ByteArray) -> Unit>()

    suspend fun handleReceiver(context: Channel.Context, data: ByteArray) {
        val packetType = data[0].toUByte()
        val packetData = data.copyOfRange(1, data.size)

        val handler = receivers[packetType]
        if (handler == null) {
            logger.error("Invalid packet type received: $packetType")
            return
        }
        handler(context, packetData)
    }

    fun connection(handler: suspend (Channel.Context) -> Unit) {
        handleConnection = handler
    }

    fun disconnection(handler: suspend (Channel.Context) -> Unit) {
        handleDisconnection = handler
    }

    fun receiver(handler: suspend (Channel.Context) -> Unit) {
        receivers[receivers.size.toUByte()] = { context, _: ByteArray -> handler(context) }
    }

    inline fun <reified U> receiver(crossinline handler: suspend (Channel.Context, U) -> Unit) {
        receivers[receivers.size.toUByte()] = { context, data: ByteArray -> handler(context, Bcs.decodeFromBytes<U>(data)) }
    }

    fun rawReceiver(handler: suspend (Channel.Context, ByteArray) -> Unit, id: UByte) {
        receivers[id] = handler
    }
}

interface Destination<T> {
    fun toAll(content: T)
    fun toAll(connections: List<Connection>, content: T)
    fun toUser(user: User, content: T)
    fun toConnection(connection: Connection, content: T)
}

class Channel : ChannelEvents() {
    val connections = mutableListOf<Connection>()

    private var destinationCount: UByte = 0u

    inline fun <reified T> destination(): Destination<T> {
        return destination(serializer<T>())
    }

    fun <T> destination(serializer: KSerializer<T>): Destination<T> {
        val i = destinationCount++
        return ChannelDestination(this, i) { Bcs.encodeToBytes(serializer, it) }
    }

    fun destinationRaw(id: UByte): Destination<ByteArray> {
        return ChannelDestination(this, id) { it }
    }

    class ChannelDestination<T>(private val channel: Channel, private val i: UByte, private val serializer: (T) -> ByteArray) : Destination<T> {
        override fun toAll(content: T) =
            toAll(channel.connections, content)

        override fun toUser(user: User, content: T) =
            toAll(channel.connections.filter { it.user == user }, content)

        private fun encodePacket(content: T): ByteArray {
            return byteArrayOf(i.toByte()) + serializer(content)
        }

        override fun toAll(connections: List<Connection>, content: T) = with(encodePacket(content)) {
            connections.forEach { rawToConnection(it, this) }
        }

        override fun toConnection(connection: Connection, content: T) =
            rawToConnection(connection, encodePacket(content))

        private fun rawToConnection(connection: Connection, data: ByteArray) {
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

        suspend fun closeConnection(reason: CloseReason) {
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
