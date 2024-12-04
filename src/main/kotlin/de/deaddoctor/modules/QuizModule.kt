package de.deaddoctor.modules

import de.deaddoctor.Module
import de.deaddoctor.respondPage
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import kotlin.random.Random

object QuizModule : Module {

    private val jsonParser = Json

    @Serializable
    data class Question(val text: String, val answers: Array<Answer>, val tags: Array<String>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Question) return false

            if (text != other.text) return false
            if (!answers.contentEquals(other.answers)) return false
            if (!tags.contentEquals(other.tags)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + answers.contentHashCode()
            result = 31 * result + tags.contentHashCode()
            return result
        }
    }

    @Serializable
    data class Answer(val text: String, val correct: Boolean)


    private val dataFile = File("quiz.json")
    @OptIn(ExperimentalSerializationApi::class)
    private val questions: Array<Question> = jsonParser.decodeFromStream(dataFile.inputStream())

    override fun path() = "quiz"

    override fun Route.route() {
        get {
            val question = questions[Random.nextInt(questions.size)]

            call.respondPage("Quiz") {
                content {
                    section {
                        h1 { +"Quiz" }
                        h3 { +question.text }
                        for (answer in question.answers) {
                            p {
                                +answer.text
                                if (answer.correct) entity(Entities.checkmark)
                            }
                        }
                        for (tag in question.tags) {
                            span { +tag }
                        }
                    }
                }
            }
        }
    }
}