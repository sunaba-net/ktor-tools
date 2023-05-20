package net.sunaba.ktor.auth

import com.auth0.jwk.GuavaCachedJwkProvider
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.Verification
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import java.net.URL
import java.security.interfaces.RSAPublicKey

class GoogleJwtAuthConfig internal constructor() {
    internal var challenge: JWTAuthChallengeFunction = { _,_ -> }
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

fun AuthenticationConfig.googleJwt(
    name: String? = null,
    clientId: String,
    configure: GoogleJwtAuthConfig.() -> Unit = {}
) {
    val jwkProvider = UrlJwkProvider(URL("https://www.googleapis.com/oauth2/v3/certs"))
    val cachedJwkProvider = GuavaCachedJwkProvider(jwkProvider)

    val config: GoogleJwtAuthConfig = GoogleJwtAuthConfig().apply(configure)

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


fun AuthenticationConfig.googlePost(
    name: String? = null,
    clientId: String,
    configure: GooglePostAuthenticationProvider.Config.() -> Unit = {}
) {
    val c = GooglePostAuthenticationProvider.Config(name, clientId).apply(configure)
    register(GooglePostAuthenticationProvider(c))
}

internal val GoogleSignInAuthKey: Any = "GoogleSignInAuth"

class GooglePostAuthenticationProvider internal constructor(val config: Config) : AuthenticationProvider(config) {
    private val cachedJwkProvider =
        GuavaCachedJwkProvider(UrlJwkProvider(URL("https://www.googleapis.com/oauth2/v3/certs")))

    class Config(name: String? = null, val clientId: String) : AuthenticationProvider.Config(name) {
        internal var challenge: AuthenticationContext.() -> Unit = { }

        internal var authenticationFunction: AuthenticationFunction<JWTCredential> =
            { jwtCredential -> JWTPrincipal(jwtCredential.payload) }

        internal var additionalVerifier: Verification.() -> Unit = {}

        /**
         * Specifies what to send back if JWT authentication fails.
         */
        fun challenge(block: AuthenticationContext.() -> Unit) {
            challenge = block
        }

        /**
         * Allows you to perform additional validations on the JWT payload.
         * @return a principal (usually an instance of [JWTPrincipal]) or `null`
         */
        fun validate(validate: suspend ApplicationCall.(JWTCredential) -> Principal?) {
            authenticationFunction = validate
        }

        fun additionalVerifier(configure: Verification.() -> Unit = {}) {
            this.additionalVerifier = configure
        }
    }

    override suspend fun onAuthenticate(it: AuthenticationContext) {
        //https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
        var params = it.call.receiveParameters()
        val csrfToken = it.call.request.cookies["g_csrf_token"]
        val csrfTokenBody = params["g_csrf_token"]
        if (csrfToken == null) {
            it.error(GoogleSignInAuthKey, AuthenticationFailedCause.Error("csrfToken not found."))
            return
        }
        if (csrfTokenBody == null) {
            it.error(GoogleSignInAuthKey, AuthenticationFailedCause.Error("csrfTokenBody not found."))
            return
        }
        if (csrfToken != csrfTokenBody) {
            it.error(GoogleSignInAuthKey, AuthenticationFailedCause.Error("invalid csrfToken."))
            return
        }
        val cred = params["credential"]
        val jwt = JWT.decode(cred)
        val alg = Algorithm.RSA256(cachedJwkProvider.get(jwt.keyId).publicKey as RSAPublicKey)
        val verification = JWT.require(alg).withAudience(config.clientId)
            .withIssuer("accounts.google.com", "https://accounts.google.com")
        config.additionalVerifier.invoke(verification)
        try {
            val decodedJwt = verification.build().verify(jwt)
            it.principal(JWTPrincipal(decodedJwt))
            config.authenticationFunction.invoke(it.call, JWTCredential(decodedJwt))
        } catch (ex: JWTVerificationException) {
            config.challenge.invoke(it)
        }
    }
}

