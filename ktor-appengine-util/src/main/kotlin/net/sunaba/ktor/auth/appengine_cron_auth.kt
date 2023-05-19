package net.sunaba.ktor.auth

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

fun AuthenticationConfig.appengineCron(name: String = "cron") {
    provider(name) {
        authenticate { context ->
            if (!context.call.request.headers.contains("X-AppEngine-Cron")) {
                context.challenge("AppEngineCron", AuthenticationFailedCause.NoCredentials) { c, call ->
                    call.respond(HttpStatusCode.Unauthorized)
                    c.complete()
                }
            }
        }
    }
}