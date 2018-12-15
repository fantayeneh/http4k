package org.http4k.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod.permitsRequestBody
import org.http4k.core.BodyMode
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.CLIENT_TIMEOUT
import org.http4k.core.Status.Companion.CONNECTION_REFUSED
import org.http4k.core.Status.Companion.SERVICE_UNAVAILABLE
import org.http4k.core.Status.Companion.UNKNOWN_HOST
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OkHttp {
    operator fun invoke(client: OkHttpClient = defaultOkHttpClient(), bodyMode: BodyMode = BodyMode.Memory): DualSyncAsyncHttpHandler =
        object : DualSyncAsyncHttpHandler {
            override suspend fun invoke(request: Request): Response =
                try {
                    val callContext = CompletableDeferred<Unit>()
                    client.executeWithCoroutine(request.asOkHttp()).let {
                        it.body.apply {
                            callContext[Job]?.invokeOnCompletion { this?.close() }
                        }
                        withContext(callContext) {
                            it.asHttp4k(bodyMode)
                        }
                    }
                } catch (e: ConnectException) {
                    Response(CONNECTION_REFUSED.toClientStatus(e))
                } catch (e: UnknownHostException) {
                    Response(UNKNOWN_HOST.toClientStatus(e))
                } catch (e: SocketTimeoutException) {
                    Response(CLIENT_TIMEOUT.toClientStatus(e))
                }

            override operator fun invoke(request: Request, fn: (Response) -> Unit) =
                client.newCall(request.asOkHttp()).enqueue(Http4kCallback(bodyMode, fn))
        }

    private suspend fun OkHttpClient.executeWithCoroutine(request: okhttp3.Request): okhttp3.Response =
        suspendCancellableCoroutine {
            val call = newCall(request)
            val callback = object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!call.isCanceled()) it.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    if (!call.isCanceled()) it.resume(response)
                }
            }
            call.enqueue(callback)
            it.invokeOnCancellation { call.cancel() }
        }

    private class Http4kCallback(private val bodyMode: BodyMode, private val fn: (Response) -> Unit) : Callback {
        override fun onFailure(call: Call, e: IOException) = fn(Response(when (e) {
            is SocketTimeoutException -> CLIENT_TIMEOUT
            else -> SERVICE_UNAVAILABLE
        }.description("Client Error: caused by ${e.localizedMessage}")))

        override fun onResponse(call: Call, response: okhttp3.Response) = fn(response.asHttp4k(bodyMode))
    }

    private fun defaultOkHttpClient() = OkHttpClient.Builder()
        .followRedirects(false)
        .build()
}

private fun Request.asOkHttp(): okhttp3.Request = headers.fold(okhttp3.Request.Builder()
    .url(uri.toString())
    .method(method.toString(), requestBody())) { memo, (first, second) ->
    val notNullValue = second ?: ""
    memo.addHeader(first, notNullValue)
}.build()

private fun Request.requestBody() =
    if (permitsRequestBody(method.toString())) body.payload.array().toRequestBody()
    else null

private fun okhttp3.Response.asHttp4k(bodyMode: BodyMode): Response {
    val init = Response(Status(code, ""))
    val headers = headers.toMultimap().flatMap { it.value.map { hValue -> it.key to hValue } }

    return (body?.let { init.body(bodyMode(it.byteStream())) } ?: init).headers(headers)
}