package net.sunaba.plugins

import com.google.cloud.tasks.v2.AppEngineHttpRequest
import com.google.cloud.tasks.v2.AppEngineRouting
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.HttpMethod
import com.google.cloud.tasks.v2.Queue
import com.google.cloud.tasks.v2.QueueName
import com.google.cloud.tasks.v2.Task
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.sunaba.ktor.auth.*
import net.sunaba.ktor.util.AppEngine
import java.lang.Exception

fun Application.configureRouting() {

    val config = configureGoogleSignIn("") {}

    install(Authentication) {
        googleSignIn(config)
        appegnineCron("cron")
        appengineTaskQueue("tq")
    }

    routing {
        installSimpleLogin(config)

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


//        authenticate() {
//            get("/need_login") {
//                call.respondText("Hello World!")
//            }
//        }
    }
}
