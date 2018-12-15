package org.http4k.testing

import kotlinx.coroutines.runBlocking
import org.http4k.core.Request
import org.http4k.websocket.PolyHandler
import org.http4k.websocket.PushPullAdaptingWebSocket
import org.http4k.websocket.WsClient
import org.http4k.websocket.WsConsumer
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsStatus
import org.http4k.websocket.WsStatus.Companion.NORMAL
import java.util.*

data class ClosedWebsocket(val status: WsStatus = NORMAL) : Exception()

/**
 * A class that is used for *offline* testing of a routed Websocket, without starting up a Server. Calls
 * are routed synchronously to the receiving Websocket, and error are propagated to the caller.
 */
class TestWsClient internal constructor(consumer: WsConsumer, request: Request) : WsClient {

    private val socket = QueueSocket(request, consumer)

    override fun received() = generateSequence { socket.queue.remove()()!! }

    private class QueueSocket(request: Request, consumer: WsConsumer) : PushPullAdaptingWebSocket(request) {
        val queue = ArrayDeque<() -> WsMessage?>()

        init {
            initConsumer(consumer)
        }

        private fun initConsumer(consumer: WsConsumer) {
            runBlocking {
                consumer(this@QueueSocket)
                onClose {
                    queue.add { throw ClosedWebsocket(it) }
                }
            }
        }

        override suspend fun send(message: WsMessage) {
            queue.add { message }
        }

        override suspend fun close(status: WsStatus) {
            queue.add { throw ClosedWebsocket(status) }
        }
    }

    /**
     * Push an error to the Websocket
     */
    fun error(throwable: Throwable) = runBlocking { socket.triggerError(throwable) }

    override fun close(status: WsStatus) = runBlocking { socket.triggerClose(status) }

    override fun send(message: WsMessage) = runBlocking { socket.triggerMessage(message) }
}

fun WsHandler.testWsClient(request: Request): TestWsClient? = runBlocking { invoke(request)?.let { TestWsClient(it, request) } }
fun PolyHandler.testWsClient(request: Request): TestWsClient? = ws.testWsClient(request)

