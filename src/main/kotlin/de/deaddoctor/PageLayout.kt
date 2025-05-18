package de.deaddoctor

import de.deaddoctor.CSSResource.Companion.addStyles
import de.deaddoctor.modules.GameModule
import io.ktor.http.*
import io.ktor.server.html.*
import kotlinx.datetime.*
import kotlinx.html.*

class PageLayout(private val user: User?, private val uri: String, private val title: String?): Template<HTML> {
    companion object {
        val styles by CSSFile()

        private val svgs = mapOf(
            Pair(
                "hamburger",
                listOf(
                    "M3 3.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1 0-1",
                    "M3 7.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1 0-1",
                    "M3 11.5h10a.5.5 0 0 1 0 1H3a.5.5 0 0 1 0-1"
                )
            ),
            Pair(
                "discord", listOf(
                    "M13.545 2.907a13.2 13.2 0 0 0-3.257-1.011.05.05 0 0 0-.052.025c-.141.25-.297.577-.406.833a12.2 12.2 0 0 0-3.658 0 8 8 0 0 0-.412-.833.05.05 0 0 0-.052-.025c-1.125.194-2.22.534-3.257 1.011a.04.04 0 0 0-.021.018C.356 6.024-.213 9.047.066 12.032q.003.022.021.037a13.3 13.3 0 0 0 3.995 2.02.05.05 0 0 0 .056-.019q.463-.63.818-1.329a.05.05 0 0 0-.01-.059l-.018-.011a9 9 0 0 1-1.248-.595.05.05 0 0 1-.02-.066l.015-.019q.127-.095.248-.195a.05.05 0 0 1 .051-.007c2.619 1.196 5.454 1.196 8.041 0a.05.05 0 0 1 .053.007q.121.1.248.195a.05.05 0 0 1-.004.085 8 8 0 0 1-1.249.594.05.05 0 0 0-.03.03.05.05 0 0 0 .003.041c.24.465.515.909.817 1.329a.05.05 0 0 0 .056.019 13.2 13.2 0 0 0 4.001-2.02.05.05 0 0 0 .021-.037c.334-3.451-.559-6.449-2.366-9.106a.03.03 0 0 0-.02-.019m-8.198 7.307c-.789 0-1.438-.724-1.438-1.612s.637-1.613 1.438-1.613c.807 0 1.45.73 1.438 1.613 0 .888-.637 1.612-1.438 1.612m5.316 0c-.788 0-1.438-.724-1.438-1.612s.637-1.613 1.438-1.613c.807 0 1.451.73 1.438 1.613 0 .888-.631 1.612-1.438 1.612"
                )
            ),
            Pair(
                "github",
                listOf("M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8")
            )
        )
    }

    val head = Placeholder<HEAD>()
    val content = Placeholder<MAIN>()

    override fun HTML.apply() {
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            title {
                if (this@PageLayout.title == null) {
                    +"deaddoctor"
                } else {
                    +this@PageLayout.title
                    +" "
                    entity(Entities.ndash)
                    +" deaddoctor"
                }
            }
            addStyles(styles)
            link(rel = "icon", type = "image/x-icon", href = "/favicon.ico")
            insert(head)
        }
        body {
            header {
                nav {
                    a(classes = "action", href = "/", block = branding)
                    links()
                    val profileAction = if (user !is AccountUser) "login" else "logout"
                    a(classes = "profile action", href = "/$profileAction?redirectUrl=${uri.encodeURLParameter()}") {
                        img(src = (user as? AccountUser)?.avatar ?: "https://cdn.discordapp.com/embed/avatars/4.png")
                        span { +profileAction }
                    }
                    button(classes = "hamburger", type = ButtonType.button) {
                        id = "menuBtn"
                        this.title = "Toggle Menu"
                        icon("hamburger")
                    }
                }
            }
            main {
                insert(content)
            }
            footer {
                div("about") {
                    span(block = branding)
                    span { +"Made by deaddoctor" }
                    span("platforms") {
                        a(href = "https://discordapp.com/users/621027101645996053") { icon("discord") }
                        a(href = "https://github.com/Dead-Doctor") { icon("github") }
                    }
                    span("copyright") {
                        entity(Entities.copy)
                        val now = Clock.System.now()
                        +" ${now.toLocalDateTime(TimeZone.currentSystemDefault()).year} deaddoctor, All rights reserved."
                    }
                }
                links()
            }
            script {
                unsafe {
                    +"""
                const menuBtn = document.getElementById('menuBtn');
                const header = document.getElementsByTagName('nav')[0];
                menuBtn.addEventListener('click', _ => header.classList.toggle('expanded'));
                """.trimIndent()
                }
            }
        }
    }

    private val branding: HtmlBlockInlineTag.() -> Unit = {
        classes += "branding"
        img(src = "/logo.png", alt = "Logo")
        span { +"deaddoctor" }
    }

    private val links: FlowContent.() -> Unit = {
        ul("links") {
            li { a(classes = "action chip", href = "/${GameModule.path()}") { +"Games" } }
            li { a(classes = "action chip", href = "/maps") { +"Maps" } }
            li { a(classes = "action chip", href = "/about") { +"About" } }
        }
    }

    private val icon: FlowOrPhrasingContent.(String) -> Unit = { name ->
        val paths = svgs[name] ?: throw IllegalArgumentException("The svg-icon '$name' does not exist!")
        svg("icon") {
            attributes["viewBox"] = "0 0 16 16"

            for (d in paths) {
                path(d = d)
            }
        }
    }
}

open class PATH(initialAttributes: Map<String, String>, override val consumer: TagConsumer<*>) :
    HTMLTag("path", consumer, initialAttributes, null, false, false), HtmlBlockInlineTag

@HtmlTagMarker
inline fun FlowOrInteractiveOrPhrasingContent.path(
    d: String? = "",
    classes: String? = null,
    crossinline block: PATH.() -> Unit = {}
): Unit = PATH(attributesMapOf("d", d, "class", classes), consumer).visit(block)