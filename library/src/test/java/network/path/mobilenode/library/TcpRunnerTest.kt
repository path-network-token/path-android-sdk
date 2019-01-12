package network.path.mobilenode.library

import network.path.mobilenode.library.data.runner.Status
import network.path.mobilenode.library.data.runner.TcpRunner
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.charset.Charset
import javax.net.SocketFactory

class TcpRunnerTest {
    companion object {
        private const val DUMMY_SUCCESS_URL = "0.0.0.0"
        private const val DUMMY_PORT = 1234
        private const val DUMMY_RESPONSE = "DUMMY_RESPONSE"
    }

    private lateinit var socket: MockSocket
    private lateinit var runner: TcpRunner

    private class MockSocket : Socket() {
        var passedEndpoint: SocketAddress? = null
            private set

        var passedTimeout: Int = 0
            private set

        var passedSoTimeout: Int = 0
            private set

        override fun connect(endpoint: SocketAddress, timeout: Int) {
            passedEndpoint = endpoint
            passedTimeout = timeout
        }

        override fun setSoTimeout(timeout: Int) {
            passedSoTimeout = timeout
        }

        override fun getOutputStream(): OutputStream {
            return Mockito.mock(OutputStream::class.java)
        }

        override fun getInputStream(): InputStream {
            return ByteArrayInputStream(DUMMY_RESPONSE.toByteArray(Charset.defaultCharset()))
        }
    }

    @BeforeEach
    fun init() {
        socket = MockSocket()
        val factory = Mockito.mock(SocketFactory::class.java)
        Mockito.`when`(factory.createSocket()).thenReturn(socket)

        runner = TcpRunner(factory)
    }

    @Test
    fun testSuccess() {
        val request = JobRequest(
            protocol = "tcp",
            endpointAddress = DUMMY_SUCCESS_URL,
            jobUuid = RunnerTest.DUMMY_UUID,
            executionUuid = RunnerTest.DUMMY_UUID
        )
        val result = runner.runJob(request, MockTimeSource)

        // Socket
        Assertions.assertEquals(socket.passedEndpoint, InetSocketAddress(DUMMY_SUCCESS_URL, Constants.DEFAULT_TCP_PORT))
        Assertions.assertEquals(socket.passedTimeout, Constants.JOB_TIMEOUT_MILLIS.toInt())
        Assertions.assertEquals(socket.passedSoTimeout, 0)

        // Result
        Assertions.assertEquals(result.checkType, JobType.TCP)
        Assertions.assertEquals(result.executionUuid, RunnerTest.DUMMY_UUID)
        Assertions.assertNotEquals(result.status, Status.UNKNOWN)
    }

    @Test
    fun testPayload() {
        val request = JobRequest(
            protocol = "tcp",
            endpointAddress = DUMMY_SUCCESS_URL,
            endpointPort = DUMMY_PORT,
            payload = "Payload",
            jobUuid = RunnerTest.DUMMY_UUID,
            executionUuid = RunnerTest.DUMMY_UUID
        )
        val result = runner.runJob(request, MockTimeSource)

        // Socket
        Assertions.assertEquals(socket.passedEndpoint, InetSocketAddress(DUMMY_SUCCESS_URL, DUMMY_PORT))
        Assertions.assertEquals(socket.passedTimeout, Constants.JOB_TIMEOUT_MILLIS.toInt())
        Assertions.assertEquals(socket.passedSoTimeout, Constants.TCP_UDP_READ_WRITE_TIMEOUT_MILLIS.toInt())

        // Result
        Assertions.assertEquals(result.checkType, JobType.TCP)
        Assertions.assertEquals(result.executionUuid, RunnerTest.DUMMY_UUID)
        Assertions.assertEquals(result.responseBody, DUMMY_RESPONSE)
        Assertions.assertNotEquals(result.status, Status.UNKNOWN)
    }
}
