package de.deaddoctor.modules.games

import de.deaddoctor.ViteBuild.addScript
import de.deaddoctor.WebSocketBinaryContext
import de.deaddoctor.modules.Game
import de.deaddoctor.respondPage
import io.ktor.server.application.*
import kotlinx.html.h2
import kotlinx.html.section
import kotlinx.serialization.Serializable

class MusicGuesserGame : Game({
    receiver(::helloDestination)
}) {

    override suspend fun get(call: ApplicationCall) {
        call.respondPage("Music Guesser") {
            head {
                addScript("game/music-guesser/main")
            }
            content {
                section {
                    h2 {
                        +"Hello, World!"
                    }
                }
            }
        }
    }

    @Serializable
    data class SomePacket(val a: String, val value: Int)

    companion object {
        suspend fun helloDestination(ctx: WebSocketBinaryContext, something: SomePacket) {
            println(ctx)
            println(something)
        }
    }
}