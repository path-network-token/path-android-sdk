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
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue

class TcpInput(private val outputQueue: ConcurrentLinkedQueue<ByteBuffer>, private val selector: Selector) : Runnable {
    companion object {
        private const val HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE
    }

    override fun run() {
        try {
            Timber.d("TCP IN: started")
            while (!Thread.interrupted()) {
                val readyChannels = selector.select()

                if (readyChannels == 0) {
                    Thread.sleep(10)
                    continue
                }

                val keys = selector.selectedKeys()
                val keyIterator = keys.iterator()

                while (keyIterator.hasNext() && !Thread.interrupted()) {
                    val key = keyIterator.next()
                    if (key.isValid) {
                        if (key.isConnectable) {
                            processConnect(key, keyIterator)
                        } else if (key.isReadable) {
                            processInput(key, keyIterator)
                        }
                    }
                }
            }
        } catch (e: InterruptedException) {
            Timber.i("TCP IN: stopping")
        } catch (e: IOException) {
            Timber.e(e, "TCP IN: error")
        }
    }

    private fun processConnect(key: SelectionKey, keyIterator: MutableIterator<SelectionKey>) {
        val tcb = key.attachment() as Tcb
        val referencePacket = tcb.referencePacket
        try {
            if (tcb.channel.finishConnect()) {
                keyIterator.remove()
                tcb.status = Tcb.TcbStatus.SYN_RECEIVED

                // TODO: Set MSS for receiving larger packets from the device
                val responseBuffer = ByteBufferPool.acquire()
                referencePacket.updateTcpBuffer(
                    responseBuffer,
                    (Packet.TcpHeader.SYN or Packet.TcpHeader.ACK).toByte(),
                    tcb.mySequenceNum,
                    tcb.myAcknowledgementNum,
                    0
                )
                outputQueue.offer(responseBuffer)

                tcb.mySequenceNum++ // SYN counts as a byte
                key.interestOps(SelectionKey.OP_READ)
            }
        } catch (e: IOException) {
            Timber.e(e, "TCP IN: connection error [${tcb.ipAndPort}]")
            val responseBuffer = ByteBufferPool.acquire()
            referencePacket.updateTcpBuffer(
                responseBuffer,
                Packet.TcpHeader.RST.toByte(),
                0,
                tcb.myAcknowledgementNum,
                0
            )
            outputQueue.offer(responseBuffer)
            Tcb.closeTcb(tcb)
        }

    }

    private fun processInput(key: SelectionKey, keyIterator: MutableIterator<SelectionKey>) {
        keyIterator.remove()

        val receiveBuffer = ByteBufferPool.acquire()
        // Leave space for the header
        receiveBuffer.position(HEADER_SIZE)

        val tcb = key.attachment() as Tcb
        synchronized(tcb) {
            val referencePacket = tcb.referencePacket
            val inputChannel = key.channel() as SocketChannel
            val readBytes: Int
            try {
                readBytes = inputChannel.read(receiveBuffer)
            } catch (e: IOException) {
                Timber.e(e, "TCP IN: network read error [${tcb.ipAndPort}]")
                referencePacket.updateTcpBuffer(
                    receiveBuffer,
                    Packet.TcpHeader.RST.toByte(),
                    0,
                    tcb.myAcknowledgementNum,
                    0
                )
                outputQueue.offer(receiveBuffer)
                Tcb.closeTcb(tcb)
                return
            }

            if (readBytes == -1) {
                // End of stream, stop waiting until we push more data
                key.interestOps(0)
                tcb.waitingForNetworkData = false

                if (tcb.status !== Tcb.TcbStatus.CLOSE_WAIT) {
                    ByteBufferPool.release(receiveBuffer)
                    return
                }

                tcb.status = Tcb.TcbStatus.LAST_ACK
                referencePacket.updateTcpBuffer(
                    receiveBuffer,
                    Packet.TcpHeader.FIN.toByte(),
                    tcb.mySequenceNum,
                    tcb.myAcknowledgementNum,
                    0
                )
                tcb.mySequenceNum++ // FIN counts as a byte
            } else {
                // XXX: We should ideally be splitting segments by MTU/MSS, but this seems to work without
                referencePacket.updateTcpBuffer(
                    receiveBuffer, (Packet.TcpHeader.PSH or Packet.TcpHeader.ACK).toByte(),
                    tcb.mySequenceNum, tcb.myAcknowledgementNum, readBytes
                )
                tcb.mySequenceNum += readBytes // Next sequence number
                receiveBuffer.position(HEADER_SIZE + readBytes)
            }
        }
        outputQueue.offer(receiveBuffer)
    }
}
