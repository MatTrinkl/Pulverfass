package at.aau.pulverfass.server

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WebSocketServerTest {
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

            val closeReason = assertNotNull(session.closeReason.await())
            assertEquals(CloseReason.Codes.NORMAL, closeReason.knownReason)
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

            val closeReason = assertNotNull(session.closeReason.await())
            assertEquals(CloseReason.Codes.CANNOT_ACCEPT, closeReason.knownReason)
            assertEquals(TEXT_FRAME_REJECTION_MESSAGE, closeReason.message)
        }
}
