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
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue

class UdpOutput(
    private val inputQueue: ConcurrentLinkedQueue<Packet>,
    private val selector: Selector,
    private val vpnService: PathVpnService
) : Runnable {
    companion object {
        private const val MAX_CACHE_SIZE = 50
    }

    private val channelCache = LRUCache(MAX_CACHE_SIZE,
        object : LRUCache.CleanupCallback<String, DatagramChannel> {
            override fun cleanup(eldest: Map.Entry<String, DatagramChannel>) {
                closeChannel(eldest.value)
            }
        })

    override fun run() {
        Timber.i("UDP OUT: started")
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

                val destinationAddress = currentPacket!!.ip4Header.destinationAddress
                val destinationPort = currentPacket.header.destinationPort
                val sourcePort = currentPacket.header.sourcePort

                val ipAndPort = "${destinationAddress.hostAddress}:$destinationPort:$sourcePort"
                var outputChannel = channelCache[ipAndPort]
                if (outputChannel == null) {
                    outputChannel = DatagramChannel.open()
                    vpnService.protect(outputChannel!!.socket())
                    try {
                        outputChannel.connect(InetSocketAddress(destinationAddress, destinationPort))
                    } catch (e: IOException) {
                        Timber.e(e, "UDP OUT: connection error [$ipAndPort]")
                        closeChannel(outputChannel)
                        ByteBufferPool.release(currentPacket.backingBuffer)
                        continue
                    }

                    outputChannel.configureBlocking(false)
                    currentPacket.swapSourceAndDestination()

                    selector.wakeup()
                    outputChannel.register(selector, SelectionKey.OP_READ, currentPacket)

                    channelCache[ipAndPort] = outputChannel
                }

                try {
                    val payloadBuffer = currentPacket.backingBuffer
                    while (payloadBuffer.hasRemaining()) {
                        outputChannel.write(payloadBuffer)
                    }
                } catch (e: IOException) {
                    Timber.e(e, "UDP OUT: network write error [$ipAndPort]")
                    channelCache.remove(ipAndPort)
                    closeChannel(outputChannel)
                }

                ByteBufferPool.release(currentPacket.backingBuffer)
            }
        } catch (e: InterruptedException) {
            Timber.i("UDP OUT: stopping")
        } catch (e: IOException) {
            Timber.i(e, "UPD OUT: error")
        } finally {
            closeAll()
        }
    }

    private fun closeAll() {
        val it = channelCache.entries.iterator()
        while (it.hasNext()) {
            closeChannel(it.next().value)
            it.remove()
        }
    }

    private fun closeChannel(channel: DatagramChannel) {
        try {
            channel.close()
        } catch (e: IOException) {
            // Ignore
        }
    }
}
