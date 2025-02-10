package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.modules.LobbyModule.Lobby.Player
import de.deaddoctor.modules.LobbyModule.lobby
import de.deaddoctor.modules.games.MusicGuesserGame
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

object GameModule : Module {

    val gameTypes = mutableListOf<GameType<*>>()

    override fun path() = "game"

    init {
        register(GameType("music-guesser", "Music Guesser", ::MusicGuesserGame))
    }

    private fun register(type: GameType<*>) {
        gameTypes.add(type)
    }

    override fun Route.route() {
        for (type in gameTypes) {
            route(type.id) {
                get("{id}") {
                    val lobby = call.lobby ?: return@get call.respondRedirect("/games")
                    val user = call.trackedUser

                    if (!lobby.joined(user)) {
                        return@get call.respondRedirect("/lobby/${call.parameters["id"]}")
                    } else if (!lobby.active(user)) {
                        lobby.activate(lobby.getPlayer(user)!!)
                    }

                    if (lobby.gameSelected != type)
                        return@get call.respondRedirect("/game/${lobby.gameSelected.id}/${call.parameters["id"]}")

                    val game = lobby.game ?: return@get call.respondRedirect("/lobby/${call.parameters["id"]}")
                    game.get(call)
                }
            }
        }
    }

    fun getGameType(id: String) = gameTypes.find { it.id == id }

    val gameTypesInfo: List<GameType.Info>
        get() = gameTypes.map { GameType.Info(it) }

    class GameType<T : Game<T>>(
        val id: String,
        val name: String,
        val factory: (GameChannel, MutableMap<TrackedUser, Player>) -> T
    ) {
        @Serializable
        data class Info(val id: String, val name: String) {
            constructor(type: GameType<*>) : this(type.id, type.name)
        }
    }
}

class GameChannel(private val send: Destination<ByteArray>) {
    private var destinationCount: UByte = 0u

    inline fun <reified T> destination(): Destination<T> {
        return destination(serializer<T>())
    }

    fun <T> destination(serializer: KSerializer<T>): Destination<T> {
        val i = destinationCount++
        return GameDestination(this, i) { Bcs.encodeToBytes(serializer, it) }
    }

    class GameDestination<T>(private val channel: GameChannel, private val i: UByte, private val serializer: (T) -> ByteArray) : Destination<T> {
        private fun encodePacket(content: T): ByteArray {
            return byteArrayOf(i.toByte()) + serializer(content)
        }

        override fun toAll(content: T) {
            channel.send.toAll(encodePacket(content))
        }

        override fun toAll(connections: List<Connection>, content: T) {
            channel.send.toAll(connections, encodePacket(content))
        }

        override fun toUser(user: User, content: T) {
            channel.send.toUser(user, encodePacket(content))
        }

        override fun toConnection(connection: Connection, content: T) {
            channel.send.toConnection(connection, encodePacket(content))
        }

    }
}

class GameChannelEvents : ChannelEvents() {

    inline fun <reified T> receiverTyped(crossinline handler: suspend T.(Channel.Context) -> Unit) {
        receivers.add { ctx, _: ByteArray ->
            val reasonInternalError = CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Illegal state encountered.")
            val lobby = ctx.connection.lobby ?: return@add ctx.closeConnection(reasonInternalError)
            val game = lobby.game ?: return@add ctx.closeConnection(reasonInternalError)
            if (game !is T) return@add ctx.closeConnection(reasonInternalError)
            game.handler(ctx)
        }
    }

    inline fun <reified T, reified U> receiverTyped(crossinline handler: suspend T.(Channel.Context, U) -> Unit) {
        receivers.add { ctx, data: ByteArray ->
            val reasonInternalError = CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Illegal state encountered.")
            val lobby = ctx.connection.lobby ?: return@add ctx.closeConnection(reasonInternalError)
            val game = lobby.game ?: return@add ctx.closeConnection(reasonInternalError)
            if (game !is T) return@add ctx.closeConnection(reasonInternalError)
            game.handler(ctx, Bcs.decodeFromBytes<U>(data))
        }
    }
}

abstract class Game<T>(socketHandlerRegistrant: GameChannelEvents.() -> Unit) {

    val channelEvents = GameChannelEvents().apply(socketHandlerRegistrant)

    abstract suspend fun get(call: ApplicationCall)
}