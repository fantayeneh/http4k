package guide.example._1_building_a_walking_skeleton

import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.coroutines.runBlocking
import org.http4k.client.OkHttp
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EndToEndTest {
    private val client = OkHttp()
    private val server = MyMathServer(0)

    @BeforeEach
    fun setup(): Unit {
        server.start()
    }

    @AfterEach
    fun teardown(): Unit {
        server.stop()
    }

    @Test
    fun `responds to ping`() = runBlocking {
        assertThat(client(Request(GET, "http://localhost:${server.port()}/ping")), hasStatus(OK))
        Unit
    }
}
