package at.aau.pulverfass.server

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close

private const val DEFAULT_HOST = "0.0.0.0"
private const val DEFAULT_PORT = 8080
const val TEXT_FRAME_REJECTION_MESSAGE = "Text frames are not supported on /ws."

fun main() {
    createServer().start(wait = true)
}

fun createServer(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
): ApplicationEngine =
    embeddedServer(
        factory = Netty,
        host = host,
        port = port,
        module = Application::module,
    )

fun Application.module() {
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 15_000
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/ws") {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> {
                        // hier dann Events werfen
                    }

                    is Frame.Text -> {
                        close(
                            CloseReason(
                                CloseReason.Codes.CANNOT_ACCEPT,
                                TEXT_FRAME_REJECTION_MESSAGE,
                            ),
                        )
                        break
                    }

                    is Frame.Close -> break
                    else -> Unit
                }
            }
        }
    }
}
