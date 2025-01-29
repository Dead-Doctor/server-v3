package de.deaddoctor.modules.games

import de.deaddoctor.Channel
import de.deaddoctor.ViteBuild.addScript
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
        fun helloDestination(ctx: Channel.Context, something: SomePacket) {
            println(ctx)
            println(something)
        }
    }
}