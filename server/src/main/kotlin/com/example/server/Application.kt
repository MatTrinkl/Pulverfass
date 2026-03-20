package com.example.server

import com.example.shared.ApiRoutes
import com.example.shared.Constants
import com.example.shared.HealthResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = Constants.DEFAULT_PORT) {
        configurePlugins()
        configureRoutes()
    }.start(wait = true)
}

fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.configureRoutes() {
    routing {
        get(ApiRoutes.HEALTH) {
            call.respond(HealthResponse(status = "UP"))
        }
    }
}
