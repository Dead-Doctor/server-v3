package de.deaddoctor.modules.games

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import de.deaddoctor.modules.*
import de.deaddoctor.modules.LobbyModule.YouInfo
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.slf4j.LoggerFactory
import java.io.File

class ScotlandYardGame(channel: GameChannel, lobby: LobbyModule.Lobby, settings: Settings) :
    Game<ScotlandYardGame>(channel, lobby, {
        receiverTyped(ScotlandYardGame::onTakeConnection)
    }) {

    @OptIn(ExperimentalSerializationApi::class)
    companion object : GameType<ScotlandYardGame> {
        override fun id() = "scotland-yard"
        override fun name() = "Scotland Yard"
        override fun description() = "The classic Scotland Yard game but played on a custom map of Dusseldorf. "
        override fun settings() = Settings()

        override suspend fun create(channel: GameChannel, lobby: LobbyModule.Lobby, settings: GameSettings) =
            ScotlandYardGame(channel, lobby, settings as Settings)

        private val logger = LoggerFactory.getLogger(ScotlandYardGame::class.java)
        private val jsonParser = Json

        private var maps = mutableListOf<Map>()
        private val folder = File(id())
        private val mapsFolder = File(folder, "maps")

        init {
            if (!folder.isDirectory) folder.mkdir()
            if (!mapsFolder.isDirectory) mapsFolder.mkdir()
            for (mapFolder in mapsFolder.listFiles()) {
                if (!mapFolder.isDirectory) continue
                loadMap(mapFolder)
            }
            logger.info("Successfully loaded ${maps.size} maps!")
        }

        private fun loadMap(folder: File) {
            val id = folder.name
            val info = jsonParser.decodeFromStream<Map.Saved>(File(folder, "info.json").inputStream())
            val map = Map(id, info.name)
            for (file in folder.listFiles()) {
                val name = file.nameWithoutExtension
                if (name == "info") continue
                val mapData = jsonParser.decodeFromStream<MapData>(file.inputStream())
                map.versions[name.toInt()] = mapData
            }
            maps.add(map)
        }

        override fun links(call: ApplicationCall) =
            if (call.user.let { it is AccountUser && it.admin }) mutableMapOf(
                "Edit Maps" to "/game/${id()}/editor"
            ) else null

        private val currentChanges = mutableMapOf<String, MutableMapData>()
        private val editorChannel = Channel()
        private val sendUpdateBoundary = editorChannel.destination<Shape>()
        private val sendUpdateMinZoom = editorChannel.destination<Int>()
        private val sendUpdateIntersectionRadius = editorChannel.destination<Double>()
        private val sendUpdateConnectionWidth = editorChannel.destination<Double>()
        private val sendSave = editorChannel.destination<Int>()
        private val sendReset = editorChannel.destination<MutableMapData>()

        private val ApplicationCall.id: String?
            get() {
                val user = user
                val id = parameters["id"]
                return if (user is AccountUser && user.admin && id != null) id
                else null
            }
        private val Channel.Context.changes: MutableMapData?
            get() = connection.session.call.id?.let { currentChanges[it] }

        override fun Route.staticRoutes() {
            route("editor") {
                get {
                    val user = call.user
                    if (user !is AccountUser || !user.admin) return@get call.respondRedirect("/${GameModule.path()}")

                    call.respondPage("Scotland Yard Editor") {
                        head {
                            addData("maps", maps.map { Map.Info(it.id, it.name, it.version) })
                            addScript("game/${id()}/editor/load")
                        }
                    }
                }

                route("{id}") {
                    fun resetChanges(id: String): MutableMapData? {
                        val map = maps.find { it.id == id } ?: return null
                        val base = map.versions[map.version]!!
                        val changes = MutableMapData(
                            base.boundary,
                            base.minZoom,
                            base.intersectionRadius,
                            base.connectionWidth,
                            base.intersections.toMutableList(),
                            base.connections.toMutableList()
                        )
                        currentChanges[id] = changes
                        return changes
                    }

                    get {
                        val id = call.id ?: return@get call.respondRedirect("/${GameModule.path()}")
                        val map = maps.find { it.id == id } ?: return@get call.respondRedirect("/${GameModule.path()}")
                        val changes = currentChanges[id] ?: resetChanges(id)

                        call.respondPage("Scotland Yard Editor") {
                            head {
                                addData("map", Map.Info(map.id, map.name, map.version))
                                addData("changes", changes)
                                addScript("game/${id()}/editor/main")
                            }
                        }
                    }
                    openChannel("channel", editorChannel)

                    val reason = CloseReason(CloseReason.Codes.NORMAL, "Not allowed.")
                    suspend fun Channel.Context.changeBoundary(boundary: Shape) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.boundary = boundary
                        sendUpdateBoundary.toAllExcept(connection, boundary)
                    }

                    suspend fun Channel.Context.changeMinZoom(minZoom: Int) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.minZoom = minZoom
                        sendUpdateMinZoom.toAllExcept(connection, minZoom)
                    }

                    suspend fun Channel.Context.changeIntersectionRadius(radius: Double) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.intersectionRadius = radius
                        sendUpdateIntersectionRadius.toAllExcept(connection, radius)
                    }

                    suspend fun Channel.Context.changeConnectionWidth(width: Double) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.connectionWidth = width
                        sendUpdateConnectionWidth.toAllExcept(connection, width)
                    }

                    suspend fun Channel.Context.changeIntersections(intersection: Intersection) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.intersections.removeIf { it.id == intersection.id }
                        changes.intersections.add(intersection)
                        //TODO: send updates
                    }

                    suspend fun Channel.Context.changeConnections(connection: Connection) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.connections.removeIf { it.id == connection.id }
                        changes.connections.add(connection)
                        //TODO: send updates
                    }

                    suspend fun Channel.Context.save() {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        //TODO: save current to new version
                        val id = connection.session.call.id ?: return closeConnection(reason)
                        val map = maps.find { it.id == id } ?: return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)

                        val nextVersion = map.version + 1
                        val mapData = MapData(
                            changes.boundary,
                            changes.minZoom,
                            changes.intersectionRadius,
                            changes.connectionWidth,
                            changes.intersections,
                            changes.connections
                        )
                        map.versions[nextVersion] = mapData
                        val folder = File(mapsFolder, id)
                        val version = File(folder, "$nextVersion.json")
                        jsonParser.encodeToStream(mapData, version.outputStream())

                        logger.info("Saved version $nextVersion of map '${map.name}' to $version.")
                        sendSave.toAll(nextVersion)
                    }

                    suspend fun Channel.Context.reset() {
                        val id = connection.session.call.id ?: return closeConnection(reason)
                        resetChanges(id)
                        //TODO: send updates
                        val changes = changes!!
                        sendReset.toAll(changes)
                    }
                    editorChannel.receiver(Channel.Context::changeBoundary)
                    editorChannel.receiver(Channel.Context::changeMinZoom)
                    editorChannel.receiver(Channel.Context::changeIntersectionRadius)
                    editorChannel.receiver(Channel.Context::changeConnectionWidth)
                    editorChannel.receiver(Channel.Context::changeIntersections)
                    editorChannel.receiver(Channel.Context::changeConnections)
                    editorChannel.receiver(Channel.Context::save)
                    editorChannel.receiver(Channel.Context::reset)
                }
            }
        }

        @Serializable
        data class MutableMapData(
            var boundary: Shape,
            var minZoom: Int,
            var intersectionRadius: Double,
            var connectionWidth: Double,
            val intersections: MutableList<Intersection>,
            val connections: MutableList<Connection>
        )
    }

    class Settings : GameSettings() {
        val misterX = GameSetting.PlayerDropDown("Mister X")
        val detective1 = GameSetting.PlayerDropDown("Detective 1")
        val detective2 = GameSetting.PlayerDropDown("Detective 2", true)
        val detective3 = GameSetting.PlayerDropDown("Detective 3", true)
        val detective4 = GameSetting.PlayerDropDown("Detective 4", true)
        val detective5 = GameSetting.PlayerDropDown("Detective 5", true)
        val detective6 = GameSetting.PlayerDropDown("Detective 6", true)
    }

    data class Map(
        val id: String,
        val name: String,
        val versions: MutableMap<Int, MapData> = mutableMapOf()
    ) {
        val version: Int
            get() = versions.keys.max()

        @Serializable
        data class Saved(val name: String)

        @Serializable
        data class Info(val id: String, val name: String, val version: Int)
    }

    @Serializable
    data class MapData(
        val boundary: Shape,
        val minZoom: Int,
        val intersectionRadius: Double,
        val connectionWidth: Double,
        val intersections: List<Intersection>,
        val connections: List<Connection>
    )

    @Serializable
    data class Intersection(val id: Int, @SerialName("pos") val position: Point)

    @Serializable
    data class Connection(val id: Int, val from: Int, val to: Int, val type: Transport, val shape: Shape)

    @Serializable
    data class Shape(val from: Point, val to: Point)

    @Serializable
    data class Point(val lat: Double, val lon: Double)

    //@formatter:off
    @Serializable
    enum class Transport {
        @SerialName("taxi") TAXI,
        @SerialName("bus") BUS,
        @SerialName("tram") TRAM,
        @SerialName("train") TRAIN
    }

    @Serializable
    enum class Role {
        @SerialName("misterX") MISTER_X,
        @SerialName("detective1") DETECTIVE1,
        @SerialName("detective2") DETECTIVE2,
        @SerialName("detective3") DETECTIVE3,
        @SerialName("detective4") DETECTIVE4,
        @SerialName("detective5") DETECTIVE5,
        @SerialName("detective6") DETECTIVE6
    }
    //@formatter:on

    @Serializable
    enum class Ticket {
        TAXI,
        BUS,
        TRAM,
        MULTI
    }

    private val sendNextRound = channel.destination<Int>()
    private val sendNextTurn = channel.destination<Role>()
    private val sendAvailableConnections = channel.destination<List<Int>>()
    private val sendMove = channel.destination<Pair<Role, Int>>()

    private val mapInfo = maps.single()
    private val map = mapInfo.versions[mapInfo.version]!!
    private val roles = mutableMapOf<Role, TrackedUser?>()
    private val detectives = mutableListOf<TrackedUser>()

    private val positions = mutableMapOf<Role, Int>()
    private var lastKnownMisterX = -1
    private var round = 0

    private var turn = Role.MISTER_X

    /**
     * List of available moves for the current turn.
     * Pairs of connections to intersections.
     */
    private var availableMoves: List<Pair<Int, Int>>

    init {
        val pool = map.intersections.toMutableList()
        for (type in Role.entries) {
            val position = pool.random()

            val neighbours = mutableListOf(position.id to 3)
            while (neighbours.isNotEmpty()) {
                val (current, depth) = neighbours.removeFirst()
                if (!pool.removeIf { it.id == current }) continue
                if (depth == 0) continue
                neighbours.addAll(findConnections(current).map { it.second to depth - 1 })
            }

            positions[type] = position.id
        }

        availableMoves = findAvailableMoves()

        roles[Role.MISTER_X] = settings.misterX.value
        roles[Role.DETECTIVE1] = settings.detective1.value
        roles[Role.DETECTIVE2] = settings.detective2.value
        roles[Role.DETECTIVE3] = settings.detective3.value
        roles[Role.DETECTIVE4] = settings.detective4.value
        roles[Role.DETECTIVE5] = settings.detective5.value
        roles[Role.DETECTIVE6] = settings.detective6.value
        for (role in Role.entries) {
            if (role == Role.MISTER_X) continue
            val user = roles[role]
            if (user != null) detectives.add(user)
        }
    }

    private fun findConnections(intersection: Int) = map.connections.mapNotNull {
        if (it.from == intersection) it.id to it.to
        else if (it.to == intersection) it.id to it.from
        else null
    }

    override suspend fun get(call: ApplicationCall) {
        call.respondGame(ScotlandYardGame) {
            addData("youInfo", YouInfo(call.trackedUser))
            addData("lobbyInfo", lobbyInfo)
            addData("map", map)
            addData("roles", roles.mapValues { it.value?.id })
            val redacted = positions.toMutableMap()
            if (call.user != roles[Role.MISTER_X])
                redacted[Role.MISTER_X] = lastKnownMisterX
            addData("positions", redacted)
            addData("round", round)
            addData("turn", turn)
            val availableConnections =
                if (isTheirTurn(call.trackedUser)) availableMoves.map { it.first }
                else null
            addData("availableConnections", availableConnections)
        }
    }

    private fun isTheirTurn(user: TrackedUser): Boolean {
        val currentUser = roles[turn]
        return currentUser == user || (currentUser == null && user in detectives)
    }

    private fun onTakeConnection(ctx: Channel.Context, data: Pair<Ticket, Int>) {
        val ticket: Ticket = data.first
        val id: Int = data.second

        if (ctx.user !is TrackedUser || !isTheirTurn(ctx.user)) return

        val connection = map.connections.find { it.id == id } ?: return
        val move = availableMoves.find { it.first == id } ?: return
        val next = move.second

        if (!isValidTicketFor(connection.type, ticket)) return

        positions[turn] = next
        updatePosition(turn)
        println("$turn took connection $id to intersection $next with $ticket ticket.")

        nextTurn()
    }

    private fun isValidTicketFor(type: Transport, ticket: Ticket) =
        ticket == Ticket.MULTI
                || ticket == Ticket.TAXI && type == Transport.TAXI
                || ticket == Ticket.BUS && type == Transport.BUS
                || ticket == Ticket.TRAM && type == Transport.TRAM

    private val revealMisterX
        get() = round % 3 == 2

    private fun updatePosition(role: Role) {
        val position = positions[turn]!!

        if (role != Role.MISTER_X)
            return sendMove.toAll(turn to position)

        if (revealMisterX)
            lastKnownMisterX = position

        val misterXUser = roles[Role.MISTER_X]!!
        sendMove.toUser(misterXUser, role to position)
        sendMove.toAllExceptUser(misterXUser, role to lastKnownMisterX)
    }

    private fun nextTurn() {
        var nextRole = turn.ordinal + 1
        if (nextRole == Role.entries.count()) {
            round++
            nextRole = 0
            sendNextRound.toAll(round)
        }
        turn = Role.entries[nextRole]
        availableMoves = findAvailableMoves()

        //TODO: might be in incorrect order
        sendNextTurn.toAll(turn)
        val user = roles[turn]
        if (user != null) sendAvailableConnections.toUser(user, availableMoves.map { it.first })
        else sendAvailableConnections.toAllExceptUser(roles[Role.MISTER_X]!!, availableMoves.map { it.first })
    }

    private fun findAvailableMoves(): List<Pair<Int, Int>> {
        val position = positions[turn]!!
        val neighbours = findConnections(position)
        val detectivePositions = positions.mapNotNull { if (it.key != Role.MISTER_X) it.value else null }
        val unoccupied = neighbours.filterNot { it.second in detectivePositions }
        if (unoccupied.isEmpty()) TODO("No available move.")
        return unoccupied
    }
}