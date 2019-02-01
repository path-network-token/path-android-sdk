package network.path.mobilenode.library.data.runner

import com.google.gson.Gson
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.data.runner.mtr.Mtr
import network.path.mobilenode.library.data.runner.mtr.MtrResult
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.endpointHost

internal class TraceRunner(private val gson: Gson) : Runner {
    companion object {
        init {
            System.loadLibrary("traceroute")
        }
    }

    override val jobType = JobType.TRACEROUTE

    override fun runJob(jobRequest: JobRequest, timeSource: TimeSource) =
        computeJobResult(jobType, jobRequest, timeSource) {
            runWithTimeout(Constants.TRACEROUTE_JOB_TIMEOUT_MILLIS) {
                runTraceJob(it)
            }
        }

    private fun runTraceJob(jobRequest: JobRequest): Pair<String, Long?> {
        val port = jobRequest.endpointPort ?: 0
        val res = Mtr().trace(jobRequest.endpointHost, port, false)
        return if (res != null) {
            val filtered = res.filterNotNull().filter { it.ttl != 0 }
            val lastHops = filtered.groupBy(MtrResult::ttl).maxBy { it.key }?.value
            val duration = lastHops?.filter { !it.timeout }?.map { it.delay * 1_000_000 }?.average()
            gson.toJson(filtered) to duration?.toLong()
        } else "" to null
    }
}
