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
import org.slf4j.LoggerFactory
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.time.Duration.Companion.seconds

object LobbyModule : Module {
    override fun path() = "lobby"

    private val logger = LoggerFactory.getLogger(LobbyModule::class.java)

    private val idCharacters = 'A'..'Z'
    private fun generateId() = (0 until 4).joinToString("") { idCharacters.random().toString() }

    private val channel = Channel()
    private val sendCheckedName = channel.destination<List<String>>()
    private val sendJoin = channel.destination<String>()
    private val sendPlayerJoined = channel.destination<Lobby.Player.Info>()
    private val sendPlayerActiveChanged = channel.destination<Lobby.PlayerActiveChanged>()
    private val sendPlayerScoreChanged = channel.destination<Pair<String, Int>>()
    private val sendHostChanged = channel.destination<String>()
    private val sendKicked = channel.destination<String>()
    private val sendGameSelected = channel.destination<Pair<String, Pair<List<GameSetting.Info>, Boolean>>>()
    private val sendGameSettingChanged = channel.destination<Pair<GameSetting.Info, Boolean>>()
    private val sendGameStarted = channel.destination<String>()
    private val sendGameEnded = channel.destination<Unit>()
    private val sendGame = channel.destinationRaw(100u)

    private val lobbies = mutableMapOf<String, Lobby>()

    val ApplicationCall.lobby: Lobby?
        get() = parameters["id"]?.let { lobbies[it] }
    val Connection.lobby: Lobby?
        get() = session.call.lobby

    fun lobbyExists(id: String) = lobbies.containsKey(id)

    override fun Route.route() {
        get("new") {
            var id = generateId()
            while (lobbies.containsKey(id)) {
                id = generateId()
            }
            val gameType = call.parameters["game"]?.let(GameModule::getGameType)
            lobbies[id] = Lobby(id, gameType ?: GameModule.gameTypes[0])
            call.respondRedirect("/${path()}/$id")
        }

        get("admin") {
            val user = call.user
            if (user !is AccountUser || !user.admin) return@get call.respondRedirect("/${GameModule.path()}")
            call.respondPage("Lobby Admin") {
                head {
                    addData("lobbies", lobbies.map { Lobby.Info(it.value) })
                    addScript("${path()}/admin")
                }
            }
        }

        route("{id}") {
            get {
                val lobby = call.lobby
                if (lobby == null) {
                    val corrected = call.parameters["id"]?.uppercase()
                    if (lobbies.containsKey(corrected))
                        return@get call.respondRedirect("/${path()}/$corrected")
                    return@get call.respondRedirect("/${GameModule.path()}?invalid-code")
                }
                val user = call.trackUser()

                if (!lobby.joined(user)) {
                    if (user is AccountUser)
                        lobby.joinActivate(user)
                } else if (!lobby.active(user)) {
                    lobby.activate(lobby.getPlayer(user)!!)
                }

                // (might be problematic because games would need to include entire state in get response)
                //TODO: spectating? (^)
                //TODO: caching errors when navigating back from game to lobby (e.g. new players that have joined will only be shown after refresh)

                call.respondPage("Lobby") {
                    head {
                        addData("youInfo", YouInfo(call.trackedUser))
                        addData("lobbyInfo", Lobby.Info(lobby))
                        addData("gameTypes", GameModule.gameTypesInfo(call))
                        addData("gameSelected", lobby.gameSelected.id())
                        addData("gameSettings", lobby.settingsInfo)
                        addData("gameSettingsValid", lobby.settingsValid)
                        addData("gameRunning", lobby.game != null)
                        addScript("${path()}/main")
                    }
                }
            }

            //TODO: separate channels that contain path parameters
            openChannel("channel", channel) { it.parameters["id"] ?: "" }

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

            fun Channel.Context.gameSettingChanged(gameSetting: GameSetting.Info) {
                val lobby = connection.lobby ?: return
                if (user !is TrackedUser || !lobby.isOperator(user) || lobby.isRunning) return
                lobby.changeGameSetting(gameSetting)
            }

            suspend fun Channel.Context.beginGame() {
                val lobby = connection.lobby ?: return
                if (user !is TrackedUser || !lobby.isOperator(user) || lobby.game != null || !lobby.settingsValid) return
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
            channel.receiver(Channel.Context::gameSettingChanged)
            channel.receiver(Channel.Context::beginGame)
            channel.rawReceiver(Channel.Context::game, 100u)
        }
    }

    private fun destroyLobby(lobby: Lobby) {
        lobbies.remove(lobby.id)
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

    class Lobby(val id: String, var gameSelected: GameType<*>) {
        private val players = mutableMapOf<TrackedUser, Player>()
        private var host: TrackedUser? = null
        private var gameSettings = gameSelected.settings()
        val settingsInfo = mutableListOf<GameSetting.Info>()
        var settingsValid = false
        var game: Game<*>? = null
        fun joined(user: TrackedUser) = players.containsKey(user)

        val activePlayers
            get() = players.filter { it.value.state.active }

        init {
            constructSettings()
        }

        fun active(user: TrackedUser) = players[user]?.state?.active == true
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
                destroyLobby(this)
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
            gameSettings = gameSelected.settings()

            settingsInfo.clear()
            constructSettings()

            //TODO: example of toAll that would go out to all lobbies instead of just the correct one
            sendGameSelected.toAll(gameType.id() to (settingsInfo to settingsValid))
        }

        private fun constructSettings() {
            //TODO: configurable settings
            // (-) dropdowns
            // (-) sliders
            // (-) presets (maybe through onchange events with callbacks)
            // (+) player-dropdowns TODO: initialization, exclusivity (maybe dropdown-groups)

            val settingsType = gameSettings::class
            for (type in settingsType.memberProperties.filter { it.visibility == KVisibility.PUBLIC }) {
                when (val setting = type.getter.call(gameSettings)) {
                    !is GameSetting ->
                        continue

                    is GameSetting.PlayerDropDown ->
                        settingsInfo.add(GameSetting.PlayerDropDown.Info(type.name, setting))

                    else ->
                        throw IllegalArgumentException("Unimplemented settings type (construction): ${type.name} (${type.returnType})")
                }
            }
            settingsValid = validateGameSettings()
        }

        fun changeGameSetting(setting: GameSetting.Info) {
            val i = settingsInfo.indexOfFirst { it.id == setting.id }
            if (i == -1) {
                logger.warn("Tried to change non-existing setting '${setting.id}' on '${gameSettings::class}'!")
                return
            }
            settingsInfo[i] = setting

            val settingsType = gameSettings::class
            val type = settingsType.memberProperties.find { it.name == setting.id }!!
            when (val gameSetting = type.getter.call(gameSettings)) {
                is GameSetting.PlayerDropDown -> {
                    val id = setting.playerDropDown.single().value
                    gameSetting.selection = if (id == "") null else players.keys.first { it.id == id }
                }

                else -> {
                    throw IllegalArgumentException("Unimplemented settings type (updating): ${type.name} (${type.returnType})")
                }
            }

            settingsValid = validateGameSettings()
            sendGameSettingChanged.toAll(setting to settingsValid)
        }

        private fun validateGameSettings(): Boolean {
            val settingsType = gameSettings::class
            for (type in settingsType.memberProperties.filter { it.visibility == KVisibility.PUBLIC }) {
                val setting = type.getter.call(gameSettings)
                if (setting is GameSetting && !setting.validate()) return false
            }
            return gameSettings.validate()
        }

        suspend fun beginGame() {
            val channel = GameChannel(sendGame)
            //TODO: loading animation
            game = gameSelected.create(channel, this, gameSettings)
            sendGameStarted.toAll("/${GameModule.path()}/${gameSelected.id()}/$id")
        }

        fun gameWon(winner: TrackedUser) {
            val player = players[winner]!!
            player.score++
            sendPlayerScoreChanged.toAll(winner.id to player.score)
        }

        fun endGame(sendFinish: Destination<String>) {
            sendGameEnded.toAll(Unit)
            sendFinish.toAll("/${path()}/$id")
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
            val id: String,
            val players: List<Player.Info>,
            val host: String
        ) {
            constructor(lobby: Lobby) : this(
                lobby.id,
                lobby.players.map { Player.Info(it.value) },
                lobby.host?.id ?: "null",
            )
        }
    }
}