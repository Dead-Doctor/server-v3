package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.CSSResource.Companion.addStyles
import de.deaddoctor.CSSResource.Companion.getStyles
import io.ktor.server.application.*
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

object SnakeModule : Module {
    override fun path() = "snake"

    private const val SERVER_TICK_DELAY = 1.0 / 60.0
    private const val ASPECT_RATIO = 0.6
    private const val START_DISTANCE_CENTER = 0.3
    private const val START_LENGTH = 0.15
    private const val START_WIDTH = 0.05
    private const val START_SEGMENT_COUNT = 20
    private val SNAKE_COLORS = listOf("#01baff", "#9372e2", "#ffeb02", "#e372c5")

    private var currentState = GameState.LOBBY

    private val playersPlaying: MutableMap<Account, Boolean> = mutableMapOf()

    private var snakes: MutableList<Snake>? = null

    private var lastTick: Duration = Duration.ZERO

    override fun Route.route() {
        get {
            call.respondTemplate("Snake Game", {
                addStyles(snakeStyles, call.request.url)
                addScript("snake")
            }) {
                div("container") {
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
                    div("winner menu") {
                        +"Winner"
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
        getStyles(snakeStyles)

        webSocketAddressable("ws") {
            connection {
                sendBack(currentState.packet)
                if (snakes != null)
                    sendBack(Packet("updateSnakes", snakes))
                if (!account.loggedIn) {
                    sendBack(updatedPlayers())
                    return@connection
                }
                if (playersPlaying.containsKey(account)) {
                    sendBack(Packet("updateYou", "alreadyLoggedIn"))
                    return@connection
                }
                sendBack(Packet("updateYou", account.id!!.toString()))
                playersPlaying[account] = false
                sendToAll(updatedPlayers())
            }
            disconnection {
                if (!account.loggedIn || !playersPlaying.containsKey(account)) return@disconnection
                if (playersPlaying[account]!!) {
                    if (snakes != null)
                        snakes!!.removeIf { it.player == account.id!!.toString() }
                    checkWinner()
                }
                playersPlaying.remove(account)
                if (playersPlaying.count { it.value } == 0) {
                    resetGame()
                    return@disconnection
                }
                sendToAll(updatedPlayers())
            }
            destination("join") { playing: Boolean ->
                if (currentState != GameState.LOBBY || !account.loggedIn || !playersPlaying.containsKey(account)) return@destination
                playersPlaying[account] = playing
                sendToAll(updatedPlayers())
            }
            destination("start") {
                if (currentState != GameState.LOBBY || playersPlaying[account] != true) return@destination
                startGame()
            }
            destination("snake") { snake: Snake ->
                if (currentState != GameState.RUNNING || !account.loggedIn || playersPlaying[account] != true || snake.player != account.id!!.toString()) return@destination
                val oldSnake = snakes!!.find { it.player == snake.player }!!
                oldSnake.segments = snake.segments
                oldSnake.width = snake.width
            }
            destination("fail") {
                if (currentState != GameState.RUNNING || !account.loggedIn || playersPlaying[account] != true) return@destination
                snakes!!.removeIf { it.player == account.id!!.toString() }
                sendToAll(updatedSnakes())
                checkWinner()
            }
            destination("closeWinner") {
                if (currentState != GameState.WINNER || !account.loggedIn || playersPlaying[account] != true) return@destination
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
            delay(3.seconds)
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
            Snake(segments, START_WIDTH, SNAKE_COLORS[i % SNAKE_COLORS.size], players[i].id!!.toString())
        }
    }

    private fun WebSocketEventHandlerContext.checkWinner() {
        if (snakes!!.size > 1) return
        currentState = GameState.WINNER
        sendToAll(currentState.packet)
    }

    private fun WebSocketEventHandlerContext.resetGame() {
        snakes = null
        sendToAll(updatedSnakes())
        currentState = GameState.LOBBY
        sendToAll(currentState.packet)
        playersPlaying.replaceAll { _, _ -> false }
        sendToAll(updatedPlayers())
    }

    private fun updatedPlayers() = Packet("updatePlayers", playersPlaying.map {
        PlayerInfo(it.key, it.value)
    })

    private fun updatedSnakes() = Packet("updateSnakes", snakes)

    private val snakeStyles by CSSRules {
        root {
//            overscrollBehavior = OverscrollBehavior.contain
            declarations["touch-action"] = "none"
        }

        mainTag {
            display = Display.flex
            justifyContent = JustifyContent.center
            alignItems = Align.stretch
            padding(30.px)
            overflow = Overflow.hidden
        }

        rule(".container") {
            position = Position.relative
            declarations["aspect-ratio"] = ASPECT_RATIO.toString()
            overflow = Overflow.hidden
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
        constructor(account: Account, playing: Boolean) : this(
            account.id!!.toString(),
            account.name,
            account.avatar!!,
            playing
        )
    }

    @Serializable
    data class Snake(var segments: Array<Point>, var width: Double, val color: String, val player: String) {
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