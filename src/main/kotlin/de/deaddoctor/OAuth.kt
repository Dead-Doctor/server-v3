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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun Application.installOAuth() {
    authentication {
        oauth("discord") {
            skipWhen { call ->
                call.user is AccountUser || call.parameters["error"] != null
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
    fun url(state: String?) = state.orEmpty().drop(16).decodeURLPart().ifBlank { "/" }
}

@Serializable
data class DiscordInfo(
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

@Serializable
data class UserSession(
    val id: String? = null,
    val info: DiscordInfo? = null
) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun generate() = UserSession(Uuid.random().toString(), null)

        suspend fun generate(principal: OAuthAccessTokenResponse.OAuth2): UserSession {
            val info = httpClient.get("https://discord.com/api/users/@me") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
                }
            }.body<DiscordInfo>()
            return UserSession(null, info)
        }
    }
}

open class User

open class TrackedUser(val id: String) : User() {
    override fun toString(): String {
        return "TrackedUser($id)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackedUser) return false
        return id == other.id
    }

    override fun hashCode() = id.hashCode()
}

@OptIn(ExperimentalStdlibApi::class)
class AccountUser(private val info: DiscordInfo) : TrackedUser(info.id.toHexString()) {

    companion object {
        const val DISCORD_CDN = "https://cdn.discordapp.com/"
        const val ADMIN_USER = 621027101645996053L
    }

    val name
        get() = info.globalName ?: info.username

    val avatar: String
        get() = if (info.avatar != null)
            "${DISCORD_CDN}avatars/${info.id}/${info.avatar}.png"
        else
            "${DISCORD_CDN}embed/avatars/${(info.id shr 22) % 6}.png"

    val admin = info.id == ADMIN_USER
}

val ApplicationCall.trackedUser: TrackedUser
    get() {
        val session = sessions.getOrSet { UserSession.generate() }
        val info = session.info ?: return TrackedUser(session.id!!)
        return AccountUser(info)
    }

val ApplicationCall.user: User
    get() {
        val session = sessions.get<UserSession>() ?: return User()
        val info = session.info ?: return TrackedUser(session.id!!)
        return AccountUser(info)
    }