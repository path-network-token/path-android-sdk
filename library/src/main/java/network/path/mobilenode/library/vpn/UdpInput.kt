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
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue

class UdpInput(private val outputQueue: ConcurrentLinkedQueue<ByteBuffer>, private val selector: Selector) : Runnable {
    companion object {
        private const val HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE
    }

    override fun run() {
        try {
            Timber.i("UDP IN: started")
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
                    if (key.isValid && key.isReadable) {
                        keyIterator.remove()

                        val receiveBuffer = ByteBufferPool.acquire()
                        // Leave space for the header
                        receiveBuffer.position(HEADER_SIZE)

                        val inputChannel = key.channel() as DatagramChannel
                        // XXX: We should handle any IOExceptions here immediately,
                        // but that probably won't happen with UDP
                        val readBytes = inputChannel.read(receiveBuffer)

                        val referencePacket = key.attachment() as Packet
                        referencePacket.updateUdpBuffer(receiveBuffer, readBytes)
                        receiveBuffer.position(HEADER_SIZE + readBytes)

                        outputQueue.offer(receiveBuffer)
                    }
                }
            }
        } catch (e: InterruptedException) {
            Timber.i("UDP IN: stopping")
        } catch (e: IOException) {
            Timber.e(e, "UDP IN: error")
        }
    }
}
