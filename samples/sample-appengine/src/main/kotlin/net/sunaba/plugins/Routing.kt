package net.sunaba.plugins

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.tasks.v2.*
import com.google.cloud.tasks.v2.HttpMethod
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.sunaba.ktor.auth.TaskQueuePrincipal
import net.sunaba.ktor.auth.appengineCron
import net.sunaba.ktor.auth.appengineTaskQueue
import net.sunaba.ktor.auth.googlePost
import net.sunaba.ktor.util.AppEngine

fun Application.configureRouting() {

    GoogleCredentials.getApplicationDefault()

    val clientId = System.getProperty("sample.clientId")

    install(Authentication) {
        googlePost("google", clientId)
        appengineCron("cron")
        appengineTaskQueue("tq")
    }

    routing {

        get("/") {
            call.respondText("Hello World!")
        }

        get("/_test_tq") {
            CloudTasksClient.create().use {client->
                val queueName = QueueName.of(AppEngine.Env.GOOGLE_CLOUD_PROJECT.value, AppEngine.currentQueueLocation, "default")
                val requestBuilder = AppEngineHttpRequest.newBuilder()
                    .setRelativeUri("/run_tq")
                    .setHttpMethod(HttpMethod.POST)
                val taskBuilder = Task.newBuilder()
                    .setAppEngineHttpRequest(requestBuilder.build())
                client.createTask(queueName, taskBuilder.build())
            }
        }

        authenticate("cron") {
            get("/test_cron") {
                call.respondText("Hello Cron!")
            }
        }

        authenticate("tq") {
            post("/run_tq") {
                val tqPrincipal = call.principal<TaskQueuePrincipal>()!!
                println(tqPrincipal)
                if (tqPrincipal.taskRetryCount < 2) {
                    throw Exception("retry: ${tqPrincipal.taskRetryCount}")
                }
                call.respondText("Hello Tq!")
            }
        }


        googleSignInRoutes("/login", clientId, "google")

        authenticate("google") {
            get("/need_login") {
                call.respondText("Hello World!")
            }
        }
    }
}


fun Routing.googleSignInRoutes(loginPath: String, clientId: String, authProviderName: String, ) {

    get(loginPath) {
        call.respondText(
            """<html lang="en">
<head>
    <meta content="width=device-width, initial-scale=1, minimum-scale=1" name="viewport">
</head>
<body>
<script src="https://accounts.google.com/gsi/client" async defer></script>
<div id="g_id_onload"
     data-client_id="${clientId}"
     data-context="signin"
     data-ux_mode="redirect"
     data-login_uri="http://localhost:8080/login"
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
</body>
</html>""", ContentType.Text.Html
        )
    }

    authenticate(authProviderName) {
        post(loginPath) {
//            val jwtPrincipal = call.authentication.principal<JWTPrincipal>()!!
            call.respondText { call.principal<JWTPrincipal>()!!.payload.toString() }
        }
    }

}


