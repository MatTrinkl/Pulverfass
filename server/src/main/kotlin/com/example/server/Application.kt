package com.example.server

import com.example.shared.ApiRoutes
import com.example.shared.Constants
import com.example.shared.HealthResponse
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
