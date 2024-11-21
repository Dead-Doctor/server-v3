package de.deaddoctor.modules

import de.deaddoctor.AccountUser
import de.deaddoctor.Module
import de.deaddoctor.user
import de.deaddoctor.respondPage
import io.ktor.server.routing.*
import kotlinx.html.p

object TestModule : Module {
    override fun path() = "test"

    override fun Route.route() {
        get {
            call.respondPage("Test") {
//                throw Exception("OH NO!")
                content {
                    p { +"Hello, ${(call.user as? AccountUser)?.name ?: "Anonymous"}!" }
                }
            }
        }
    }
}