package de.deaddoctor

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun Application.installOAuth() {
    authentication {
        oauth("discord") {
            skipWhen { call ->
                call.sessions.get<UserSession>() != null || call.parameters["error"] != null
            }
            client = httpClient
            providerLookup = {
                val redirects = mutableMapOf<String, String>()
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "discord",
                    authorizeUrl = "https://discordapp.com/api/oauth2/authorize",
                    accessTokenUrl = "https://discordapp.com/api/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = getConfig("DISCORD_CLIENT_ID"),
                    clientSecret = getConfig("DISCORD_CLIENT_SECRET"),
                    defaultScopes = listOf("identify"),
                    onStateCreated = { call, state ->
                        call.parameters["redirectUrl"]?.let {
                            redirects[state] = it
                        }
                    },
                    authorizeUrlInterceptor = {
                        val state = parameters["state"]!!
                        val redirectUrl = redirects.remove(state) ?: "/"
                        parameters["state"] = OAuthState.encode(state, redirectUrl)
                    }
                )
            }
            urlProvider = { "${request.url.protocolWithAuthority}/login/callback" }
        }
    }
}


fun Routing.routeOAuth() {
    authenticate("discord") {
        get("login") {
            call.respondRedirect("/")
        }
        get("login/callback") {
            val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
            if (principal != null) {
                call.sessions.set(UserSession.generate(principal))

            } else if (application.developmentMode) when (call.parameters["error"]) {
                "access_denied" -> {}
                else -> throw IllegalArgumentException("${call.parameters["error"]}: ${call.parameters["error_description"]}")
            }
            call.respondRedirect(OAuthState.url(call.parameters["state"]))
        }
    }
    get("logout") {
        call.sessions.clear<UserSession>()
        call.respondRedirect(call.parameters["redirectUrl"] ?: "/")
    }
}

data object OAuthState {
    fun encode(state: String, redirectUrl: String) = state + redirectUrl.encodeURLParameter()
    fun decode(state: String) = state.take(16)
    fun url(state: String?) = state.orEmpty().drop(16).decodeURLPart().ifBlank { "/" }
}

@Serializable
data class UserSession(val state: String, val token: String, val userInfo: UserInfo) {
    companion object {
        suspend fun generate(principal: OAuthAccessTokenResponse.OAuth2) = UserSession(
            OAuthState.decode(principal.state!!),
            principal.accessToken,
            httpClient.get("https://discord.com/api/users/@me") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
                }
            }.body<UserInfo>()
        )
    }
}

@Serializable
data class UserInfo(
    val id: Long,
    val username: String,
    val discriminator: String,
    @SerialName("global_name")
    val globalName: String?,
    val avatar: String?,
//    val bot: Boolean,
//    val system: Boolean,
    @SerialName("mfa_enabled")
    val mfaEnabled: Boolean,
    val banner: String?,
    @SerialName("banner_color")
    val bannerColor: String?,
    @SerialName("accent_color")
    val accentColor: Int?,
    val locale: String,
    val clan: String?,
//    val verified: Boolean,
//    val email: String,
    val flags: Int,
    @SerialName("premium_type")
    val premiumType: Int,
    @SerialName("public_flags")
    val publicFlags: Int,
//    @SerialName("avatar_decoration")
    @SerialName("avatar_decoration_data")
    val avatarDecoration: String?,
)

class Account(private val user: UserInfo?) {
    companion object {
        const val DISCORD_CDN = "https://cdn.discordapp.com/"
    }

    val loggedIn
        get() = user != null
    val id
        get() = user?.id
    val name
        get() = user?.globalName ?: "Anonymous"

    val avatar
        get() = user?.let {
            if (user.avatar != null) "${DISCORD_CDN}avatars/${user.id}/${user.avatar}.png"
            else "${DISCORD_CDN}embed/avatars/${(user.id shr 22) % 6}.png"
        }

    override fun toString(): String {
        return "Account($name)"
    }

    override fun equals(other: Any?) = other is Account && (other.loggedIn && loggedIn && other.id == id)
    override fun hashCode() = id.hashCode()
}

fun ApplicationCall.getAccount() = Account(sessions.get<UserSession>()?.userInfo)