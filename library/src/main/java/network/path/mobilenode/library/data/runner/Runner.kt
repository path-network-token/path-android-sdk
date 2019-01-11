package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import network.path.mobilenode.library.domain.entity.JobType

internal interface Runner {
    val jobType: JobType
    fun runJob(jobRequest: JobRequest, timeSource: TimeSource): JobResult
}
