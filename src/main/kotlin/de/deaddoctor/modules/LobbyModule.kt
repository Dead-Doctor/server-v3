package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable

object LobbyModule : Module {
    private val lobbies = mutableMapOf<String, Lobby>()

    override fun path() = "lobby"

    private val idCharacters = ('A'..'Z') + ('0'..'9')
    private fun generateId() = buildString {
        for (i in 0 until 4) append(idCharacters.random())
    }

    override fun Route.route() {
        get("/new") {
            var id = generateId()
            while (lobbies.containsKey(id)) {
                id = generateId()
            }
            lobbies[id] = Lobby(id)
            call.respondRedirect("/${path()}/$id")
        }

        get("/{id}") {
            val lobby = call.parameters["id"]?.let { lobbies[it] } ?: return@get call.respondRedirect("/games")

            call.respondPage("Lobby") {
                head {
                    addData("youInfo", YouInfo(call.trackedUser))
                    addData("lobbyInfo", lobby.info)
                    addScript("lobby/main")
                }
            }
        }
    }

    @Serializable
    data class YouInfo(
        val you: String,
        val admin: Boolean
    ) {
        constructor(user: TrackedUser) : this(
            user.id,
            user is AccountUser && user.admin
        )
    }

    class Lobby(private val id: String) {
        private val players = mutableMapOf<TrackedUser, Player>()
        private var host: TrackedUser? = null

        class Player(
            val user: TrackedUser,
            private val state: State,
            customName: String?,
            val disconnectJob: Job,
            private val score: Int
        ) {
            enum class State(val playing: Boolean) {
                LEFT(false),
                JOINED(true),
                CONNECTED(true)
            }

            private val name = if (user is AccountUser) user.name else customName!!

            val info: Info
                get() = Info(
                    user.id,
                    name,
                    user is AccountUser,
                    (user as? AccountUser)?.avatar,
                    state.playing,
                    score
                )

            @Serializable
            data class Info(
                val id: String,
                val name: String,
                val verified: Boolean,
                val avatar: String?,
                val playing: Boolean,
                val score: Int
            )
        }

        val info: Info
            get() = Info(
                players.map { it.value.info },
                host?.id ?: "null",
            )

        @Serializable
        data class Info(
            val players: List<Player.Info>,
            val host: String,
        )
    }
}