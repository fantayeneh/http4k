package org.http4k.streaming

import org.http4k.client.ApacheClient
import org.http4k.core.BodyMode.Stream
import org.http4k.server.SunHttp
import org.junit.jupiter.api.Disabled

class SunHttpStreamingTest : StreamingContract() {
    override fun serverConfig() = SunHttp(0)

    override fun createClient() = ApacheClient(requestBodyMode = Stream, responseBodyMode = Stream)

    @Disabled("New ApacheClient is missing streaming support")
    override fun `can stream response`() {
    }
}