package de.deaddoctor.modules.games

import de.deaddoctor.modules.Game
import de.deaddoctor.respondPage
import io.ktor.server.application.*
import kotlinx.html.h2
import kotlinx.html.section

class MusicGuesserGame : Game {

    override suspend fun get(call: ApplicationCall) {
        call.respondPage("Music Guesser") {
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