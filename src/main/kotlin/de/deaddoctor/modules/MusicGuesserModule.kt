package de.deaddoctor.modules

import de.deaddoctor.Module
import de.deaddoctor.user
import de.deaddoctor.httpClient
import de.deaddoctor.respondPage
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
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
import java.io.File
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSerializationApi::class)
object MusicGuesserModule : Module {
    const val NAME = "Music Guesser"

    private val cacheMillis = 30L.days
    private val storeFronts = listOf("us", "gb", "de")
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

    private var ready = false
    private val tracksCache = File("tracks.json")
    private lateinit var tracks: MutableList<Track>

    init {
        thread(isDaemon = true, name = "fetch-playlists") {
            runBlocking {
                if (!tracksCache.isFile || (System.currentTimeMillis() - tracksCache.lastModified()).milliseconds > cacheMillis) {
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
                    println("Successfully loaded ${tracks.size} tracks!")
                    jsonParser.encodeToStream(tracks, tracksCache.outputStream())
                } else {
                    tracks = jsonParser.decodeFromStream(tracksCache.inputStream())
                    ready = true
                    println("Successfully read ${tracks.size} cached tracks!")
                }
            }
        }
    }

    override fun path() = NAME.lowercase().replace(' ', '-')

    override fun Route.route() {
        get {
            if (!ready) throw Exception("Not ready yet")
            val user = call.user
            val track = tracks[Random.nextInt(0, tracks.size)]
            val song = queryTrack(track)
            call.respondPage(NAME) {
                content {
                    section {
                        h1 { +NAME }
                        h3 { +"Welcome to $NAME, ${user.name}!" }
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
}