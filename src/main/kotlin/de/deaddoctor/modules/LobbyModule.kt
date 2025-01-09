package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.full.createInstance
import kotlin.time.Duration.Companion.seconds

object LobbyModule : Module {
    override fun path() = "lobby"

    private val idCharacters = ('A'..'Z') + ('0'..'9')
    private fun generateId() = buildString {
        for (i in 0 until 4) append(idCharacters.random())
    }

    private lateinit var socket: WebSocketSender

    private val lobbies = mutableMapOf<String, Lobby>()

    val ApplicationCall.lobby: Lobby?
        get() = parameters["id"]?.let { lobbies[it] }
    val Connection.lobby: Lobby?
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

                if (!lobby.joined(user)) {
                    if (user is AccountUser)
                        lobby.joinActivate(user)
                } else if (!lobby.active(user)) {
                    lobby.activate(lobby.getPlayer(user)!!)
                }

                call.respondPage("Lobby") {
                    head {
                        addData("youInfo", YouInfo(call.trackedUser))
                        addData("lobbyInfo", Lobby.Info(lobby))
                        addData("gameTypes", GameModule.gameTypesInfo)
                        addData("gameSelected", lobby.gameSelected.id)
                        addScript("lobby/main")
                    }
                }
            }

            webSocketBinary("wsBin") {
                connection {
                    println("Connected!")
                }

                disconnection {
                    println("Disconnected!")
                }

                message { data: ByteArray ->
                    println("Received data!")
                }
            }

            socket = webSocketAddressable("ws") {
                connection {
                    val lobby = connection.lobby
                    if (lobby == null || user !is TrackedUser) {
                        closeConnection(
                            connection, CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Illegal state encountered.")
                        )
                        return@connection
                    }
                    if (!lobby.active(user)) return@connection
                    lobby.activeConnect(lobby.getPlayer(user)!!)
                }

                fun checkName(name: String): MutableList<String> {
                    val nameErrors = mutableListOf<String>()
                    if (name.length < 3) {
                        nameErrors.add("Has to be at least <samp>3</samp> characters long.")
                    }
                    if (name.length > 20) {
                        nameErrors.add("Can't be longer than <samp>20</samp> characters.")
                    }
                    val allowedSpecialCharacters = "-_.!?"
                    if (!name.all { it.isLetterOrDigit() || it in allowedSpecialCharacters }) {
                        nameErrors.add(
                            "Can only contain <samp>letters</samp>, <samp>digits</samp> or any of the following: ${
                            allowedSpecialCharacters.toCharArray().joinToString(", ") { "<samp>$it</samp>" }
                        }.")
                    }
                    return nameErrors
                }
                destination("checkName") { name: String ->
                    val errors = checkName(name)
                    sendBack(Packet("checkedName", errors))
                }
                destination("join") { name: String ->
                    val lobby = connection.lobby ?: return@destination
                    if (user !is TrackedUser || user is AccountUser || lobby.joined(user) || checkName(name).isNotEmpty()) return@destination
                    val player = lobby.joinActivate(user, name)
                    lobby.activeConnect(player)
                    sendToUser(user, Packet("join", user.id))
                }
                disconnection {
                    val lobby = connection.lobby ?: return@disconnection
                    if (user !is TrackedUser || !lobby.joined(user) || countConnections(user) != 0) return@disconnection
                    lobby.activeDisconnect(lobby.getPlayer(user)!!)
                }
                destination("promote") { playerId: String ->
                    val lobby = connection.lobby ?: return@destination
                    val player = TrackedUser(playerId)
                    if (user !is TrackedUser || !lobby.isOperator(user) || !lobby.joined(player)) return@destination
                    lobby.promote(player)
                }
                destination("kick") { playerId: String ->
                    val lobby = connection.lobby ?: return@destination
                    val player = TrackedUser(playerId)
                    if (user !is TrackedUser || !lobby.isOperator(user) || !lobby.joined(player)) return@destination
                    lobby.kick(player)
                }
                destination("gameSelected") { gameSelected: String ->
                    val lobby = connection.lobby ?: return@destination
                    val gameType = GameModule.getGameType(gameSelected)
                    if (user !is TrackedUser || !lobby.isOperator(user) || gameType == null) return@destination
                    lobby.selectGame(gameType)
                }
                destination("beginGame") {
                    val lobby = connection.lobby ?: return@destination
                    if (user !is TrackedUser || !lobby.isOperator(user) || lobby.game != null) return@destination
                    lobby.beginGame()
                }
            }
        }
    }

    @Serializable
    data class Packet<T>(val type: String, val data: T)

    @Serializable
    data class YouInfo(
        val id: String,
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
        var gameSelected = GameModule.gameTypes[0]
        var game: Game<*>? = null

        fun joined(user: TrackedUser) = players.containsKey(user)
        fun active(user: TrackedUser) = players[user]?.state?.active ?: false
        fun getPlayer(user: TrackedUser) = players[user]

        fun joinActivate(user: TrackedUser, name: String? = null): Player {
            val player = Player(user, Player.State.ACTIVE, name)
            players[user] = player
            sendToAll(Packet("playerJoined", Player.Info(player)))

            if (host == null) promote(user)

            return player
        }

        fun activate(player: Player) {
            player.state = Player.State.ACTIVE
            sendToAll(Packet("playerActiveChanged", PlayerActiveChanged(player)))
        }

        private fun deactivate(player: Player) {
            player.state = Player.State.INACTIVE

//            if (round?.players?.remove(user) == true) {
//                if (round!!.players.isEmpty()) {
//                    round = null
//                    sendToAll(Packet("round", roundInfo))
//                } else {
//                    maybeShowResults()
//                }
//            }

            player.disconnectJob.cancel()
            sendToAll(Packet("playerActiveChanged", PlayerActiveChanged(player)))
            val firstRemainingPlayer = players.values.firstOrNull { it.state.active }
            if (firstRemainingPlayer == null) {
//                destroyLobby()
                return
            }
            if (player.user == host) promote(firstRemainingPlayer.user)
        }

        fun activeConnect(player: Player) {
            player.state = Player.State.ACTIVE_CONNECTED
            player.disconnectJob.cancel()
        }

        fun activeDisconnect(player: Player) {
            if (player.state != Player.State.ACTIVE_CONNECTED) return

            player.state = Player.State.ACTIVE
            player.disconnectJob = CoroutineScope(Job()).launch {
                delay(5.seconds)
                deactivate(player)
            }
        }

        fun isOperator(user: TrackedUser) = user == host || (user is AccountUser && user.admin)

        fun promote(user: TrackedUser) {
            host = user
            sendToAll(Packet("hostChanged", host?.id))
        }

        fun kick(user: TrackedUser) {
            val player = getPlayer(user)!!
            deactivate(player)
            sendToUser(user, Packet("kicked", "You got kicked"))
        }

        fun selectGame(gameType: GameModule.GameType<*>) {
            gameSelected = gameType
            sendToAll(Packet("gameSelected", gameType.id))
        }

        fun beginGame() {
            game = gameSelected.instanceClass.createInstance()
            sendToAll(Packet("gameStarted", "/game/${gameSelected.id}/$id"))
        }

        class Player(
            val user: TrackedUser,
            var state: State,
            customName: String?,
        ) {
            private val name = if (user is AccountUser) user.name else customName!!
            var disconnectJob: Job = Job()
            val score: Int = 0

            enum class State(val active: Boolean) {
                INACTIVE(false),
                ACTIVE(true),
                ACTIVE_CONNECTED(true)
            }

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

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Player) return false
                return user == other.user
            }

            override fun hashCode() = user.hashCode()
        }

        @Serializable
        data class PlayerActiveChanged(val player: String, val active: Boolean) {
            constructor(player: Player) : this(
                player.user.id,
                player.state.active,
            )
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

        private inline fun <reified T> sendToAll(packet: Packet<T>) {
            socket.sendToAll(socket.connections.filter { it.lobby == this }, packet)
            //TODO: send to all players even those that are only connected through a game websocket
        }

        private inline fun <reified T> sendToUser(user: TrackedUser, packet: Packet<T>) {
            socket.sendToUser(user, packet)
            //TODO: send to all players even those that are only connected through a game websocket
        }
    }
}