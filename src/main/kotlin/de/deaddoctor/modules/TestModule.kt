package de.deaddoctor.modules

import de.deaddoctor.Module
import de.deaddoctor.user
import de.deaddoctor.respondPage
import io.ktor.server.routing.*
import kotlinx.html.p

object TestModule : Module {
    override fun path() = "test"

    override fun Route.route() {
        get {
            val account = call.user
            call.respondPage("Test") {
//                throw Exception("OH NO!")
                content {
                    p { +"Hello, ${account.name}!" }
                }
            }
        }
    }
}