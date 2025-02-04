package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.modules.LobbyModule.lobby
import de.deaddoctor.modules.games.MusicGuesserGame
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object GameModule : Module {

    val gameTypes = mutableListOf<GameType<*>>()

    override fun path() = "game"

    init {
        register(GameType("music-guesser", "Music Guesser", MusicGuesserGame::class))
    }

    private fun register(type: GameType<*>) {
        gameTypes.add(type)
    }

    override fun Route.route() {
        for (type in gameTypes) {
            route(type.id) {
                route("{id}") {
                    get {
                        val lobby = call.lobby ?: return@get call.respondRedirect("/games")
                        val user = call.trackedUser

                        if (!lobby.joined(user)) {
                            return@get call.respondRedirect("/lobby/${call.parameters["id"]}}")
                        } else if (!lobby.active(user)) {
                            lobby.activate(lobby.getPlayer(user)!!)
                        }

                        val game = lobby.game ?: return@get call.respondRedirect("/lobby/${call.parameters["id"]}}")
                        //TODO: this shouldn't crash
                        if (game::class != type.instanceClass)
                            throw IllegalStateException("Expected game instance of type '${game::class.qualifiedName}' but got '${type.instanceClass.qualifiedName}' ")
                        game.get(call)
                    }
                }
            }
        }
    }

    fun getGameType(id: String) = gameTypes.find { it.id == id }

    val gameTypesInfo: List<GameType.Info>
        get() = gameTypes.map { GameType.Info(it) }

    class GameType<T : Game<T>>(val id: String, val name: String, val instanceClass: KClass<T>) {
        @Serializable
        data class Info(val id: String, val name: String) {
            constructor(type: GameType<*>) : this(type.id, type.name)
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