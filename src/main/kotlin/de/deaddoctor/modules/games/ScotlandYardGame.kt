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

class ScotlandYardGame(channel: GameChannel, lobby: LobbyModule.Lobby) : Game<ScotlandYardGame>(channel, lobby, {

}) {

    @OptIn(ExperimentalSerializationApi::class)
    companion object : GameType<ScotlandYardGame> {
        override fun id() = "scotland-yard"
        override fun name() = "Scotland Yard"
        override fun description() = "The classic Scotland Yard game but played on a custom map of Dusseldorf. "

        override suspend fun create(channel: GameChannel, lobby: LobbyModule.Lobby) = ScotlandYardGame(channel, lobby)

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
                        sendUpdateBoundary.toAll(editorChannel.connections.filter { it != connection }, boundary)
                    }

                    suspend fun Channel.Context.changeMinZoom(minZoom: Int) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.minZoom = minZoom
                        sendUpdateMinZoom.toAll(editorChannel.connections.filter { it != connection }, minZoom)
                    }

                    suspend fun Channel.Context.changeIntersectionRadius(radius: Double) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.intersectionRadius = radius
                        sendUpdateIntersectionRadius.toAll(
                            editorChannel.connections.filter { it != connection },
                            radius
                        )
                    }

                    suspend fun Channel.Context.changeConnectionWidth(width: Double) {
                        if (user !is AccountUser || !user.admin) return closeConnection(reason)
                        val changes = changes ?: return closeConnection(reason)
                        changes.connectionWidth = width
                        sendUpdateConnectionWidth.toAll(editorChannel.connections.filter { it != connection }, width)
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

    private val mapInfo = maps.single()
    private val map = mapInfo.versions[mapInfo.version]!!
    private val roles = mutableMapOf<Role, TrackedUser?>()
    private val positions = mutableMapOf<Role, Int>()

    init {
        for (type in Role.entries) {
            positions[type] = map.intersections.random().id
        }
        roles[Role.MISTER_X] = lobby.activePlayers.keys.first()
        roles[Role.DETECTIVE1] = null
        roles[Role.DETECTIVE2] = null
        roles[Role.DETECTIVE3] = null
        roles[Role.DETECTIVE4] = null
        roles[Role.DETECTIVE5] = null
        roles[Role.DETECTIVE6] = null
    }

    override suspend fun get(call: ApplicationCall) {
        call.respondGame(ScotlandYardGame) {
            addData("youInfo", YouInfo(call.trackedUser))
            addData("lobbyInfo", lobbyInfo)
            addData("map", map)
            addData("roles", roles.mapValues { it.value?.id })
            addData("positions", positions)
        }
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
    enum class Transport {
        @SerialName("taxi")
        TAXI,

        @SerialName("bus")
        BUS,

        @SerialName("tram")
        TRAM,

        @SerialName("train")
        TRAIN
    }

    @Serializable
    data class Shape(val from: Point, val to: Point)

    @Serializable
    data class Point(val lat: Double, val lon: Double)

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
}