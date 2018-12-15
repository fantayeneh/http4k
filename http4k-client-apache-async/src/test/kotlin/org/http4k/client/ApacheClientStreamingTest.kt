package org.http4k.client

import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.impl.nio.reactor.IOReactorConfig
import org.http4k.core.BodyMode
import org.http4k.server.SunHttp

class ApacheClientStreamingTest : HttpClientContract({ SunHttp(it) }, ApacheAsyncClient(responseBodyMode = BodyMode.Stream),
    ApacheAsyncClient(HttpAsyncClients.custom()
        .setDefaultIOReactorConfig(IOReactorConfig.custom()
            .setSoTimeout(100)
            .build()).build().apply { start() },
        requestBodyMode = BodyMode.Stream,
        responseBodyMode = BodyMode.Stream
    ))