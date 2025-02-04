package de.deaddoctor.modules.games

import de.deaddoctor.Channel
import de.deaddoctor.ViteBuild.addScript
import de.deaddoctor.modules.Game
import de.deaddoctor.respondPage
import io.ktor.server.application.*
import kotlinx.html.h2
import kotlinx.html.section
import kotlinx.serialization.Serializable

class MusicGuesserGame : Game<MusicGuesserGame>({
    receiverTyped(MusicGuesserGame::helloDestination)
}) {

    fun helloDestination(ctx: Channel.Context, something: SomePacket) {
        println(ctx)
        println(something)
    }

    @Serializable
    data class SomePacket(val a: String, val value: Int)

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
//        val game = currentGame
//        if (game == null) {
//            call.respondRedirect(relative("/start"))
//            return@get
//        }
//        val user = call.trackedUser
//        if (!game.joined(user) && (user is AccountUser || game.playerById(user.id) != null)) {
//            game.join(user)
//        }
//        call.respondPage(NAME) {
//            head {
//                addData("playerInfo", game.playerInfo)
//                addData("gameInfo", game.gameInfo(user))
//                addData("round", game.roundInfo)
//                addScript("$NAME_ID/main")
//            }
//        }
    }
}