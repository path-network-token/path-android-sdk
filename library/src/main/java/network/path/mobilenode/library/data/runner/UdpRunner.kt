package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.entity.CheckType
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.endpointHost
import network.path.mobilenode.library.domain.entity.endpointPortOrDefault
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

internal class UdpRunner : Runner {
    override val checkType = CheckType.UDP

    override fun runJob(jobRequest: JobRequest) = computeJobResult(checkType, jobRequest) {
        runWithTimeout(Constants.JOB_TIMEOUT_MILLIS) {
            runUdpJob(it)
        }
    }

    private fun runUdpJob(jobRequest: JobRequest): String {
        val port = jobRequest.endpointPortOrDefault(Constants.DEFAULT_UDP_PORT)

        DatagramSocket().use {
            val socketAddress = InetAddress.getByName(jobRequest.endpointHost)
            val body = jobRequest.payload.orEmpty()

            val datagramPacket = DatagramPacket(body.toByteArray(), body.length, socketAddress, port)
            it.send(datagramPacket)
        }
        return "UDP packet sent successfully"
    }
}
