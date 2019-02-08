package org.http4k.server

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.cloudnative.env.Port
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.junit.jupiter.api.Test

class CloudNativeExtTest {

    @Test
    fun `can HttpHandler to a server`() {
        assertThat(HttpHandler { Response(Status.OK) }.asServer(::SunHttp, Port(8000)).port(), equalTo(8000))
    }
}