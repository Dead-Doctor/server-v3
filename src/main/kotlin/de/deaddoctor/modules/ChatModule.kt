package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.CSSResource.Companion.addStyles
import de.deaddoctor.CSSResource.Companion.getStyles
import de.deaddoctor.ViteBuild.addScript
import io.ktor.server.routing.*
import kotlinx.css.*
import kotlinx.html.*

object ChatModule : Module {
    override fun path() = "chat"

    override fun Route.route() {
        get {
            call.respondPage("Chat App") {
                head {
                    addStyles(chatStyle, call.request.url)
                    addScript("chat")
                }
                content {
                    section {
                        h1 { +"Chat" }
                        div("chat") {
                            p("messages") { span("info") { +"Welcome to the chat!" } }
                            input(type = InputType.text)
                        }
                    }
                }
            }
        }

        getStyles(chatStyle)

        webSocketAddressable("ws") {
            destination("sendMessage") { msg: String ->
                if (msg == "/list") {
                    val onlineAccounts = connections.map { it.user }.distinct()
                    sendToConnection(
                        connection,
                        "Currently online (${onlineAccounts.count()}): ${onlineAccounts.joinToString(", ") { (it as? AccountUser)?.name ?: "Anonymous" }}"
                    )
                } else if (msg.startsWith("/me ")) {
                    if (user is AccountUser) sendToUser(user, "[Secret]: ${msg.substringAfter("/me ")}")
                    else sendToConnection(connection, "You're not logged in!")
                } else {
                    sendToAll("<${(user as? AccountUser)?.name ?: "Anonymous"}> $msg")
                }
            }
        }
    }

    private val chatStyle by CSSRules {
        rule(".chat") {
            display = Display.flex
            flexDirection = FlexDirection.column
        }
        rule(".messages") {
            display = Display.flex
            flexDirection = FlexDirection.column
            height = LinearDimension("200px")
            padding(0.2.rem)
            backgroundColor = Color("var(--secondary)")
            color = Color.white
            border = "var(--border)"
            borderBottom = "none"
            wordWrap = WordWrap.breakWord
            overflowY = Overflow.scroll
        }
        rule(".info") {
            color = Color.yellow
        }
    }
}
