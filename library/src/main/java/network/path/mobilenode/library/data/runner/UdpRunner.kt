package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.endpointHost
import network.path.mobilenode.library.domain.entity.endpointPortOrDefault
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

internal class UdpRunner : Runner {
    override val jobType = JobType.UDP

    override fun runJob(jobRequest: JobRequest, timeSource: TimeSource) =
        computeJobResult(jobType, jobRequest, timeSource) {
            runWithTimeout(Constants.JOB_TIMEOUT_MILLIS) {
                runUdpJob(it)
            }
        }

    private fun runUdpJob(jobRequest: JobRequest): Pair<String, Long?> {
        DatagramSocket().use {
            val port = jobRequest.endpointPortOrDefault(Constants.DEFAULT_UDP_PORT)
            val socketAddress = InetAddress.getByName(jobRequest.endpointHost)
            val body = jobRequest.payload.orEmpty()

            val datagramPacket = DatagramPacket(body.toByteArray(), body.length, socketAddress, port)
            it.send(datagramPacket)
        }
        return "UDP packet sent successfully" to null
    }
}
