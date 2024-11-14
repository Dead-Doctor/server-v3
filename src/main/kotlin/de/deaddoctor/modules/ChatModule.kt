package de.deaddoctor.modules

import de.deaddoctor.*
import de.deaddoctor.CSSResource.Companion.addStyles
import de.deaddoctor.CSSResource.Companion.getStyles
import io.ktor.server.application.*
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
                    val onlineAccounts = connections.map { it.account }.distinct()
                    sendToConnection(
                        connection,
                        "Currently online (${onlineAccounts.count()}): ${onlineAccounts.joinToString(", ") { it.name }}"
                    )
                } else if (msg.startsWith("/me ")) {
                    if (account.loggedIn) sendToAccount(account, "[Secret]: ${msg.substringAfter("/me ")}")
                    else sendToConnection(connection, "You're not logged in!")
                } else {
                    sendToAll("<${account.name}> $msg")
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
