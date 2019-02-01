package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.endpointHost
import network.path.mobilenode.library.domain.entity.endpointPortOrDefault
import network.path.mobilenode.library.utils.readText
import network.path.mobilenode.library.utils.writeText
import java.net.InetSocketAddress
import javax.net.SocketFactory

internal class TcpRunner(private val factory: SocketFactory) : Runner {
    override val jobType = JobType.TCP

    override fun runJob(jobRequest: JobRequest, timeSource: TimeSource) =
        computeJobResult(jobType, jobRequest, timeSource) {
            runWithTimeout(Constants.JOB_TIMEOUT_MILLIS) {
                runTcpJob(it)
            }
        }

    private fun runTcpJob(jobRequest: JobRequest): Pair<String, Long?> =
        factory.createSocket().use {
            val port = jobRequest.endpointPortOrDefault(Constants.DEFAULT_TCP_PORT)
            val address = InetSocketAddress(jobRequest.endpointHost, port)

            it.connect(address, Constants.JOB_TIMEOUT_MILLIS.toInt())

            if (jobRequest.payload != null) {
                it.soTimeout = Constants.TCP_UDP_READ_WRITE_TIMEOUT_MILLIS.toInt()
                it.writeText(jobRequest.payload)

                it.readText(Constants.RESPONSE_LENGTH_BYTES_MAX)
            } else {
                "TCP connection established successfully"
            } to null
        }
}
