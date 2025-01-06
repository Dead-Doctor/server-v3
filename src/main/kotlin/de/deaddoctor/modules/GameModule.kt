package de.deaddoctor.modules

import de.deaddoctor.Module
import de.deaddoctor.modules.games.MusicGuesserGame
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.reflect.KClass

object GameModule : Module {

    private val gameTypes = mutableListOf<GameType>()
    private val games = mutableMapOf<String, Game>()

    override fun path() = "game"

    init {
        register(GameType("music-guesser", "Music Guesser", MusicGuesserGame::class))
    }

    private fun register(type: GameType) {
        gameTypes.add(type)
    }

    override fun Route.route() {
        for (type in gameTypes) {
            route(type.id) {
                get("{id}") {
                    val id = call.parameters["id"] ?: return@get call.respondRedirect("/lobby")
                    val game = games[id] ?: return@get call.respondRedirect("/lobby/$id")
                    if (game::class != type.instanceClass)
                        throw IllegalStateException("Expected game instance of type '${game::class.qualifiedName}' but got '${type.instanceClass.qualifiedName}' ")
                    game.get(call)
                }
            }
        }
    }

    data class GameType(val id: String, val name: String, val instanceClass: KClass<*>)
}

interface Game {
    suspend fun get(call: ApplicationCall)
}