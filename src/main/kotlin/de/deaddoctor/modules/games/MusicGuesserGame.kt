package de.deaddoctor.modules.games

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import de.deaddoctor.modules.Game
import de.deaddoctor.modules.GameChannel
import de.deaddoctor.modules.LobbyModule
import io.ktor.server.application.*
import kotlinx.html.h2
import kotlinx.html.section
import kotlinx.serialization.Serializable

class MusicGuesserGame(channel: GameChannel, val players: MutableMap<TrackedUser, LobbyModule.Lobby.Player>) : Game<MusicGuesserGame>({
    receiverTyped(MusicGuesserGame::helloDestination)
}) {
    private val sendAnswer = channel.destination<Int>()

    companion object {
        const val NAME = "Music Guesser"
        private val NAME_ID = NAME.lowercase().replace(' ', '-')
    }

    fun helloDestination(ctx: Channel.Context, something: SomePacket) {
        println(something.a)
        sendAnswer.toConnection(ctx.connection, something.value)
    }

    @Serializable
    data class SomePacket(val a: String, val value: Int)

    override suspend fun get(call: ApplicationCall) {
        call.respondPage(NAME) {
            head {
                addScript("game/$NAME_ID/main")
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
}