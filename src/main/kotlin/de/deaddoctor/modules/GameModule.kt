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

    val gameTypes = mutableListOf(
        MusicGuesserGame,
        QuizGame,
        ScotlandYardGame
    )

    private val channel = Channel()
    private val sendInvalid = channel.destination<Unit>()
    private val sendSuccess = channel.destination<Unit>()

    override fun Route.route() {
        get {
            call.respondPage("Games") {
                head {
                    val other = mutableMapOf<String, String>()
                    other["Chat"] = "/${ChatModule.path()}"
                    other["Snake"] = "/${SnakeModule.path()}"
                    if ((call.user as? AccountUser)?.admin == true) {
                        other["Test"] = "/${TestModule.path()}"
                        other["Websockets"] = "/${WebsocketModule.path()}"
                        other["Lobby Admin"] = "/${LobbyModule.path()}/admin"
                    }
                    addData("gameTypes", gameTypesInfo(call))
                    addData("otherGames", other)
                    addScript("${path()}/main")
                }
            }
        }
        openChannel("channel") { channel }

        fun Channel.Context.onCode(code: String) {
            if (LobbyModule.lobbyExists(code)) sendSuccess.toConnection(connection, Unit)
            else sendInvalid.toConnection(connection, Unit)
        }
        channel.receiver(Channel.Context::onCode)

        for (type in gameTypes) {
            route(type.id()) {
                get("{id}") {
                    val lobby = call.lobby ?: return@get call.respondRedirect("/${path()}")
                    val user = call.trackUser()

                    if (!lobby.joined(user)) {
                        return@get call.respondRedirect("/${LobbyModule.path()}/${call.parameters["id"]}")
                    } else if (!lobby.active(user)) {
                        lobby.activate(lobby.getPlayer(user)!!)
                    }

                    if (lobby.gameSelected != type)
                        return@get call.respondRedirect("/${path()}/${lobby.gameSelected.id()}/${call.parameters["id"]}")

                    val game =
                        lobby.game ?: return@get call.respondRedirect("/${LobbyModule.path()}/${call.parameters["id"]}")
                    game.get(call)
                }

                also {
                    with(type) {
                        it.staticRoutes()
                    }
                }
            }
        }
    }

    fun getGameType(id: String) = gameTypes.find { it.id() == id }

    fun gameTypesInfo(call: ApplicationCall): List<GameType.Info> = gameTypes.map { GameType.Info(it, call) }

}

interface GameType<T : Game<T>> {
    fun id(): String
    fun name(): String
    fun description(): String
    fun settings(): GameSettings
    suspend fun create(channel: GameChannel, lobby: Lobby, settings: GameSettings): T

    fun links(call: ApplicationCall): MutableMap<String, String>? = null

    /**
     * **Warning!** - Should never define routes that clash with lobby ids.
     */
    fun Route.staticRoutes() {}

    @Serializable
    data class Info(val id: String, val name: String, val description: String, val links: MutableMap<String, String>?) {
        constructor(type: GameType<*>, call: ApplicationCall) : this(
            type.id(),
            type.name(),
            type.description(),
            type.links(call)
        )
    }
}

abstract class Game<T>(
    channel: GameChannel,
    private val lobby: Lobby,
    socketHandlerRegistrant: GameChannelEvents.() -> Unit
) {

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

        override fun toAll(content: T) =
            channel.send.toAll(encodePacket(content))

        override fun toAll(connections: List<Connection>, content: T) =
            channel.send.toAll(connections, encodePacket(content))

        override fun toAllExcept(connection: Connection, content: T) =
            channel.send.toAllExcept(connection, encodePacket(content))

        override fun toAllExceptUser(user: User, content: T) =
            channel.send.toAllExceptUser(user, encodePacket(content))

        override fun toUser(user: User, content: T) =
            channel.send.toUser(user, encodePacket(content))

        override fun toConnection(connection: Connection, content: T) =
            channel.send.toConnection(connection, encodePacket(content))

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

open class GameSettings {
    open fun validate(): Boolean = true
}

open class GameSetting(val name: String) {

    open fun validate(): Boolean = true

    @Serializable
    data class Info(val id: String, val name: String) {
        val playerDropDown = mutableListOf<PlayerDropDown.Info>()
    }

    class PlayerDropDown(name: String, private val optional: Boolean = false) : GameSetting(name) {
        var selection: TrackedUser? = null

        val value: TrackedUser?
            get() {
                if (!validate()) throw IllegalStateException("Tried to get value of PlayerDropDown before it was initialized!")
                return selection
            }

        override fun validate() = optional || selection != null

        @Serializable
        data class Info(val value: String, val optional: Boolean = false) {
            companion object {
                operator fun invoke(id: String, setting: PlayerDropDown) = Info(id, setting.name).also {
                    it.playerDropDown.add(Info(setting.selection?.id ?: "", setting.optional))
                }
            }
        }
    }
}