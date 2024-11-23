package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.CSSResource.Companion.addStyles
import de.deaddoctor.CSSResource.Companion.getStyles
import de.deaddoctor.ViteBuild.addScript
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.css.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

//TODO: power ups / sprinting / fruits slow down or decrease size of snake

object SnakeModule : Module {
    override fun path() = "snake"

    private const val SERVER_TICK_DELAY = 1.0 / 60.0
    private const val ASPECT_RATIO = 0.6
    private const val START_DISTANCE_CENTER = 0.3
    private const val START_LENGTH = 0.15
    private const val START_WIDTH = 0.04
    private const val START_SEGMENT_COUNT = 20
    private val SNAKE_COLORS = listOf("#01baff", "#9372e2", "#ffeb02", "#e372c5")
    private val COUNT_DOWN_TIMEOUT = 1000.milliseconds

    private var currentState = GameState.LOBBY

    private val playersPlaying: MutableMap<AccountUser, Boolean> = mutableMapOf()

    /**
     * List of snakes
     *  - `null` when game is not running
     **/
    private var snakes: MutableList<Snake>? = null

    private var lastTick: Duration = Duration.ZERO

    override fun Route.route() {
        get {
            call.respondPage("Snake Game") {
                head {
                    addStyles(snakeStyles, call.request.url)
                    addScript("snake")
                }
                content {
                    section {
                        div("lobby menu show") {
                            h2 { +"Lobby" }
                            div("players") {}
                            div {
                                button {
                                    id = "joinBtn"
                                    disabled = true
                                    +"Join"
                                }
                                button {
                                    id = "startBtn"
                                    disabled = true
                                    +"Start Game"
                                }
                            }
                        }
                        div("start menu") {
                            h2 {
                                id = "countDown"
                            }
                        }
                        div("winner menu") {
                            h2 { +"Winner" }
                            div("players") {}
                            button {
                                id = "closeWinnerBtn"
                                disabled = true
                                +"Close"
                            }
                        }
                        canvas {}
                    }
                }
            }
        }
        getStyles(snakeStyles)

        webSocketAddressable("ws") {
            connection {
                sendBack(currentState.packet)
                if (snakes != null)
                    sendBack(Packet("updateSnakes", snakes))
                if (user !is AccountUser) {
                    sendBack(updatedPlayers())
                    return@connection
                }
                if (playersPlaying.containsKey(user)) {
                    sendBack(Packet("updateYou", "alreadyLoggedIn"))
                    return@connection
                }
                sendBack(Packet("updateYou", user.id))
                playersPlaying[user] = false
                sendToAll(updatedPlayers())
            }
            disconnection {
                if (user !is AccountUser || !playersPlaying.containsKey(user) || countConnections(user) >= 1) return@disconnection
                if (playersPlaying[user]!!) {
                    if (snakes != null) {
                        snakes!!.find { it.player == user.id }?.dead = true
                    }
                    if (currentState == GameState.RUNNING)
                        checkWinner()
                }
                playersPlaying.remove(user)
                sendToAll(updatedPlayers())
                if (playersPlaying.count { it.value } == 0) {
                    resetGame()
                    return@disconnection
                }
            }
            destination("join") { playing: Boolean ->
                if (currentState != GameState.LOBBY || user !is AccountUser || !playersPlaying.containsKey(user))
                    return@destination
                playersPlaying[user] = playing
                sendToAll(updatedPlayers())
            }
            destination("start") {
                if (currentState != GameState.LOBBY || playersPlaying[user] != true) return@destination
                startGame()
            }
            destination("snake") { snake: Snake ->
                if (currentState != GameState.RUNNING || user !is AccountUser || playersPlaying[user] != true || snake.player != user.id) return@destination
                val oldSnake = snakes!!.find { it.player == snake.player }!!
                oldSnake.segments = snake.segments
                oldSnake.width = snake.width
                oldSnake.dead = snake.dead
            }
            destination("fail") {
                if (currentState != GameState.RUNNING || user !is AccountUser || playersPlaying[user] != true) return@destination
                snakes!!.find { it.player == user.id }?.dead = true
                sendToAll(updatedSnakes())
                checkWinner()
            }
            destination("reset") {
                if (currentState != GameState.WINNER || user !is AccountUser || playersPlaying[user] != true) return@destination
                resetGame()
            }
        }
    }

    private fun WebSocketEventHandlerContext.startGame() {
        currentState = GameState.START
        sendToAll(currentState.packet)
        generateStartingLocations()
        sendToAll(updatedSnakes())

        CoroutineScope(Job()).launch {
            delay(COUNT_DOWN_TIMEOUT * 3)
            if (currentState != GameState.START) return@launch
            currentState = GameState.RUNNING
            sendToAll(currentState.packet)

            lastTick = -SERVER_TICK_DELAY.seconds
            while (currentState == GameState.RUNNING) {
                val now = System.currentTimeMillis().milliseconds
                val delta = now - lastTick
                lastTick = now
                sendToAll(updatedSnakes())
                delay(SERVER_TICK_DELAY.seconds - delta)
            }
        }
    }

    private fun generateStartingLocations() {
        val segmentLength = START_LENGTH / START_SEGMENT_COUNT

        val players = playersPlaying.filter { it.value }.map { it.key }
        val n = players.size
        val angleGap = 2.0 * PI / n
        snakes = MutableList(n) { i ->
            val angle = angleGap * i
            val directionX = sin(angle)
            val directionY = -cos(angle)
            val segments = Array(START_SEGMENT_COUNT + 1) {
                val distanceFromCenter = START_DISTANCE_CENTER + segmentLength * it
                arrayOf(0.5 + directionX * distanceFromCenter, 0.5 / ASPECT_RATIO + directionY * distanceFromCenter)
            }
            Snake(segments, START_WIDTH, SNAKE_COLORS[i % SNAKE_COLORS.size], false, players[i].id)
        }
    }

    private fun WebSocketEventHandlerContext.checkWinner() {
        if (snakes!!.count { !it.dead } > 1) return
        currentState = GameState.WINNER
        sendToAll(currentState.packet)
        sendToAll(updatedSnakes())
    }

    private fun WebSocketEventHandlerContext.resetGame() {
        snakes = null
        sendToAll(updatedSnakes())
        currentState = GameState.LOBBY
        sendToAll(currentState.packet)
    }

    private fun updatedPlayers() = Packet("updatePlayers", playersPlaying.map {
        PlayerInfo(it.key, it.value)
    })

    private fun updatedSnakes() = Packet("updateSnakes", snakes)


    val main = TagSelector("main")

    private val snakeStyles by CSSRules {
        body {
            height = LinearDimension.auto
        }

        main {
            display = Display.block
            width = 100.vw - 6.rem
            height = 100.vh - 5.rem - 6.rem
        }

        section {
            position = Position.relative
            display = Display.flex
            declarations["aspect-ratio"] = ASPECT_RATIO.toString()
            maxHeight = 100.pct
            margin = "0 auto"
            overflow = Overflow.hidden
            declarations["touch-action"] = "none"
        }

        rule(".menu") {
            display = Display.none
            flexDirection = FlexDirection.column
            justifyContent = JustifyContent.spaceEvenly
            alignItems = Align.center

            position = Position.absolute
            left = 0.px
            top = 0.px
            right = 0.px
            bottom = 0.px
        }

        rule(".players") {
            display = Display.flex
            gap = Gap("16px")
        }

        rule(".players img") {
            height = 64.px
            borderRadius = 50.pct
        }

        rule(".players img.not-joined") {
            filter = "brightness(30%)"
        }

        rule(".show") {
            display = Display.flex
        }

        canvas {
            backgroundColor = Color("#2a6911")
        }
    }

    enum class GameState {
        LOBBY,
        START,
        RUNNING,
        WINNER;

        val packet: Packet<Int>
            get() = Packet("gameState", this.ordinal)
    }

    @Serializable
    data class Packet<T>(val type: String, val data: T)

    @Serializable
    data class PlayerInfo(val id: String, val name: String, val avatar: String, val playing: Boolean) {
        constructor(account: AccountUser, playing: Boolean) : this(
            account.id,
            account.name,
            account.avatar,
            playing
        )
    }

    @Serializable
    data class Snake(var segments: Array<Point>, var width: Double, val color: String, var dead: Boolean, val player: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Snake) return false

            if (!segments.contentDeepEquals(other.segments)) return false
            if (width != other.width) return false
            if (color != other.color) return false
            if (player != other.player) return false

            return true
        }

        override fun hashCode(): Int {
            var result = segments.contentDeepHashCode()
            result = 31 * result + width.hashCode()
            result = 31 * result + color.hashCode()
            result = 31 * result + player.hashCode()
            return result
        }
    }
}

typealias Point = Array<Double>