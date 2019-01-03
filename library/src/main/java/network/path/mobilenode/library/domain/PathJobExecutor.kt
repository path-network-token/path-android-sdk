package network.path.mobilenode.library.domain

import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import java.util.concurrent.Future

internal interface PathJobExecutor {
    fun start()
    fun execute(request: JobRequest): Future<JobResult>
    fun stop()
}
