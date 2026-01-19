package de.deaddoctor.modules.games

import de.deaddoctor.*
import de.deaddoctor.modules.*
import de.deaddoctor.modules.LobbyModule.YouInfo
import io.ktor.server.application.*
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

class QuizGame(channel: GameChannel, lobby: LobbyModule.Lobby) : Game<QuizGame>(channel, lobby, {
    receiverTyped(QuizGame::onGuess)
    receiverTyped(QuizGame::onFinish)
}) {
    @Serializable
    data class Question(val text: String, val answers: List<Answer>, val tags: List<String>) {
        fun shuffled() = Question(text, answers.shuffled(), tags)
    }

    @Serializable
    data class Answer(val text: String, val correct: Boolean)

    companion object : GameType<QuizGame> {
        override fun id() = "quiz"
        override fun name() = "Quiz"
        override fun description() = "It's a quiz. What is there more to know?"
        override fun settings() = GameSettings()
        override suspend fun create(channel: GameChannel, lobby: LobbyModule.Lobby, settings: GameSettings) = QuizGame(channel, lobby)

        private val logger = LoggerFactory.getLogger(QuizGame::class.java)
        private val jsonParser = Json

        private val dataFile = File(persistentDir, "quiz.json")
        private var errorMsg: String? = null
        private val questions: Array<Question>?

        init {
            var loaded: Array<Question>? = null
            try {
                loaded = jsonParser.decodeFromResource(dataFile.inputStream())
                logger.info("Successfully loaded ${loaded?.size} questions!")
            } catch (error: Exception) {
                errorMsg = "Parsing Error: ${error.message}"
                logger.error("Error while loading questions:", error)
            }
            questions = loaded
        }
    }

    private val sendResults = channel.destination<Array<List<String>>>()

    private val players = lobby.activePlayers.map { it.key }
    private val question = questions?.random()?.shuffled()
    private val guesses = mutableMapOf<TrackedUser, Int>()
    private var showResults = false

    private val guessesPerAnswer
        get() = Array(questions!!.size) { i ->
            guesses.filter { it.value == i }.map { it.key.id }
        }

    override suspend fun get(call: ApplicationCall) {
        if (questions == null) return call.respondPage(name()) {
            content {
                h1 { +"Error" }
                h2 { +(errorMsg ?: "???") }
            }
        }
        call.respondGame(QuizGame) {
            addData("youInfo", YouInfo(call.trackedUser))
            addData("lobbyInfo", lobbyInfo)
            addData("question", question)
            addData("results", if (showResults) guessesPerAnswer else null)
        }
    }

    private fun onGuess(ctx: Channel.Context, i: Int?) {
        if (ctx.user !is TrackedUser || ctx.user !in players) return
        if (i != null) {
            guesses[ctx.user] = i
            checkReveal()
        } else {
            guesses.remove(ctx.user)
        }
    }

    private fun checkReveal() {
        if (guesses.size < players.size) return

        showResults = true
        for ((player, guess) in guesses) {
            if (question!!.answers[guess].correct)
                gameWon(player)
        }
        sendResults.toAll(guessesPerAnswer)
    }

    private fun onFinish(ctx: Channel.Context) {
        if (ctx.user !is TrackedUser || !isOperator(ctx.user)) return
        finish()
    }
}
