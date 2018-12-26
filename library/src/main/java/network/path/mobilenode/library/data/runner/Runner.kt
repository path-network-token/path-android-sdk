package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.domain.entity.CheckType
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult

interface Runner {
    val checkType: CheckType
    fun runJob(jobRequest: JobRequest): JobResult
}
