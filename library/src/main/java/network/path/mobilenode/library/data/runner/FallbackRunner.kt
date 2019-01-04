package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult

internal object FallbackRunner : Runner {
    override val jobType = JobType.UNKNOWN

    override fun runJob(jobRequest: JobRequest) = JobResult(
            checkType = jobType,
            responseBody = "No runner found for $jobRequest",
            responseTime = 0L,
            status = Status.UNKNOWN,
            executionUuid = jobRequest.executionUuid
    )
}