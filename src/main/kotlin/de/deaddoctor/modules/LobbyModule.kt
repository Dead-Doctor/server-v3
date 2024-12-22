package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import de.deaddoctor.modules.MusicGuesserModule.Game.*
import de.deaddoctor.modules.MusicGuesserModule.Packet
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable

object LobbyModule : Module {
    override fun path() = "lobby"

    private val idCharacters = ('A'..'Z') + ('0'..'9')
    private fun generateId() = buildString {
        for (i in 0 until 4) append(idCharacters.random())
    }

    private val lobbies = mutableMapOf<String, Lobby>()

    private val ApplicationCall.lobby: Lobby?
        get() = parameters["id"]?.let { lobbies[it] }
    private val Connection.lobby: Lobby?
        get() = session.call.lobby

    override fun Route.route() {
        get("new") {
            var id = generateId()
            while (lobbies.containsKey(id)) {
                id = generateId()
            }
            lobbies[id] = Lobby(id)
            call.respondRedirect("/${path()}/$id")
        }

        route("{id}") {
            get {
                val lobby = call.lobby ?: return@get call.respondRedirect("/games")
                val user = call.trackedUser

                if (!lobby.joinedAndActive(user) && (user is AccountUser || lobby.joined(user))) {
                    lobby.activate(user)
                }

                call.respondPage("Lobby") {
                    head {
                        addData("youInfo", YouInfo(call.trackedUser))
                        addData("lobbyInfo", Lobby.Info(lobby))
                        addScript("lobby/main")
                    }
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

        fun joined(user: TrackedUser) = players.containsKey(user)
        fun joinedAndActive(user: TrackedUser) = players[user]?.state?.active ?: false

        fun activate(user: TrackedUser, name: String? = null) {
            val initialActivation = !joined(user)

            if (initialActivation) {
                val player = Player(user, Player.State.ACTIVE, name)
                players[user] = player
//                sendToAll(Packet("playerJoined", Player.Info(player)))
            } else {
                players[user]?.state = Player.State.ACTIVE
//                sendToAll(Packet("playerStateChanged", PlayerStateChanged(user.id, players[user]!!.playing)))
            }

//            if (host == null) promote(user)
        }

        class Player(
            val user: TrackedUser,
            var state: State,
            customName: String?,
        ) {
            val disconnectJob: Job? = null
            val score: Int = 0

            enum class State(val active: Boolean) {
                INACTIVE(false),
                ACTIVE(true),
                ACTIVE_CONNECTED(true)
            }

            private val name = if (user is AccountUser) user.name else customName!!

            @Serializable
            data class Info(
                val id: String,
                val name: String,
                val verified: Boolean,
                val avatar: String?,
                val active: Boolean,
                val score: Int
            ) {
                constructor(o: Player) : this(
                    o.user.id,
                    o.name,
                    o.user is AccountUser,
                    (o.user as? AccountUser)?.avatar,
                    o.state.active,
                    o.score
                )
            }
        }

        @Serializable
        data class Info(
            val players: List<Player.Info>,
            val host: String
        ) {
            constructor(o: Lobby) : this(
                o.players.map { Player.Info(it.value) },
            o.host?.id ?: "null",
            )
        }
    }
}