package at.aau.pulverfass.server

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class WebSocketServerTest {
    @Test
    fun `createServer creates startable engine that can be stopped cleanly`() {
        val server = createServer(host = "127.0.0.1", port = 0)

        try {
            server.start(wait = false)
            Thread.sleep(100)
        } finally {
            server.stop(1_000, 1_000)
        }
    }

    @Test
    fun `connection on ws can be established`() =
        testApplication {
            application {
                module()
            }

            val client =
                createClient {
                    install(WebSockets)
                }

            client.webSocket("/ws") {
                assertNotNull(this)
            }
        }

    @Test
    fun `connection can be closed cleanly`() =
        testApplication {
            application {
                module()
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val session = client.webSocketSession("/ws")

            session.close()

            val closeReason = session.closeReason.await()
            assertNotNull(closeReason)
            assertEquals(CloseReason.Codes.NORMAL, closeReason?.knownReason)
        }

    @Test
    fun `text frame is rejected with defined close reason`() =
        testApplication {
            application {
                module()
            }

            val client =
                createClient {
                    install(WebSockets)
                }
            val session = client.webSocketSession("/ws")

            session.send(Frame.Text("hello"))

            val closeReason = session.closeReason.await()
            assertNotNull(closeReason)
            assertEquals(
                CloseReason.Codes.byCode(WebSocketPolicy.TEXT_FRAME_CLOSE_CODE),
                closeReason?.knownReason,
            )
            assertEquals(WebSocketPolicy.TEXT_FRAMES_NOT_SUPPORTED, closeReason?.message)
        }
}
