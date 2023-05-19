package net.sunaba.ktor.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*



/**
 * App Engine task requests always contain the following headers.
 *
 * - X-AppEngine-QueueName
 * - X-AppEngine-TaskName
 * - X-AppEngine-TaskRetryCount
 * - X-AppEngine-TaskExecutionCount
 *
 * @see https://cloud.google.com/tasks/docs/creating-appengine-handlers
 * @param name
 */
fun AuthenticationConfig.appengineTaskQueue(name: String = "tq") {
    provider(name) {
        authenticate { context ->
            if (context.call.request.headers.contains("X-AppEngine-QueueName")) {
                context.principal(TaskQueuePrincipal(context.call.request))
            }
            else {
                context.challenge("AppEngineTaskQueue", AuthenticationFailedCause.NoCredentials) { c, call ->
                    call.respond(HttpStatusCode.Unauthorized)
                    c.complete()
                }
            }
        }
    }
}

class TaskQueuePrincipal internal constructor(request:ApplicationRequest) : Principal {
    val queueName:String by lazy { request.header("X-AppEngine-QueueName")!! }
    val taskName:String by lazy { request.header("X-AppEngine-TaskName")!! }
    val taskRetryCount:Int by lazy { request.header("X-AppEngine-TaskRetryCount")!!.toInt() }
    val taskExecutionCount:Int by lazy { request.header("X-AppEngine-TaskExecutionCount")!!.toInt() }

    val taskPreviousResponse:Int? by lazy { request.header("X-AppEngine-TaskPreviousResponse")?.toInt() }
    val taskRetryReason:String? by lazy { request.header("X-AppEngine-TaskRetryReason") }
    val failFast:Boolean by lazy { "true".equals(request.header("X-AppEngine-FailFast"), ignoreCase = true) }

    override fun toString(): String {
        return "TaskQueuePrincipal(queueName='$queueName', taskName='$taskName', taskRetryCount=$taskRetryCount" +
                ", taskExecutionCount=$taskExecutionCount, taskPreviousResponse=$taskPreviousResponse, taskRetryReason=$taskRetryReason, failFast=$failFast)"
    }
}
