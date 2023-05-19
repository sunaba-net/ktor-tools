package net.sunaba.ktor.auth

import com.auth0.jwk.GuavaCachedJwkProvider
import com.auth0.jwk.UrlJwkProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.auth.*
import java.net.URL

fun configureGoogleSignIn(clientId:String, configure:GoogleSignInConfig.()->Unit) = GoogleSignInConfig(clientId).apply(configure)

class GoogleSignInConfig internal constructor(val clientId: String) {
    var authName: String? = "google"
    var loginPath: String = "/__login"
    var onLoginAction: ApplicationCall.(jwt: JWTPrincipal) -> Boolean = { true }
}

fun AuthenticationConfig.googleSignIn(config: GoogleSignInConfig) {
    val audience = config.clientId
    val jwkProvider = UrlJwkProvider(URL("https://www.googleapis.com/oauth2/v3/certs"))
    val cachedJwkProvider = GuavaCachedJwkProvider(jwkProvider)
    jwt(config.authName) {
        verifier(cachedJwkProvider) {
            withIssuer("accounts.google.com", "https://accounts.google.com")
            withAudience(audience)
        }
        validate { jwtCredential -> JWTPrincipal(jwtCredential.payload) }
    }
}

fun Routing.installSimpleLogin(config: GoogleSignInConfig) {

    get(config.loginPath) {
        call.respondText("""<html lang="en">
<head>
    <meta content="width=device-width, initial-scale=1, minimum-scale=1" name="viewport">
</head>
<body>
<script src="https://accounts.google.com/gsi/client" async defer></script>
<div id="g_id_onload"
     data-client_id="${config.clientId}"
     data-context="signin"
     data-ux_mode="popup"
     data-callback="onSignIn"
     data-auto_select="true"
     data-itp_support="true">
</div>

<div class="g_id_signin"
     data-type="standard"
     data-shape="rectangular"
     data-theme="outline"
     data-text="signin_with"
     data-size="large"
     data-logo_alignment="left">
</div>
<script>
  function onSignIn(response) {
     // decodeJwtResponse() is a custom function defined by you
     // to decode the credential response.
             // The ID token you need to pass to your backend:
        var id_token = response.credential
        var xhr = new XMLHttpRequest();
        xhr.open('POST', '${config.loginPath}');
        xhr.setRequestHeader('Authorization', 'Bearer ' + id_token)
        xhr.onload = function () {
            if (xhr.status == 200) {
                var searchParams = new URLSearchParams(window.location.search)
                var continueUrl = searchParams.get("continue")
                if (continueUrl) {
                    window.location = continueUrl
                }
            }
        };
        xhr.send();
  }
</script>
</body>
</html>""", ContentType.Text.Html)
    }


    authenticate(config.authName) {
        post(config.loginPath) {
            //Verify the Google ID token on your server side
            //https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
            val jwtPrincipal = call.authentication.principal<JWTPrincipal>()!!
            config.onLoginAction.invoke(this.call, jwtPrincipal)
            call.respond(HttpStatusCode.OK)
        }
    }

}

