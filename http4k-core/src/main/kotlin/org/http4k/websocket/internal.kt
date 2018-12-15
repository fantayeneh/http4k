package org.http4k.websocket

import org.http4k.core.Request
import org.http4k.websocket.WsStatus.Companion.NORMAL

abstract class PushPullAdaptingWebSocket(override val upgradeRequest: Request) : Websocket {

    private val errorHandlers = mutableListOf<suspend (Throwable) -> Unit>()
    private val closeHandlers = mutableListOf<suspend (WsStatus) -> Unit>()
    private val messageHandlers = mutableListOf<suspend (WsMessage) -> Unit>()

    suspend fun triggerError(throwable: Throwable) = errorHandlers.forEach { it(throwable) }
    suspend fun triggerClose(status: WsStatus = NORMAL) = closeHandlers.forEach { it(status) }
    suspend fun triggerMessage(message: WsMessage) = messageHandlers.forEach { it(message) }

    override suspend fun onError(fn: suspend (Throwable) -> Unit) {
        errorHandlers.add(fn)
    }

    override suspend fun onClose(fn: suspend (WsStatus) -> Unit) {
        closeHandlers.add(fn)
    }

    override suspend fun onMessage(fn: suspend (WsMessage) -> Unit) {
        messageHandlers.add(fn)
    }
}