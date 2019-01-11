package network.path.mobilenode.library

import network.path.mobilenode.library.data.runner.HttpRunner
import network.path.mobilenode.library.data.runner.Status
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import okhttp3.OkHttpClient
import okhttp3.mock.MockInterceptor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class HttpRunnerTest {
    companion object {
        private const val DUMMY_UUID = "DUMMY_UUID"
        private const val DUMMY_NODE_ID = "DUMMY_NODE_ID"
        private const val DUMMY_SUCCESS_URL = "http://www.server.org/"
        private const val DUMMY_FAILURE_URL = "http://www.failure.org/"
        private const val RESPONSE_SUCCESS = "RESPONSE_SUCCESS"
    }

    private lateinit var runner: HttpRunner
    private lateinit var interceptor: MockInterceptor

    @BeforeEach
    fun init() {
        val storage = Mockito.mock(PathStorage::class.java)
        Mockito.`when`(storage.nodeId).thenReturn(DUMMY_NODE_ID)

        interceptor = MockInterceptor()

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        runner = HttpRunner(httpClient, storage)
    }

    @Test
    fun testSuccess() {
        interceptor.addRule()
            .get().or().put().or().post()
            .url(DUMMY_SUCCESS_URL)
            .respond(RESPONSE_SUCCESS)

        val request = JobRequest(
            protocol = "http",
            method = "get",
            endpointAddress = DUMMY_SUCCESS_URL,
            jobUuid = DUMMY_UUID,
            executionUuid = DUMMY_UUID
        )
        val result = runner.runJob(request, MockTimeSource)
        Assertions.assertEquals(result.checkType, JobType.HTTP)
        Assertions.assertEquals(result.executionUuid, DUMMY_UUID)
        Assertions.assertEquals(result.responseBody, RESPONSE_SUCCESS)
        Assertions.assertNotEquals(result.status, Status.UNKNOWN)
    }

    @Test
    fun testFailure() {
        interceptor.addRule()
            .get(DUMMY_FAILURE_URL)
            .respond(401)

        val request = JobRequest(
            protocol = "http",
            method = "get",
            endpointAddress = DUMMY_FAILURE_URL,
            jobUuid = DUMMY_UUID,
            executionUuid = DUMMY_UUID
        )
        val result = runner.runJob(request, MockTimeSource)
        Assertions.assertEquals(result.checkType, JobType.HTTP)
        Assertions.assertEquals(result.executionUuid, DUMMY_UUID)
        Assertions.assertEquals(result.status, Status.UNKNOWN)
    }
}
