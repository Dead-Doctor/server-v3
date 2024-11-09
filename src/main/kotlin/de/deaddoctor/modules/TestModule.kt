package de.deaddoctor.modules

import de.deaddoctor.Module
import de.deaddoctor.getAccount
import de.deaddoctor.respondTemplate
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.html.p

object TestModule : Module {
    override fun path() = "test"

    override fun Route.route() {
        get {
            val account = call.getAccount()
            call.respondTemplate("Test") {
//                throw Exception("OH NO!")
                p { +"Hello, ${account.name}!" }
            }
        }
    }
}