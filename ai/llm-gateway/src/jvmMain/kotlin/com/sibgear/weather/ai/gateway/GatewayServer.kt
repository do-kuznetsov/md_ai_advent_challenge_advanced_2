package com.sibgear.weather.ai.gateway

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondResource
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.http.content.staticResources
import kotlinx.serialization.json.Json
import java.util.UUID

internal fun startGatewayServer(
    service: GatewayService,
    host: String,
    port: Int,
    wait: Boolean,
) = embeddedServer(CIO, host = host, port = port) {
    gatewayModule(service)
}.start(wait = wait)

internal fun Application.gatewayModule(
    service: GatewayService,
    rateLimiter: SlidingWindowRateLimiter = SlidingWindowRateLimiter(),
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = false })
    }
    install(StatusPages) {
        exception<GatewayFailure> { call, failure ->
            call.respond(
                HttpStatusCode.fromValue(failure.httpStatus),
                ApiError(failure.requestId, failure.code, failure.message),
            )
        }
        exception<Throwable> { call, failure ->
            val requestId = UUID.randomUUID().toString()
            System.err.println("request_id=$requestId status=UNHANDLED type=${failure::class.simpleName}")
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(requestId, "INTERNAL_ERROR", "Unexpected gateway error"),
            )
        }
    }
    routing {
        get("/health") {
            call.respond(HealthResponse("ok"))
        }
        route("/v1") {
            post("/chat") {
                val requestId = UUID.randomUUID().toString()
                if (!rateLimiter.tryAcquire(call.request.local.remoteHost)) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(requestId, "RATE_LIMITED", "Maximum 10 requests per 60 seconds"),
                    )
                    return@post
                }
                val request = runCatching { call.receive<ChatRequest>() }.getOrElse {
                    throw GatewayFailure(400, "INVALID_REQUEST", "Malformed JSON request", requestId)
                }
                call.respond(service.chat(request))
            }
            get("/chats") {
                call.respond(service.chats())
            }
            get("/chats/{id}") {
                val chatId = call.parameters["id"]
                    ?: throw GatewayFailure(400, "CHAT_ID_REQUIRED", "Chat id required")
                call.respond(service.chat(chatId))
            }
            delete("/chats/{id}") {
                val chatId = call.parameters["id"]
                    ?: throw GatewayFailure(400, "CHAT_ID_REQUIRED", "Chat id required")
                call.respond(service.delete(chatId))
            }
        }
        get("/") {
            call.respondResource("static/index.html")
        }
        get("/debug") {
            call.respondResource("static/index.html")
        }
        staticResources("/", "static")
    }
}
