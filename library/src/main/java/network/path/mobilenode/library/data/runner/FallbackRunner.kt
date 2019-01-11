package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import network.path.mobilenode.library.domain.entity.JobType

internal object FallbackRunner : Runner {
    override val jobType = JobType.UNKNOWN

    override fun runJob(jobRequest: JobRequest, timeSource: TimeSource) = JobResult(
            checkType = jobType,
            responseBody = "No runner found for $jobRequest",
            responseTime = 0L,
            status = Status.UNKNOWN,
            executionUuid = jobRequest.executionUuid
    )
}