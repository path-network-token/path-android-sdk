/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package network.path.mobilenode.library.vpn

import timber.log.Timber
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class TcpOutput(
    private val inputQueue: ConcurrentLinkedQueue<Packet>,
    private val outputQueue: ConcurrentLinkedQueue<ByteBuffer>,
    private val selector: Selector,
    private val vpnService: PathVpnService
) : Runnable {
    private val random = Random()

    override fun run() {
        Timber.i("TCP OUT: started")
        try {

            val currentThread = Thread.currentThread()
            while (true) {
                var currentPacket: Packet?
                // TODO: Block when not connected
                do {
                    currentPacket = inputQueue.poll()
                    if (currentPacket != null)
                        break
                    Thread.sleep(10)
                } while (!currentThread.isInterrupted)

                if (currentThread.isInterrupted)
                    break

                val payloadBuffer = currentPacket!!.backingBuffer

                val responseBuffer = ByteBufferPool.acquire()

                val destinationAddress = currentPacket.ip4Header.destinationAddress

                val tcpHeader = currentPacket.header as Packet.TcpHeader
                val destinationPort = tcpHeader.destinationPort
                val sourcePort = tcpHeader.sourcePort

                val ipAndPort = "${destinationAddress.hostAddress}:$destinationPort:$sourcePort"
                val tcb = Tcb.getTcb(ipAndPort)
                when {
                    tcb == null -> initializeConnection(
                        ipAndPort, destinationAddress, destinationPort,
                        currentPacket, tcpHeader, responseBuffer
                    )
                    tcpHeader.isSYN -> processDuplicateSYN(tcb, tcpHeader, responseBuffer)
                    tcpHeader.isRST -> closeCleanly(tcb, responseBuffer)
                    tcpHeader.isFIN -> processFIN(tcb, tcpHeader, responseBuffer)
                    tcpHeader.isACK -> processACK(tcb, tcpHeader, payloadBuffer, responseBuffer)
                    // XXX: cleanup later
                }

                // XXX: cleanup later
                if (responseBuffer.position() == 0) {
                    ByteBufferPool.release(responseBuffer)
                }
                ByteBufferPool.release(payloadBuffer)
            }
        } catch (e: InterruptedException) {
            Timber.i("TCP OUT: stopping")
        } catch (e: IOException) {
            Timber.e(e, "TCP OUT: error")
        } finally {
            Tcb.closeAll()
        }
    }

    @Throws(IOException::class)
    private fun initializeConnection(
        ipAndPort: String,
        destinationAddress: InetAddress,
        destinationPort: Int,
        currentPacket: Packet,
        tcpHeader: Packet.TcpHeader,
        responseBuffer: ByteBuffer
    ) {
        currentPacket.swapSourceAndDestination()
        if (tcpHeader.isSYN) {
            val outputChannel = SocketChannel.open()
            outputChannel.configureBlocking(false)
            vpnService.protect(outputChannel.socket())

            val tcb = Tcb(
                ipAndPort,
                random.nextLong() % (java.lang.Short.MAX_VALUE + 1),
                tcpHeader.sequenceNumber,
                tcpHeader.sequenceNumber + 1,
                tcpHeader.acknowledgementNumber,
                outputChannel,
                currentPacket
            )
            Tcb.putTcb(ipAndPort, tcb)

            try {
                outputChannel.connect(InetSocketAddress(destinationAddress, destinationPort))
                if (outputChannel.finishConnect()) {
                    tcb.status = Tcb.TcbStatus.SYN_RECEIVED
                    // TODO: Set MSS for receiving larger packets from the device
                    currentPacket.updateTcpBuffer(
                        responseBuffer,
                        (Packet.TcpHeader.SYN or Packet.TcpHeader.ACK).toByte(),
                        tcb.mySequenceNum,
                        tcb.myAcknowledgementNum,
                        0
                    )
                    tcb.mySequenceNum++ // SYN counts as a byte
                } else {
                    tcb.status = Tcb.TcbStatus.SYN_SENT
                    selector.wakeup()
                    tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_CONNECT, tcb)
                    return
                }
            } catch (e: IOException) {
                Timber.e(e, "TCP OUT: connection error [$ipAndPort]")
                currentPacket.updateTcpBuffer(
                    responseBuffer,
                    Packet.TcpHeader.RST.toByte(),
                    0,
                    tcb.myAcknowledgementNum,
                    0
                )
                Tcb.closeTcb(tcb)
            }

        } else {
            currentPacket.updateTcpBuffer(
                responseBuffer,
                Packet.TcpHeader.RST.toByte(),
                0,
                tcpHeader.sequenceNumber + 1,
                0
            )
        }
        outputQueue.offer(responseBuffer)
    }

    private fun processDuplicateSYN(tcb: Tcb, tcpHeader: Packet.TcpHeader, responseBuffer: ByteBuffer) {
        synchronized(tcb) {
            if (tcb.status === Tcb.TcbStatus.SYN_SENT) {
                tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + 1
                return
            }
        }
        sendRST(tcb, 1, responseBuffer)
    }

    private fun processFIN(tcb: Tcb, tcpHeader: Packet.TcpHeader, responseBuffer: ByteBuffer) {
        synchronized(tcb) {
            val referencePacket = tcb.referencePacket
            tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + 1
            tcb.theirAcknowledgementNum = tcpHeader.acknowledgementNumber

            if (tcb.waitingForNetworkData) {
                tcb.status = Tcb.TcbStatus.CLOSE_WAIT
                referencePacket.updateTcpBuffer(
                    responseBuffer,
                    Packet.TcpHeader.ACK.toByte(),
                    tcb.mySequenceNum,
                    tcb.myAcknowledgementNum,
                    0
                )
            } else {
                tcb.status = Tcb.TcbStatus.LAST_ACK
                referencePacket.updateTcpBuffer(
                    responseBuffer,
                    (Packet.TcpHeader.FIN or Packet.TcpHeader.ACK).toByte(),
                    tcb.mySequenceNum,
                    tcb.myAcknowledgementNum,
                    0
                )
                tcb.mySequenceNum++ // FIN counts as a byte
            }
        }
        outputQueue.offer(responseBuffer)
    }

    @Throws(IOException::class)
    private fun processACK(
        tcb: Tcb,
        tcpHeader: Packet.TcpHeader,
        payloadBuffer: ByteBuffer,
        responseBuffer: ByteBuffer
    ) {
        val payloadSize = payloadBuffer.limit() - payloadBuffer.position()

        synchronized(tcb) {
            val outputChannel = tcb.channel
            if (tcb.status == Tcb.TcbStatus.SYN_RECEIVED) {
                tcb.status = Tcb.TcbStatus.ESTABLISHED

                selector.wakeup()
                tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_READ, tcb)
                tcb.waitingForNetworkData = true
            } else if (tcb.status == Tcb.TcbStatus.LAST_ACK) {
                closeCleanly(tcb, responseBuffer)
                return
            }

            if (payloadSize == 0) return  // Empty ACK, ignore

            if (!tcb.waitingForNetworkData) {
                selector.wakeup()
                tcb.selectionKey?.interestOps(SelectionKey.OP_READ)
                tcb.waitingForNetworkData = true
            }

            // Forward to remote server
            try {
                while (payloadBuffer.hasRemaining())
                    outputChannel.write(payloadBuffer)
            } catch (e: IOException) {
                Timber.e(e, "TCP OUT: Network write error ${tcb.ipAndPort}")
                sendRST(tcb, payloadSize, responseBuffer)
                return
            }

            // TODO: We don't expect out-of-order packets, but verify
            tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + payloadSize
            tcb.theirAcknowledgementNum = tcpHeader.acknowledgementNumber
            val referencePacket = tcb.referencePacket
            referencePacket.updateTcpBuffer(
                responseBuffer,
                Packet.TcpHeader.ACK.toByte(),
                tcb.mySequenceNum,
                tcb.myAcknowledgementNum,
                0
            )
        }
        outputQueue.offer(responseBuffer)
    }

    private fun sendRST(tcb: Tcb, prevPayloadSize: Int, buffer: ByteBuffer) {
        tcb.referencePacket.updateTcpBuffer(
            buffer,
            Packet.TcpHeader.RST.toByte(),
            0,
            tcb.myAcknowledgementNum + prevPayloadSize,
            0
        )
        outputQueue.offer(buffer)
        Tcb.closeTcb(tcb)
    }

    private fun closeCleanly(tcb: Tcb, buffer: ByteBuffer) {
        ByteBufferPool.release(buffer)
        Tcb.closeTcb(tcb)
    }
}
