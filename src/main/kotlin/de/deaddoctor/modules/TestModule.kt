package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.ViteBuild.addScript
import io.ktor.server.routing.*

object TestModule : Module {
    override fun path() = "test"

    private val channel = Channel()
    private val sendMessage = channel.destination<Int>()

    override fun Route.route() {
        get {
            call.respondPage("Test") {
                head {
                    addScript("test")
                }
            }
        }

        fun Channel.Context.onConnection() {
            for (i in 0..10)
                sendMessage.toConnection(connection, i)
        }

        channel.connection(Channel.Context::onConnection)

        openChannel("channel", channel)
    }
}