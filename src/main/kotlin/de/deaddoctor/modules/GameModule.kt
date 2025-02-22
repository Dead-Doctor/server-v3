package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import de.deaddoctor.modules.LobbyModule.Lobby
import de.deaddoctor.modules.LobbyModule.lobby
import de.deaddoctor.modules.games.MusicGuesserGame
import de.deaddoctor.modules.games.QuizGame
import de.deaddoctor.modules.games.ScotlandYardGame
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.html.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

object GameModule : Module {

    override fun path() = "game"

    val gameTypes = mutableListOf<GameType<*>>(
        MusicGuesserGame,
        QuizGame,
        ScotlandYardGame
    )

    override fun Route.route() {
        get {
            call.respondPage("Games") {
                content {
                    h1 { +"Games" }
                    section(classes = "grid") {
                        for (type in gameTypes) {
                            div {
                                h3 { +type.name() }
                                p { +type.description() }
                                a(href = "/lobby/new?game=${type.id()}") { +"Create Lobby" }
                            }
                        }

                        a(href = "/${ChatModule.path()}") { +"Chat" }
                        a(href = "/${SnakeModule.path()}") { +"Snake" }
                        if ((call.user as? AccountUser)?.admin == true) {
                            a(href = "/${TestModule.path()}") { +"Test" }
                            a(href = "/${WebsocketModule.path()}") { +"Websockets" }
                        }
                    }
                }
            }
        }
        for (type in gameTypes) {
            route(type.id()) {
                get("{id}") {
                    val lobby = call.lobby ?: return@get call.respondRedirect("/game")
                    val user = call.trackUser()

                    if (!lobby.joined(user)) {
                        return@get call.respondRedirect("/lobby/${call.parameters["id"]}")
                    } else if (!lobby.active(user)) {
                        lobby.activate(lobby.getPlayer(user)!!)
                    }

                    if (lobby.gameSelected != type)
                        return@get call.respondRedirect("/game/${lobby.gameSelected.id()}/${call.parameters["id"]}")

                    val game = lobby.game ?: return@get call.respondRedirect("/lobby/${call.parameters["id"]}")
                    game.get(call)
                }
            }
        }
    }

    fun getGameType(id: String) = gameTypes.find { it.id() == id }

    val gameTypesInfo: List<GameType.Info>
        get() = gameTypes.map { GameType.Info(it) }

}

interface GameType<T : Game<T>> {
    fun id(): String
    fun name(): String
    fun description(): String
    suspend fun create(channel: GameChannel, lobby: Lobby): T

    @Serializable
    data class Info(val id: String, val name: String, val description: String) {
        constructor(type: GameType<*>) : this(type.id(), type.name(), type.description())
    }
}

abstract class Game<T>(channel: GameChannel, private val lobby: Lobby, socketHandlerRegistrant: GameChannelEvents.() -> Unit) {

    val channelEvents = GameChannelEvents().apply(socketHandlerRegistrant)

    private val sendFinish = channel.destination<String>()

    val lobbyInfo
        get() = Lobby.Info(lobby)

    fun isOperator(user: TrackedUser) = lobby.isOperator(user)

    fun gameWon(winner: TrackedUser) = lobby.gameWon(winner)

    fun finish() = lobby.endGame(sendFinish)

    abstract suspend fun get(call: ApplicationCall)
}

suspend fun ApplicationCall.respondGame(game: GameType<*>, encodedData: FlowOrMetaDataOrPhrasingContent.() -> Unit) {
    respondHtmlTemplate(PageLayout(user, request.uri, game.name()), HttpStatusCode.OK) {
        head {
            encodedData()
            addScript("game/${game.id()}/main")
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

    class GameDestination<T>(
        private val channel: GameChannel,
        private val i: UByte,
        private val serializer: (T) -> ByteArray
    ) : Destination<T> {
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
        receivers[receivers.size.toUByte()] = handler@{ ctx, _: ByteArray ->
            val reasonInternalError = CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Illegal state encountered.")
            val lobby = ctx.connection.lobby ?: return@handler ctx.closeConnection(reasonInternalError)
            val game = lobby.game ?: return@handler ctx.closeConnection(reasonInternalError)
            if (game !is T) return@handler ctx.closeConnection(reasonInternalError)
            game.handler(ctx)
        }
    }

    inline fun <reified T, reified U> receiverTyped(crossinline handler: suspend T.(Channel.Context, U) -> Unit) {
        receivers[receivers.size.toUByte()] = handler@{ ctx, data: ByteArray ->
            val reasonInternalError = CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Illegal state encountered.")
            val lobby = ctx.connection.lobby ?: return@handler ctx.closeConnection(reasonInternalError)
            val game = lobby.game ?: return@handler ctx.closeConnection(reasonInternalError)
            if (game !is T) return@handler ctx.closeConnection(reasonInternalError)
            game.handler(ctx, Bcs.decodeFromBytes<U>(data))
        }
    }
}