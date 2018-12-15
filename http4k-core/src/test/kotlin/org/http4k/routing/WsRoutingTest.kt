package org.http4k.routing

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import kotlinx.coroutines.runBlocking
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsStatus
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

class WsRoutingTest {

    @Test
    fun `simple find with path matching`() = runBlocking {

        val request = AtomicReference<Request>()

        val ws = websockets(
            "/path1" bind websockets(
                "/{name}" bind { ws: Websocket ->
                    request.set(ws.upgradeRequest)
                }
            ))

        val sentRequestWithNoUriTemplateHeader = Request(GET, "/path1/correct")
        val a = ws(sentRequestWithNoUriTemplateHeader)
        a!!(object : Websocket {
            override val upgradeRequest: Request = sentRequestWithNoUriTemplateHeader

            override suspend fun send(message: WsMessage) {
            }

            override suspend fun close(status: WsStatus) {
            }

            override suspend fun onError(fn: suspend (Throwable) -> Unit) {
            }

            override suspend fun onClose(fn: suspend (WsStatus) -> Unit) {
            }

            override suspend fun onMessage(fn: suspend (WsMessage) -> Unit) {
            }

        })
        assertThat(request.get().path("name"), equalTo("correct"))
    }

    @Test
    fun `not found`() = runBlocking {
        val websockets = websockets()

        assertThat(websockets(Request(GET, "/path1/index.html")), absent())
    }
}