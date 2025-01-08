package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.modules.LobbyModule.lobby
import de.deaddoctor.modules.games.MusicGuesserGame
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import xyz.mcxross.bcs.Bcs
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

    @OptIn(ExperimentalStdlibApi::class)
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
                        if (game::class != type.instanceClass)
                            throw IllegalStateException("Expected game instance of type '${game::class.qualifiedName}' but got '${type.instanceClass.qualifiedName}' ")
                        game.get(call)
                    }

                    webSocket("ws") {
                        for (frame in incoming) {
                            val lobby = call.lobby ?: return@webSocket close(CloseReason(CloseReason.Codes.NORMAL, "No lobby"))
                            val gameG = lobby.game ?: return@webSocket close(CloseReason(CloseReason.Codes.NORMAL, "No game"))
                            val game = gameG as MusicGuesserGame

                            val data = frame.readBytes()
                            println("data: ${data.toHexString()}")
                            val packetType = data[0].toInt()
                            println("packetType: $packetType")
                            val packetData = data.copyOfRange(1, data.size)
                            println("packetData: ${packetData.toHexString()}")

                            game.handlers.destinations[packetType](game, packetData)
                        }
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

abstract class Game<T>(socketHandlerRegistrant: Handlers<T>.() -> Unit) {
    class Handlers<T> {
        val destinations = mutableListOf<suspend T.(ByteArray) -> Unit>()

        inline fun <reified U> destination(crossinline handler: suspend T.(U) -> Unit) {
            destinations.add { data: ByteArray -> handler(Bcs.decodeFromByteArray<U>(data)) }
        }
    }

    val handlers = Handlers<T>().apply(socketHandlerRegistrant)

    abstract suspend fun get(call: ApplicationCall)
}