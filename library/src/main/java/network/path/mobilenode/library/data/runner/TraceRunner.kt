package network.path.mobilenode.library.data.runner

import android.content.Context
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.endpointHost
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

internal class TraceRunner(private val context: Context) : Runner {
    override val jobType = JobType.TRACEROUTE

    override fun runJob(jobRequest: JobRequest, timeSource: TimeSource) =
        computeJobResult(jobType, jobRequest, timeSource) {
            runWithTimeout(Constants.TRACEROUTE_JOB_TIMEOUT_MILLIS) {
                runTraceJob(it)
            }
        }

    private fun runTraceJob(jobRequest: JobRequest): Pair<String, Long?> {
        val libs = context.applicationInfo.nativeLibraryDir
        val cmd = listOf(
            File(libs, "libtraceroute.so").absolutePath,
            "--icmp", "--wait=1,3,10", "-n", "--queries=10", jobRequest.endpointHost
        )

        val p = ProcessBuilder(cmd).start()
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(p.inputStream)).useLines {
            it.forEach { line -> sb.append(line) }
        }
        p.destroy()

        return sb.toString() to null
    }
}
