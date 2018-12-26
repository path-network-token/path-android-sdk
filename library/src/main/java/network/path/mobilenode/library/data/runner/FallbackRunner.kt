package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.domain.entity.CheckType
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import network.path.mobilenode.library.domain.entity.Status

object FallbackRunner : Runner {
    override val checkType = CheckType.UNKNOWN

    override fun runJob(jobRequest: JobRequest) = JobResult(
            checkType = checkType,
            responseBody = "No runner found for $jobRequest",
            responseTime = 0L,
            status = Status.UNKNOWN,
            executionUuid = jobRequest.executionUuid
    )
}