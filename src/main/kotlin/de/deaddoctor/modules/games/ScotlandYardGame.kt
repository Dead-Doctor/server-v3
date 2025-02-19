package de.deaddoctor.modules.games

import de.deaddoctor.modules.Game
import de.deaddoctor.modules.GameChannel
import de.deaddoctor.modules.GameType
import de.deaddoctor.modules.LobbyModule
import io.ktor.server.application.*
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

        private var maps = mutableMapOf<Int, Map>()
        private val folder = File(id())
        private val mapsFolder = File(folder, "maps")

        init {
            if (!folder.isDirectory) folder.mkdir()
            if (!mapsFolder.isDirectory) mapsFolder.mkdir()
            for (file in mapsFolder.listFiles()) {
                val version = file.nameWithoutExtension.toInt()
                val oldMap = jsonParser.decodeFromStream<OldMap>(file.inputStream())
                val map = Map(
                    oldMap.intersections.map { Intersection(it.id, Point(it.lat, it.lng)) },
                    oldMap.connections.map {
                        Connection(
                            it.id,
                            it.from,
                            it.to,
                            it.type,
                            Connection.Shape(Point(it.controls[0][0], it.controls[0][1]), Point(it.controls[1][0], it.controls[1][1]))
                        )
                    })
                jsonParser.encodeToStream(map, file.outputStream())
                maps[version] = map
            }
            logger.info("Successfully loaded ${maps.size} map versions!")
        }
    }

    override suspend fun get(call: ApplicationCall) {
        TODO("Not yet implemented")
    }

    @Serializable
    data class OldMap(val intersections: List<OldIntersection>, val connections: List<OldConnection>)

    @Serializable
    data class OldIntersection(val id: Int, val lat: Double, val lng: Double)

    @Serializable
    data class OldConnection(
        val id: Int,
        val from: Int,
        val to: Int,
        val type: Transport,
        val controls: List<List<Double>>
    )

    @Serializable
    data class Map(val intersections: List<Intersection>, val connections: List<Connection>)

    @Serializable
    data class Intersection(val id: Int, @SerialName("pos") val position: Point)

    @Serializable
    data class Connection(val id: Int, val from: Int, val to: Int, val type: Transport, val shape: Shape) {
        @Serializable
        data class Shape(val from: Point, val to: Point)
    }

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
    data class Point(val lat: Double, val lon: Double)
}