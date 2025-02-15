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
import kotlin.time.Duration.Companion.seconds

object LobbyModule : Module {
    override fun path() = "lobby"

    private val idCharacters = ('A'..'Z') + ('0'..'9')
    private fun generateId() = buildString {
        for (i in 0 until 4) append(idCharacters.random())
    }

    private val channel = Channel()
    private val sendCheckedName = channel.destination<List<String>>()
    private val sendJoin = channel.destination<String>()
    private val sendPlayerJoined = channel.destination<Lobby.Player.Info>()
    private val sendPlayerActiveChanged = channel.destination<Lobby.PlayerActiveChanged>()
    private val sendPlayerScoreChanged = channel.destination<Pair<String, Int>>()
    private val sendHostChanged = channel.destination<String>()
    private val sendKicked = channel.destination<String>()
    private val sendGameSelected = channel.destination<String>()
    private val sendGameStarted = channel.destination<String>()
    private val sendGame = channel.destinationRaw(100u)

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
                val user = call.trackUser()

                if (!lobby.joined(user)) {
                    if (user is AccountUser)
                        lobby.joinActivate(user)
                } else if (!lobby.active(user)) {
                    lobby.activate(lobby.getPlayer(user)!!)
                }

                //          (might be problematic because games would need to include entire state in get response)
                //TODO: show game running and button to spectate (^) (cant redirect instantly because user might have to join and select name)
                //TODO: caching errors when navigating back from game to lobby (e.g. new players that have joined will only be shown after refresh)

                call.respondPage("Lobby") {
                    head {
                        addData("youInfo", YouInfo(call.trackedUser))
                        addData("lobbyInfo", Lobby.Info(lobby))
                        addData("gameTypes", GameModule.gameTypesInfo)
                        addData("gameSelected", lobby.gameSelected.id())
                        addScript("lobby/main")
                    }
                }
            }

            openChannel("ws", channel)

            suspend fun Channel.Context.connect() {
                val lobby = connection.lobby
                if (lobby == null || user !is TrackedUser) {
                    closeConnection(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Illegal state encountered."))
                    return
                }
                if (!lobby.active(user)) return
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
            fun Channel.Context.checkName(name: String) {
                val errors = checkName(name)
                sendCheckedName.toConnection(connection, errors)
            }
            fun Channel.Context.join(name: String) {
                val lobby = connection.lobby ?: return
                if (user !is TrackedUser || user is AccountUser || lobby.joined(user) || checkName(name).isNotEmpty()) return
                val player = lobby.joinActivate(user, name)
                lobby.activeConnect(player)
                sendJoin.toUser(user, user.id)
            }
            fun Channel.Context.disconnect() {
                val lobby = connection.lobby ?: return
                if (user !is TrackedUser || !lobby.joined(user) || countConnections() != 0) return
                lobby.activeDisconnect(lobby.getPlayer(user)!!)
            }
            fun Channel.Context.promote(playerId: String) {
                val lobby = connection.lobby ?: return
                val player = TrackedUser(playerId)
                if (user !is TrackedUser || !lobby.isOperator(user) || !lobby.joined(player)) return
                lobby.promote(player)
            }
            fun Channel.Context.kick(playerId: String) {
                val lobby = connection.lobby ?: return
                val player = TrackedUser(playerId)
                if (user !is TrackedUser || !lobby.isOperator(user) || !lobby.joined(player)) return
                lobby.kick(player)
            }
            fun Channel.Context.gameSelected(gameSelected: String) {
                val lobby = connection.lobby ?: return
                val gameType = GameModule.getGameType(gameSelected)
                if (user !is TrackedUser || !lobby.isOperator(user) || gameType == null || lobby.isRunning) return
                lobby.selectGame(gameType)
            }
            suspend fun Channel.Context.beginGame() {
                val lobby = connection.lobby ?: return
                if (user !is TrackedUser || !lobby.isOperator(user) || lobby.game != null) return
                lobby.beginGame()
            }
            suspend fun Channel.Context.game(data: ByteArray) {
                val lobby = connection.lobby ?: return
                val game = lobby.game ?: return
                game.channelEvents.handleReceiver(this, data)
            }

            channel.connection(Channel.Context::connect)
            channel.receiver(Channel.Context::checkName)
            channel.receiver(Channel.Context::join)
            channel.disconnection(Channel.Context::disconnect)
            channel.receiver(Channel.Context::promote)
            channel.receiver(Channel.Context::kick)
            channel.receiver(Channel.Context::gameSelected)
            channel.receiver(Channel.Context::beginGame)
            channel.rawReceiver(Channel.Context::game, 100u)
        }
    }

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

    class Lobby(val id: String) {
        private val players = mutableMapOf<TrackedUser, Player>()
        private var host: TrackedUser? = null
        var gameSelected = GameModule.gameTypes[0]
        var game: Game<*>? = null
        fun joined(user: TrackedUser) = players.containsKey(user)

        val activePlayers
            get() = players.filter { it.value.state.active }

        fun active(user: TrackedUser) = players[user]?.state?.active ?: false
        fun getPlayer(user: TrackedUser) = players[user]
        fun joinActivate(user: TrackedUser, name: String? = null): Player {
            val player = Player(user, Player.State.ACTIVE, name)
            players[user] = player
            sendPlayerJoined.toAll(Player.Info(player))

            if (host == null) promote(user)

            return player
        }

        fun activate(player: Player) {
            player.state = Player.State.ACTIVE
            sendPlayerActiveChanged.toAll(PlayerActiveChanged(player))
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
            sendPlayerActiveChanged.toAll(PlayerActiveChanged(player))
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

            sendHostChanged.toAll(user.id)
        }

        fun kick(user: TrackedUser) {
            val player = getPlayer(user)!!
            deactivate(player)
            sendKicked.toUser(user, "You got kicked")
        }

        fun selectGame(gameType: GameType<*>) {
            gameSelected = gameType
            sendGameSelected.toAll(gameType.id())
        }

        suspend fun beginGame() {
            //TODO: configurable settings
            val channel = GameChannel(sendGame)
            //TODO: loading animation
            game = gameSelected.create(channel, this)
            sendGameStarted.toAll("/game/${gameSelected.id()}/$id")
        }

        fun gameWon(winner: TrackedUser) {
            val player = players[winner]!!
            player.score++
            sendPlayerScoreChanged.toAll(winner.id to player.score)
        }

        fun endGame(sendFinish: Destination<String>) {
            sendFinish.toAll("/lobby/$id")
            game = null
        }

        val isRunning
            get() = game != null

        class Player(
            val user: TrackedUser,
            var state: State,
            customName: String?,
        ) {
            private val name = if (user is AccountUser) user.name else customName!!
            var disconnectJob: Job = Job()
            var score: Int = 0

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
            constructor(lobby: Lobby) : this(
                lobby.players.map { Player.Info(it.value) },
                lobby.host?.id ?: "null",
            )
        }
    }
}