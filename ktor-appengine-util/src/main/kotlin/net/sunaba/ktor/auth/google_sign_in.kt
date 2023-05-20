package net.sunaba.ktor.auth

import com.auth0.jwk.GuavaCachedJwkProvider
import com.auth0.jwk.UrlJwkProvider
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.net.URL

class GoogleSignInConfig internal constructor() {
    internal var challenge: JWTAuthChallengeFunction = { s: String, s1: String -> }
    internal var authenticationFunction: AuthenticationFunction<JWTCredential> =
        { jwtCredential -> JWTPrincipal(jwtCredential.payload) }
    internal var additionalVerifier: JWTConfigureFunction = {}

    /**
     * Specifies what to send back if JWT authentication fails.
     */
    fun challenge(block: JWTAuthChallengeFunction) {
        challenge = block
    }

    /**
     * Allows you to perform additional validations on the JWT payload.
     * @return a principal (usually an instance of [JWTPrincipal]) or `null`
     */
    fun validate(validate: suspend ApplicationCall.(JWTCredential) -> Principal?) {
        authenticationFunction = validate
    }

    fun additionalVerifier(configure: JWTConfigureFunction = {}) {
        this.additionalVerifier = configure
    }
}

fun AuthenticationConfig.googleSignIn(name: String?=null, clientId: String, configure: GoogleSignInConfig.() -> Unit = {}) {
    val jwkProvider = UrlJwkProvider(URL("https://www.googleapis.com/oauth2/v3/certs"))
    val cachedJwkProvider = GuavaCachedJwkProvider(jwkProvider)

    val config: GoogleSignInConfig = GoogleSignInConfig().apply(configure)

    jwt(name) {
        verifier(cachedJwkProvider) {
            withIssuer("accounts.google.com", "https://accounts.google.com")
            withAudience(clientId)
            config.additionalVerifier.invoke(this)
        }
        validate(config.authenticationFunction)
        challenge(config.challenge)
    }
}
