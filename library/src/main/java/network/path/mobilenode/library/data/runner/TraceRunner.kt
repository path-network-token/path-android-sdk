package network.path.mobilenode.library.data.runner

import com.google.gson.Gson
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.data.runner.mtr.Mtr
import network.path.mobilenode.library.domain.entity.CheckType
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.endpointHost

class TraceRunner(private val gson: Gson) : Runner {
    companion object {
        init {
            System.loadLibrary("traceroute")
        }
    }

    override val checkType = CheckType.TRACEROUTE

    override fun runJob(jobRequest: JobRequest) = computeJobResult(checkType, jobRequest) {
        runWithTimeout(Constants.TRACEROUTE_JOB_TIMEOUT_MILLIS) {
            runTraceJob(it)
        }
    }

    private fun runTraceJob(jobRequest: JobRequest): String {
        val port = jobRequest.endpointPort ?: 0
        val res = Mtr().trace(jobRequest.endpointHost, port)
        return if (res != null) gson.toJson(res.filter { it != null && it.ttl != 0 }) else ""
    }
}
