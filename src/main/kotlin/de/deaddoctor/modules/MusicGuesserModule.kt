package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalSerializationApi::class)
object MusicGuesserModule : Module {
    const val NAME = "Music Guesser"
    private val NAME_ID = NAME.lowercase().replace(' ', '-')

    private val cacheDuration = 30L.days
    private val storeFronts = listOf("us", "gb", "de")

    @Suppress("SpellCheckingInspection")
    private val centuries = listOf(
        "pl.4c4185db922342f1bc36e0817eec213a",
        "pl.405e6f67264a4a44ba1b0c3a787c78b8",
        "pl.1745c21b5f084936ad637b4cd5cbd99a",
        "pl.af4d982795c6472ea48579eb147cd726",
        "pl.0d70b7c9be8e4e0b95ebbf5578aaf7a2",
        "pl.e50ccee7318043eaaf8e8e28a2a55114",
        "pl.6b1b5dfda067443481265436811002f1"
    )
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(javaClass)

    private var ready = false
    private val tracksCache = File("tracks.json")
    private lateinit var tracks: MutableList<Track>

    init {
        thread(isDaemon = true, name = "fetch-playlists") {
            runBlocking {
                if (!tracksCache.isFile || (System.currentTimeMillis() - tracksCache.lastModified()).milliseconds > cacheDuration) {
                    tracks = mutableListOf()
                    for (storeFront in storeFronts) {
                        for (century in centuries) {
                            val url = "https://music.apple.com/$storeFront/playlist/xyz/$century"
                            val response = httpClient.get(url)
                            val html = response.bodyAsText()
                            val json =
                                html.substringAfter("<script id=schema:music-playlist type=\"application/ld+json\">")
                                    .substringBefore("</script>").trim()
                            val playlist = jsonParser.decodeFromString<AppleMusicPlaylist>(json)
                            for (track in playlist.tracks) {
                                val id = track.url.substringAfterLast("/").toLong()
                                tracks.add(Track(storeFront, id))
                            }
                        }
                    }
                    ready = true
                    logger.info("Successfully loaded ${tracks.size} tracks!")
                    jsonParser.encodeToStream(tracks, tracksCache.outputStream())
                } else {
                    tracks = jsonParser.decodeFromStream(tracksCache.inputStream())
                    ready = true
                    logger.info("Successfully read ${tracks.size} cached tracks!")
                }
            }
        }
    }

    override fun path() = NAME_ID

    private fun relative(subUrl: String) = "/$NAME_ID$subUrl"

    private lateinit var socket: WebSocketSender
    private var currentGame: Game? = null

    override fun Route.route() {
        install(CachingHeaders) {
            options { _, _ -> CachingOptions(CacheControl.NoStore(null)) }
        }
        get {
            call.respondPage(NAME) {
                content {
                    section {
                        h1 { +NAME }
                    }
                    section(classes = "grid") {
                        if (currentGame == null) {
                            a(href = relative("/start")) { +"Start" }
                        } else {
                            a(href = relative("/game")) { +"Join" }
                        }
                    }
                }
            }
        }

        get("start") {
            if (currentGame == null) {
                currentGame = Game()
            }
            call.respondRedirect(relative("/game"))
        }

        get("game") {
            val game = currentGame
            if (game == null) {
                call.respondRedirect(relative("/start"))
                return@get
            }
            val user = call.trackedUser
            if (!game.joined(user) && (user is AccountUser || game.playerById(user.id) != null)) {
                game.join(user)
            }
            call.respondPage(NAME) {
                head {
                    addData("playerInfo", game.playerInfo)
                    addData("gameInfo", game.gameInfo(user))
                    addData("round", game.roundInfo)
                    addScript("$NAME_ID/main")
                }
            }
        }

        socket = webSocketAddressable("ws") {
            connection {
                val game = currentGame
                if (game == null || user !is TrackedUser) {
                    closeConnection(
                        connection,
                        CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Illegal state encountered.")
                    )
                    return@connection
                }
                if (!game.joined(user)) return@connection
                game.socketConnect(user)
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
                    nameErrors.add("Can only contain <samp>letters</samp>, <samp>digits</samp> or any of the following: ${
                        allowedSpecialCharacters.toCharArray().joinToString(", ") { "<samp>$it</samp>" }
                    }."
                    )
                }
                return nameErrors
            }
            destination("checkName") { name: String ->
                val nameErrors = checkName(name)
                sendToUser(user, Packet("checkedName", nameErrors))
            }
            destination("join") { name: String ->
                val game = currentGame ?: return@destination
                if (user !is TrackedUser || user is AccountUser || game.joined(user) || checkName(name).isNotEmpty()) return@destination
                game.join(user, name)
                game.socketConnect(user)
                sendToUser(user, Packet("join", user.id))
            }
            disconnection {
                val game = currentGame ?: return@disconnection
                if (user !is TrackedUser || !game.joined(user) || countConnections(user) != 0) return@disconnection
                game.socketDisconnect(user)
            }
            destination("promote") { playerId: String ->
                val game = currentGame ?: return@destination
                val player = game.playerById(playerId)
                if (user !is TrackedUser || !game.isOperator(user) || player == null || !game.joined(player)) return@destination
                game.promote(player)
            }
            destination("kick") { playerId: String ->
                val game = currentGame ?: return@destination
                val player = game.playerById(playerId)
                if (user !is TrackedUser || !game.isOperator(user) || player == null || !game.joined(player)) return@destination
                game.kick(player)
            }
            destination("beginRound") {
                val game = currentGame ?: return@destination
                if (user !is TrackedUser || !game.isOperator(user) || game.round != null) return@destination
                game.beginRound()
            }
            destination("guess") { year: Int? ->
                val game = currentGame ?: return@destination
                if (user !is TrackedUser || game.round?.players?.contains(user) != true) return@destination
                game.guess(user, year)
            }
            destination("finish") {
                val game = currentGame ?: return@destination
                if (user !is TrackedUser || !game.isOperator(user) || game.round == null) return@destination
                game.endRound()
            }
        }

        get("test")
        {
            if (!ready) throw Exception("Not ready yet")
            val track = tracks[Random.nextInt(0, tracks.size)]
            val song = queryTrack(track)
            call.respondPage(NAME) {
                content {
                    section {
                        h1 { +NAME }
                        h3 { +"Try to guess the release year!" }
                    }
                    section {
                        p { +"${song.trackName} - ${song.duration}" }
                        p { +"${song.artistName} - ${song.collectionName}" }
                        p {
                            +"Reveal Year: "
                            button {
                                onClick = "innerText = '${song.releaseDate.year}'"
                                +"Click"
                            }
                        }
                    }
                    section {
                        audio {
                            src = song.previewUrl
                            controls = true
                        }
                    }
                }
            }
        }
    }

    private suspend fun queryTrack(track: Track): Song {
        val url =
            "https://itunes.apple.com/search?term=${track.id}&limit=1&country=${track.storeFront}&media=music&entity=musicTrack&explicit=yes"
        val response = httpClient.get(url)
        val json = response.bodyAsText()
        val search = jsonParser.decodeFromString<Search>(json)
        return search.results.single()
    }

    @Serializable
    data class Search(val resultCount: Int, val results: List<Song>)

    @Serializable
    data class Song(
        val artistId: Long,
        val artistName: String,
        val artistViewUrl: String,

        val collectionId: Long,
        val collectionName: String,
        val collectionCensoredName: String,
        val collectionViewUrl: String,

        val trackId: Long,
        val trackName: String,
        val trackCensoredName: String,
        val trackViewUrl: String,

        val previewUrl: String,
        val artworkUrl30: String,
        val artworkUrl60: String,
        val artworkUrl100: String,

        @SerialName("releaseDate")
        val releaseDateTime: Instant,
        val discCount: Int,
        val discNumber: Int,
        val trackCount: Int,
        val trackNumber: Int,
        val trackTimeMillis: Int,
        val country: String,
        val currency: String,
        val primaryGenreName: String,
        val isStreamable: Boolean
    ) {
        @Transient
        val duration: Duration = trackTimeMillis.milliseconds

        @Transient
        val releaseDate: LocalDateTime = releaseDateTime.toLocalDateTime(TimeZone.UTC)

        @Serializable
        data class Info(
            val previewUrl: String,
            val trackName: String?,
            val artistName: String?,
            val artworkUrl: String?,
            val releaseYear: Int?
        )
    }

    @Serializable(with = TrackSerializer::class)
    data class Track(val storeFront: String, val id: Long)

    object TrackSerializer : KSerializer<Track> {
        override val descriptor = PrimitiveSerialDescriptor("Track", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Track) {
            encoder.encodeString("${value.storeFront}:${value.id}")
        }

        override fun deserialize(decoder: Decoder): Track {
            val split = decoder.decodeString().split(":")
            return Track(split[0], split[1].toLong())
        }
    }

    @Serializable
    data class AppleMusicPlaylist(
        @SerialName("numTracks")
        val countTracks: Int,
        @SerialName("track")
        val tracks: Array<AppleMusicRecording>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AppleMusicPlaylist) return false
            if (!tracks.contentEquals(other.tracks)) return false
            return true
        }

        override fun hashCode(): Int {
            return tracks.contentHashCode()
        }
    }

    @Serializable
    data class AppleMusicRecording(val url: String)

    class Game {
        private val players = mutableMapOf<TrackedUser, PlayerState>()
        private val names = mutableMapOf<TrackedUser, String>()
        private var host: TrackedUser? = null
        private val disconnectJobs = mutableMapOf<TrackedUser, Job>()
        private val scores = mutableMapOf<TrackedUser, Int>()
        var round: Round? = null
            private set

        fun playerById(userId: String) = players.keys.find { it.id == userId }

        fun joined(user: TrackedUser) = players.containsKey(user) && players[user]!!.playing

        fun join(user: TrackedUser, name: String? = null) {
            val initialJoin = !players.containsKey(user)

            players[user] = PlayerState.JOINED

            if (initialJoin) {
                if (user !is AccountUser) names[user] = name!!
                sendToAll(Packet("playerJoined", PlayerInfo(user, getName(user), players[user]!!.playing, 0)))
            } else {
                sendToAll(Packet("playerStateChanged", PlayerStateChanged(user.id, players[user]!!.playing)))
            }

            if (host == null) promote(user)
        }

        private fun leave(user: TrackedUser) {
            players[user] = PlayerState.LEFT

            if (round?.players?.remove(user) == true) {
                if (round!!.players.isEmpty()) {
                    round = null
                    sendToAll(Packet("round", roundInfo))
                } else {
                    maybeShowResults()
                }
            }

            disconnectJobs.remove(user)?.cancel()
            sendToAll(Packet("playerStateChanged", PlayerStateChanged(user.id, players[user]!!.playing)))
            val firstRemainingPlayer = players.toList().firstOrNull { it.second.playing }?.first
            if (firstRemainingPlayer == null) {
                currentGame = null
                return
            }
            if (user == host) promote(firstRemainingPlayer)
        }

        fun socketConnect(user: TrackedUser) {
            players[user] = PlayerState.CONNECTED
            disconnectJobs.remove(user)?.cancel()
        }

        fun socketDisconnect(user: TrackedUser) {
            if (players[user] != PlayerState.CONNECTED) return
            players[user] = PlayerState.JOINED
            disconnectJobs[user] = CoroutineScope(Job()).launch {
                delay(5.seconds)
                leave(user)
            }
        }

        fun isOperator(user: TrackedUser) = user == host || (user is AccountUser && user.admin)

        fun promote(user: TrackedUser) {
            host = user
            sendToAll(Packet("hostChanged", host?.id))
        }

        fun kick(user: TrackedUser) {
            leave(user)
            sendToUser(user, Packet("kicked", "You got kicked"))
        }

        //TODO: multiple round per round?
        suspend fun beginRound() {
            round = Round(
                players.filter { it.value.playing }.map { it.key }.toMutableList(),
                Question(0, queryTrack(tracks[Random.nextInt(0, tracks.size)]))
            )
            sendToAll(Packet("round", roundInfo))
            //TODO: guess timeout
        }

        fun guess(user: TrackedUser, year: Int?) {
            if (year != null) {
                round!!.question.guesses[user] = evaluateGuess(round!!.question.song, year)
            } else {
                round!!.question.guesses.remove(user)
            }
            maybeShowResults()
        }

        private val minimumYear = 1950
        private val maximumYear = 2020
        private val fallOfFactor = 0.025f
        private fun evaluateGuess(song: Song, year: Int): Guess {
            val difference = abs(year - song.releaseDate.year).toFloat()
            val points =
                (100f * (1f - difference / (maximumYear - minimumYear)) / (1f + difference * fallOfFactor)).roundToInt()
            return Guess(year, points)
        }

        private fun maybeShowResults() {
            val question = round!!.question
            if (!question.showResult && question.guesses.size >= round!!.players.size) {
                question.showResult = true
                val winner = question.guesses.maxBy { it.value.points }.key
                val newScore = scores.getOrDefault(winner, 0) + 1
                scores[winner] = newScore
                sendToAll(Packet("round", roundInfo))
                sendToAll(Packet("playerScoreChanged", PlayerScoreChanged(winner.id, newScore)))
            }
        }

        fun endRound() {
            round = null
            sendToAll(Packet("round", roundInfo))
        }

        val playerInfo: List<PlayerInfo>
            get() {
                return players.map { PlayerInfo(it.key, getName(it.key), it.value.playing, scores[it.key] ?: 0) }
            }

        private fun getName(user: TrackedUser) = if (user is AccountUser) user.name else names[user]!!

        fun gameInfo(user: TrackedUser) = GameInfo(playerById(user.id)?.id, host?.id, user is AccountUser && user.admin)

        val roundInfo
            get() = round?.let { round ->
                val question = round.question
                val song = question.song
                Round.Info(
                    round.players.map { p -> p.id },
                    Question.Info(
                        question.i,
                        Song.Info(
                            song.previewUrl,
                            question.reveal(song.trackName),
                            question.reveal(song.artistName),
                            question.reveal(song.artworkUrl100),
                            question.reveal(song.releaseDate.year),
                        ),
                        question.showResult,
                        question.reveal(question.guesses.map { (key, value) -> key.id to value }.toMap())
                    )
                )
            }

        enum class PlayerState(val playing: Boolean) {
            LEFT(false),
            JOINED(true),
            CONNECTED(true)
        }

        @Serializable
        data class PlayerInfo(
            val id: String,
            val name: String,
            val verified: Boolean,
            val avatar: String?,
            val playing: Boolean,
            val score: Int
        ) {
            constructor(user: TrackedUser, name: String, playing: Boolean, score: Int) : this(
                user.id,
                name,
                user is AccountUser,
                (user as? AccountUser)?.avatar,
                playing,
                score
            )
        }

        @Serializable
        data class PlayerStateChanged(val player: String, val playing: Boolean)

        @Serializable
        data class PlayerScoreChanged(val player: String, val score: Int)

        @Serializable
        data class GameInfo(
            val you: String?,
            val host: String?,
            val admin: Boolean
        )

        data class Round(
            val players: MutableList<TrackedUser>,
            val question: Question
        ) {

            @Serializable
            data class Info(
                val players: List<String>,
                val question: Question.Info
            )
        }

        data class Question(
            val i: Int,
            val song: Song,
            var showResult: Boolean = false,
            var guesses: MutableMap<TrackedUser, Guess> = mutableMapOf()
        ) {

            fun <T> reveal(value: T): T? {
                return if (showResult) value else null
            }

            @Serializable
            data class Info(
                val i: Int,
                val song: Song.Info,
                val showResults: Boolean,
                val guesses: Map<String, Guess>?
            )
        }
    }

    @Serializable
    data class Guess(val year: Int, val points: Int)

    @Serializable
    data class Packet<T>(val type: String, val data: T)

    private inline fun <reified T> sendToAll(packet: Packet<T>) {
        socket.sendToAll(packet)
    }

    private inline fun <reified T> sendToUser(user: TrackedUser, packet: Packet<T>) {
        socket.sendToUser(user, packet)
    }
}