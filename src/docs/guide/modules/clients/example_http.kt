package guide.modules.clients

import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.http4k.client.ApacheClient
import org.http4k.core.BodyMode
import org.http4k.core.Method
import org.http4k.core.Request

suspend fun main() {

    // standard client
    val client = ApacheClient()
    val request = Request(Method.GET, "http://httpbin.org/get").query("location", "John Doe")
    val response = client(request)
    println("SYNC")
    println(response.status)
    println(response.bodyString())

    // streaming client
    val streamingClient = ApacheClient(responseBodyMode = BodyMode.Stream)
    val streamingRequest = Request(Method.GET, "http://httpbin.org/stream/100")
    println("STREAM")
    println(streamingClient(streamingRequest).bodyString())

    // custom configured client
    val customClient = ApacheClient(
        client =
        HttpAsyncClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .build()).build().apply { start() }
    )
}

