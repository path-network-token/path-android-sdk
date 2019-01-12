package network.path.mobilenode.library

import network.path.mobilenode.library.data.runner.FallbackRunner
import network.path.mobilenode.library.data.runner.Status
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

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
}
