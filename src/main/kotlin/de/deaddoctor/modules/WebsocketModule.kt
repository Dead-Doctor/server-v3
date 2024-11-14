package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.CSSResource.Companion.addStyles
import de.deaddoctor.CSSResource.Companion.getStyles
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.css.*
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.serialization.Serializable

object WebsocketModule : Module {

    @Serializable
    data class Data(val id: Int, val name: String)

    @Serializable
    data class AccountInfo(val name: String, val avatar: String)

    override fun path() = "ws"

    override fun Route.route() {
        get {
            call.respondPage("WebSocket") {
                head {
                    addStyles(styles, call.request.url)
                    addScript("wsTest")
                }
                content {
                    section {
                        p { +"Currently logged in:" }
                        div("accounts") {}
                    }
                }
            }
        }

        getStyles(styles)

        get("data") {
            call.respond(Data(1, "one"))
        }

        webSocketAddressable("ws") {
            connection { sendUpdatedAccountInfos() }
            disconnection { sendUpdatedAccountInfos() }

            destination("message") { msg: String ->
                sendToConnection(connection, Frame.Text("YOU SAID: $msg"))
                if (msg.equals("bye", ignoreCase = true))
                    closeConnection(connection, CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
            }
        }
    }

    private fun WebSocketEventHandlerContext.sendUpdatedAccountInfos() {
        val connectedAccounts = connections.filter { it.account.loggedIn }.distinct()
        sendToAll(connectedAccounts.map { AccountInfo(it.account.name, it.account.avatar!!) })
    }

    private val styles by CSSRules {
        rule(".accounts") {
            height = LinearDimension("128px")
            display = Display.flex
            gap = Gap("16px")
        }

        rule(".accounts > img") {
            borderRadius = 50.pct
        }
    }
}