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

import java.io.IOException
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

/**
 * Transmission Control Block
 */
class Tcb(
    var ipAndPort: String,
    var mySequenceNum: Long,
    var theirSequenceNum: Long,
    var myAcknowledgementNum: Long,
    var theirAcknowledgementNum: Long,
    var channel: SocketChannel,
    var referencePacket: Packet
) {
    companion object {
        private const val MAX_CACHE_SIZE = 50 // XXX: Is this ideal?
        private val tcbCache = LRUCache(
            MAX_CACHE_SIZE,
            object : LRUCache.CleanupCallback<String, Tcb> {
                override fun cleanup(eldest: Map.Entry<String, Tcb>) {
                    eldest.value.closeChannel()
                }
            })

        fun getTcb(ipAndPort: String): Tcb? {
            synchronized(tcbCache) {
                return tcbCache[ipAndPort]
            }
        }

        fun putTcb(ipAndPort: String, tcb: Tcb) {
            synchronized(tcbCache) {
                tcbCache.put(ipAndPort, tcb)
            }
        }

        fun closeTcb(tcb: Tcb) {
            tcb.closeChannel()
            synchronized(tcbCache) {
                tcbCache.remove(tcb.ipAndPort)
            }
        }

        fun closeAll() {
            synchronized(tcbCache) {
                val it = tcbCache.entries.iterator()
                while (it.hasNext()) {
                    it.next().value.closeChannel()
                    it.remove()
                }
            }
        }
    }

    // TCP has more states, but we need only these
    enum class TcbStatus {
        SYN_SENT,
        SYN_RECEIVED,
        ESTABLISHED,
        CLOSE_WAIT,
        LAST_ACK
    }

    var status: TcbStatus? = null
    var waitingForNetworkData: Boolean = false
    var selectionKey: SelectionKey? = null

    private fun closeChannel() {
        try {
            channel.close()
        } catch (e: IOException) {
            // Ignore
        }
    }
}
