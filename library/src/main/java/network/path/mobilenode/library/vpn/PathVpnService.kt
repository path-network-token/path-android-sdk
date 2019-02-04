package network.path.mobilenode.library.vpn

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import timber.log.Timber
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PathVpnService : VpnService() {
    companion object {
        private const val VPN_ADDRESS = "10.0.0.2" // Only IPv4 support for now
        private const val VPN_ROUTE = "0.0.0.0" // Intercept everything

        const val BROADCAST_VPN_STATE = "network.path.mobilenode.library.vpn.VPN_STATE"

        var isRunning = false
            private set

        // TODO: Move this to a "utils" class for reuse
        private fun closeResources(vararg resources: Closeable) {
            for (resource in resources) {
                try {
                    resource.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val pendingIntent: PendingIntent? = null

    private var deviceToNetworkUDPQueue: ConcurrentLinkedQueue<Packet>? = null
    private var deviceToNetworkTCPQueue: ConcurrentLinkedQueue<Packet>? = null
    private var networkToDeviceQueue: ConcurrentLinkedQueue<ByteBuffer>? = null
    private var executorService: ExecutorService? = null

    private var udpSelector: Selector? = null
    private var tcpSelector: Selector? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        setupVPN()
        try {
            udpSelector = Selector.open()
            tcpSelector = Selector.open()
            deviceToNetworkUDPQueue = ConcurrentLinkedQueue()
            deviceToNetworkTCPQueue = ConcurrentLinkedQueue()
            networkToDeviceQueue = ConcurrentLinkedQueue()

            executorService = Executors.newFixedThreadPool(5).apply {
                submit(UdpInput(networkToDeviceQueue!!, udpSelector!!))
                submit(UdpOutput(deviceToNetworkUDPQueue!!, udpSelector!!, this@PathVpnService))
                submit(TcpInput(networkToDeviceQueue!!, tcpSelector!!))
                submit(TcpOutput(deviceToNetworkTCPQueue!!, networkToDeviceQueue!!, tcpSelector!!, this@PathVpnService))
                submit(
                    VpnRunnable(
                        vpnInterface!!.fileDescriptor,
                        deviceToNetworkUDPQueue!!,
                        deviceToNetworkTCPQueue!!,
                        networkToDeviceQueue!!
                    )
                )
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_STATE).putExtra("running", true))
            Timber.i("VPN: started")
        } catch (e: IOException) {
            // TODO: Here and elsewhere, we should explicitly notify the user of any errors
            // and suggest that they stop the service, since we can't do it ourselves
            Timber.e(e, "VPN: error starting service")
            cleanup()
        }
    }

    private fun setupVPN() {
        if (vpnInterface == null) {
            vpnInterface = Builder()
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .setSession("PathVpnService")
                .setConfigureIntent(pendingIntent)
                .establish()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        executorService!!.shutdownNow()
        cleanup()
        Timber.i("VPN: stopped")
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_STATE).putExtra("running", false))
    }

    private fun cleanup() {
        deviceToNetworkTCPQueue = null
        deviceToNetworkUDPQueue = null
        networkToDeviceQueue = null
        ByteBufferPool.clear()
        closeResources(udpSelector!!, tcpSelector!!, vpnInterface!!)
    }

    private class VpnRunnable(
        private val vpnFileDescriptor: FileDescriptor,
        private val deviceToNetworkUDPQueue: ConcurrentLinkedQueue<Packet>,
        private val deviceToNetworkTCPQueue: ConcurrentLinkedQueue<Packet>,
        private val networkToDeviceQueue: ConcurrentLinkedQueue<ByteBuffer>
    ) : Runnable {

        override fun run() {
            Timber.i("VPN: runnable started")

            val vpnInput = FileInputStream(vpnFileDescriptor).channel
            val vpnOutput = FileOutputStream(vpnFileDescriptor).channel

            try {
                var bufferToNetwork: ByteBuffer? = null
                var dataSent = true
                var dataReceived: Boolean
                while (!Thread.interrupted()) {
                    if (dataSent) {
                        bufferToNetwork = ByteBufferPool.acquire()
                    } else {
                        bufferToNetwork!!.clear()
                    }

                    // TODO: Block when not connected
                    val readBytes = vpnInput.read(bufferToNetwork)
                    if (readBytes > 0) {
                        dataSent = true
                        bufferToNetwork.flip()
                        val packet = Packet(bufferToNetwork)
                        when {
                            packet.isUDP -> deviceToNetworkUDPQueue.offer(packet)
                            packet.isTCP -> deviceToNetworkTCPQueue.offer(packet)
                            else -> {
                                Timber.w("VPN: Unknown packet type [${packet.ip4Header}]")
                                dataSent = false
                            }
                        }
                    } else {
                        dataSent = false
                    }

                    val bufferFromNetwork = networkToDeviceQueue.poll()
                    if (bufferFromNetwork != null) {
                        bufferFromNetwork.flip()
                        while (bufferFromNetwork.hasRemaining())
                            vpnOutput.write(bufferFromNetwork)
                        dataReceived = true

                        ByteBufferPool.release(bufferFromNetwork)
                    } else {
                        dataReceived = false
                    }

                    // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                    // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                    if (!dataSent && !dataReceived)
                        Thread.sleep(10)
                }
            } catch (e: InterruptedException) {
                Timber.i("VPN: stopping runnable")
            } catch (e: IOException) {
                Timber.e(e, "VPN: error in runnable")
            } finally {
                closeResources(vpnInput, vpnOutput)
            }
        }
    }
}
