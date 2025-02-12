package de.deaddoctor.modules.games

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import de.deaddoctor.modules.*
import de.deaddoctor.modules.LobbyModule.Lobby
import de.deaddoctor.modules.LobbyModule.YouInfo
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class MusicGuesserGame(
    channel: GameChannel,
    val lobby: Lobby
) : Game<MusicGuesserGame>({
    receiverTyped(MusicGuesserGame::onGuess)
    receiverTyped(MusicGuesserGame::onOverride)
    receiverTyped(MusicGuesserGame::onNext)
    receiverTyped(MusicGuesserGame::onFinish)
}) {

    @OptIn(ExperimentalSerializationApi::class)
    companion object : GameType<MusicGuesserGame> {
        override fun id() = "music-guesser"
        override fun name() = "Music Guesser"
        override fun create(channel: GameChannel, lobby: Lobby) = MusicGuesserGame(channel, lobby)

        private val logger = LoggerFactory.getLogger(MusicGuesserGame::class.java)
        private val jsonParser = Json { ignoreUnknownKeys = true }

        private val storeFronts = listOf("us", "gb", "de")
        @Suppress("SpellCheckingInspection")
        private val centuryPlaylistIds = listOf(
            "pl.4c4185db922342f1bc36e0817eec213a",
            "pl.405e6f67264a4a44ba1b0c3a787c78b8",
            "pl.1745c21b5f084936ad637b4cd5cbd99a",
            "pl.af4d982795c6472ea48579eb147cd726",
            "pl.0d70b7c9be8e4e0b95ebbf5578aaf7a2",
            "pl.e50ccee7318043eaaf8e8e28a2a55114",
            "pl.6b1b5dfda067443481265436811002f1"
        )
        private val cacheDuration = 30L.days

        private var ready = false
        private val tracksCache = File("${id()}/tracks.json")
        private lateinit var tracks: MutableList<Track>
        private val overridesFile = File("${id()}/overrides.json")
        private lateinit var overrides: MutableMap<Long, Int>

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
            val count: Int,
            @SerialName("track")
            val tracks: Array<AppleMusicRecording>
        ) {
            companion object {
                suspend fun fetch(storeFront: String, id: String): AppleMusicPlaylist {
                    val url = "https://music.apple.com/$storeFront/playlist/xyz/$id"
                    val response = httpClient.get(url)
                    val html = response.bodyAsText()
                    val json =
                        html.substringAfter("<script id=schema:music-playlist type=\"application/ld+json\">")
                            .substringBefore("</script>").trim()
                    return jsonParser.decodeFromString<AppleMusicPlaylist>(json)
                }
            }

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
        data class AppleMusicRecording(val url: String) {
            val id = url.substringAfterLast("/").toLong()
        }

        init {
            thread(isDaemon = true, name = "fetch-playlists-and-overrides") {
                runBlocking {
                    overrides = if (overridesFile.isFile) jsonParser.decodeFromStream(overridesFile.inputStream())
                    else mutableMapOf()

                    if (!tracksCache.isFile || (System.currentTimeMillis() - tracksCache.lastModified()).milliseconds > cacheDuration) {
                        tracks = mutableListOf()
                        for (storeFront in storeFronts) {
                            for (centuryId in centuryPlaylistIds) {
                                val playlist = AppleMusicPlaylist.fetch(storeFront, centuryId)
                                for (track in playlist.tracks) {
                                    tracks.add(Track(storeFront, track.id))
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
            server.monitor.subscribe(ApplicationStopped) {
                jsonParser.encodeToStream(overrides, overridesFile.outputStream())
                logger.info("Saved overrides!")
            }
        }
    }

    private val sendRound = channel.destination<RoundInfo>()

    private val currentPlayers: MutableList<TrackedUser>
    private val questions = mutableListOf<Question>()
    private var showResults: Boolean = false
    private val results: MutableMap<TrackedUser, Int>

    init {
        val players = lobby.activePlayers.map { it.key }
        currentPlayers = players.toMutableList()
        results = players.associateWith { 0 }.toMutableMap()

        CoroutineScope(Job()).launch {
            questions.add(fetchRandomQuestion())
            sendRound.toAll(roundInfo)
            //TODO: guess timeout
        }
    }

    override suspend fun get(call: ApplicationCall) {
        call.respondPage(name()) {
            head {
                addData("youInfo", YouInfo(call.trackedUser))
                addData("lobbyInfo", Lobby.Info(lobby))
                addData("round", roundInfo)
                addScript("game/${id()}/main")
            }
        }
    }

    fun onGuess(ctx: Channel.Context, year: Int?) {
        if (ctx.user !is TrackedUser || !currentPlayers.contains(ctx.user)) return
        guess(ctx.user, year)
    }

    fun onOverride(ctx: Channel.Context, year: Int) {
        if (ctx.user !is TrackedUser || !lobby.isOperator(ctx.user)) return
        override(year, ctx.user is AccountUser && ctx.user.admin)
    }

    suspend fun onNext(ctx: Channel.Context) {
        if (ctx.user !is TrackedUser || !lobby.isOperator(ctx.user)) return
        next()
    }

    fun onFinish(ctx: Channel.Context) {
        if (ctx.user !is TrackedUser || !lobby.isOperator(ctx.user)) return
        endRound()
    }

    private fun guess(user: TrackedUser, year: Int?) {
        if (year != null) {
            question.guesses[user] = evaluateGuess(getYear(question.song), year)
        } else {
            question.guesses.remove(user)
        }
        maybeShowResults()
    }

    private val minimumYear = 1950
    private val maximumYear = 2020
    private val fallOfFactor = 0.05f
    private fun evaluateGuess(correct: Int, year: Int): Guess {
        val difference = abs(year - correct).toFloat()
        val points =
            (100f * (1f - difference / (maximumYear - minimumYear)) / (1f + difference * fallOfFactor)).roundToInt()
        return Guess(year, points)
    }

    private fun maybeShowResults() {
        if (!question.showResult && question.guesses.size >= currentPlayers.size) {
            question.showResult = true
            sendRound.toAll(roundInfo)
        }
    }

    fun override(year: Int, save: Boolean) {
        if (save) overrides[question.song.trackId] = year
        question.guesses.replaceAll { _, guess -> evaluateGuess(year, guess.year) }
        sendRound.toAll(roundInfo)
    }

    private val questionsPerRound = 5
    private suspend fun next() {
        question.guesses.forEach { (user, guess) ->
            results[user] = results[user]!! + guess.points
        }

        if (questions.size < questionsPerRound) {
            questions.add(fetchRandomQuestion())
            sendRound.toAll(roundInfo)
        } else {
            showResults = true
            results.replaceAll { _, total -> (total.toFloat() / questionsPerRound).roundToInt() }
            sendRound.toAll(roundInfo)

            //TODO: implement lobby scoring and game ending
//            val winner = results.maxBy { it.value }.key
//            val newScore = scores.getOrDefault(winner, 0) + 1
//            scores[winner] = newScore
//            sendToAll(Packet("playerScoreChanged", PlayerScoreChanged(winner.id, newScore)))
        }
    }

    private fun endRound() {
        //TODO: implement game ending
    }

    private val question
        get() = questions.last()

    private val roundInfo
        get() = RoundInfo(
            currentPlayers.map { p -> p.id },
            questions.map { question ->
                val song = question.song
                Question.Info(
                    Song.Info(
                        song.previewUrl,
                        question.reveal(song.trackName),
                        question.reveal(song.artistName),
                        question.reveal(song.artworkUrl100),
                        question.reveal(getYear(song)),
                    ),
                    question.showResult,
                    question.reveal(question.guesses.map { (key, value) -> key.id to value }.toMap())
                )
            }, if (showResults) results.map { (key, value) -> key.id to value }.toMap() else null
        )

    private fun getYear(song: Song) = overrides[song.trackId] ?: song.releaseDate.year

    private suspend fun fetchRandomQuestion(): Question {
        if (tracks.size == 0) throw IllegalStateException("No tracks!")
        var song: Song?
        do {
            val i = Random.nextInt(0, tracks.size)
            song = fetchSong(tracks[i])
        } while (song == null)
        return Question(song)
    }

    private suspend fun fetchSong(track: Track): Song? {
        val url =
            "https://itunes.apple.com/lookup?id=${track.id}&limit=1&country=${track.storeFront}&media=music&entity=musicTrack&explicit=yes"
        val response = httpClient.get(url)
        val json = response.bodyAsText()
        val search = jsonParser.decodeFromString<Search>(json)
        if (search.resultCount == 0) {
            logger.error("No results for track: $track")
            return null
        }
        return search.results.single()
    }

    @Serializable
    data class RoundInfo(
        val players: List<String>,
        val questions: List<Question.Info>,
        val results: Map<String, Int>?
    )

    data class Question(
        val song: Song,
        var showResult: Boolean = false,
        var guesses: MutableMap<TrackedUser, Guess> = mutableMapOf()
    ) {

        fun <T> reveal(value: T): T? {
            return if (showResult) value else null
        }

        @Serializable
        data class Info(
            val song: Song.Info,
            val showResult: Boolean,
            val guesses: Map<String, Guess>?
        )
    }

    @Serializable
    data class Guess(val year: Int, val points: Int)

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
        /*@Transient
        val duration: Duration = trackTimeMillis.milliseconds*/

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
}