package org.http4k.stream

import org.http4k.client.ApacheClient
import org.http4k.core.BodyMode.Stream
import org.http4k.server.Undertow
import org.http4k.streaming.StreamingContract
import org.junit.jupiter.api.Disabled

class UndertowStreamingTest : StreamingContract() {
    override fun serverConfig() = Undertow(0)

    override fun createClient() =
        ApacheClient(requestBodyMode = Stream, responseBodyMode = Stream)

    @Disabled("New ApacheClient is missing streaming support")
    override fun `can stream response`() {
    }
}