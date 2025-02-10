package de.deaddoctor.modules.games

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import de.deaddoctor.modules.*
import io.ktor.server.application.*
import kotlinx.html.h2
import kotlinx.html.section
import kotlinx.serialization.Serializable

class MusicGuesserGame(
    val type: GameModule.GameType<MusicGuesserGame>,
    channel: GameChannel,
    val players: MutableMap<TrackedUser, LobbyModule.Lobby.Player>
) : Game<MusicGuesserGame>({
    receiverTyped(MusicGuesserGame::helloDestination)
}) {
    private val sendAnswer = channel.destination<Int>()

    fun helloDestination(ctx: Channel.Context, something: SomePacket) {
        println(something.a)
        sendAnswer.toConnection(ctx.connection, something.value)
    }

    @Serializable
    data class SomePacket(val a: String, val value: Int)

    override suspend fun get(call: ApplicationCall) {
        call.respondPage(type.name) {
            head {
                addScript("game/${type.id}/main")
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