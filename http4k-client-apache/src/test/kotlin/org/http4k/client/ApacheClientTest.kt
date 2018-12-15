package org.http4k.client

import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.coroutines.runBlocking
import org.apache.http.concurrent.FutureCallback
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.impl.nio.reactor.IOReactorConfig
import org.apache.http.nio.protocol.HttpAsyncRequestProducer
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer
import org.apache.http.protocol.HttpContext
import org.http4k.core.BodyMode.Stream
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CLIENT_TIMEOUT
import org.http4k.hamkrest.hasStatus
import org.http4k.server.SunHttp
import org.junit.jupiter.api.Test
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

class ApacheClientTest : HttpClientContract({ SunHttp(it) }, ApacheClient(),
    ApacheClient(HttpAsyncClients.custom()
        .setDefaultIOReactorConfig(IOReactorConfig.custom()
            .setSoTimeout(100)
            .build()).build().apply { start() }
        , responseBodyMode = Stream)) {

    @Test
    fun `connect timeout is handled`() = runBlocking {
        assertThat(ApacheClient(object : CloseableHttpAsyncClient() {
            override fun isRunning(): Boolean = true

            override fun start() = Unit

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> execute(requestProducer: HttpAsyncRequestProducer, responseConsumer: HttpAsyncResponseConsumer<T>, context: HttpContext, callback: FutureCallback<T>): Future<T> {
                callback.failed(ConnectTimeoutException())
                return FutureTask<T> { error("task should have finished with ConnectionTimeoutException") }
            }

            override fun close() {
            }

        })(Request(GET, "http://localhost:8000")), hasStatus(CLIENT_TIMEOUT))
    }

}
