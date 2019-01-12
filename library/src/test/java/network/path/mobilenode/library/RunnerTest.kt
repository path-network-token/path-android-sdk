package network.path.mobilenode.library

import com.google.gson.Gson
import network.path.mobilenode.library.data.runner.*
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class RunnerTest {
    companion object {
        const val DUMMY_UUID = "DUMMY_UUID"
    }

    @Test
    fun testFallbackRunner() {
        val request = JobRequest(executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID)
        val runner = FallbackRunner
        val result = runner.runJob(request, MockTimeSource)
        Assertions.assertEquals(result.checkType, JobType.UNKNOWN)
        Assertions.assertEquals(result.executionUuid, DUMMY_UUID)
        Assertions.assertEquals(result.responseTime, 0L)
        Assertions.assertEquals(result.status, Status.UNKNOWN)
    }

    @Test
    fun testJobRequestFindRunner() {
        val executor = PathJobExecutorImpl(
            Mockito.mock(OkHttpClient::class.java),
            Mockito.mock(PathStorage::class.java),
            Mockito.mock(Gson::class.java),
            MockTimeSource
        )

        // Fallback runner
        JobRequest(executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is FallbackRunner)
        }

        // HTTP runner
        JobRequest(protocol = "http", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is HttpRunner)
        }
        JobRequest(protocol = "HTTPS", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is HttpRunner)
        }

        // TCP runner
        JobRequest(protocol = "TcP", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is TcpRunner)
        }

        // UDP runner
        JobRequest(protocol = "uDp", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is UdpRunner)
        }

        // Tracepath runner
        // Very fragile test as I'm relying on loading a library when TraceRunner is created
        JobRequest(protocol = "", method = "traceroute", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            Assertions.assertThrows(UnsatisfiedLinkError::class.java) {
                executor.findRunner(it)
            }
        }
    }
}
