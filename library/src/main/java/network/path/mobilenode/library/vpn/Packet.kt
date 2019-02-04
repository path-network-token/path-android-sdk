package network.path.mobilenode.library.vpn

import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Representation of an IP Packet
 */
// TODO: Reduce public mutability
class Packet @Throws(UnknownHostException::class)
constructor(var backingBuffer: ByteBuffer) {
    companion object {
        const val IP4_HEADER_SIZE = 20
        const val TCP_HEADER_SIZE = 20
        const val UDP_HEADER_SIZE = 8
    }

    val ip4Header: Ip4Header
    val header: Header

    var isTCP: Boolean = false
        private set
    var isUDP: Boolean = false
        private set

    init {
        ip4Header = Ip4Header(backingBuffer)
        header = when {
            ip4Header.protocol == Ip4Header.TransportProtocol.TCP -> {
                isTCP = true
                TcpHeader(backingBuffer)
            }
            ip4Header.protocol == Ip4Header.TransportProtocol.UDP -> {
                isUDP = true
                UdpHeader(backingBuffer)
            }
            else -> throw IllegalStateException("Unknown header protocol [${ip4Header.protocol}]")
        }
    }

    override fun toString(): String {
        val sb = StringBuilder("Packet{")
        sb.append("ip4Header=").append(ip4Header)
        sb.append(", header=").append(header)
        sb.append(", payloadSize=").append(backingBuffer.limit() - backingBuffer.position())
        sb.append('}')
        return sb.toString()
    }

    fun swapSourceAndDestination() {
        val newSourceAddress = ip4Header.destinationAddress
        ip4Header.destinationAddress = ip4Header.sourceAddress
        ip4Header.sourceAddress = newSourceAddress

        val newSourcePort = header.destinationPort
        header.destinationPort = header.sourcePort
        header.sourcePort = newSourcePort
    }

    fun updateTcpBuffer(buffer: ByteBuffer, flags: Byte, sequenceNum: Long, ackNum: Long, payloadSize: Int) {
        buffer.position(0)
        fillHeader(buffer)
        backingBuffer = buffer

        val tcpHeader = header as TcpHeader
        tcpHeader.flags = flags
        backingBuffer.put(IP4_HEADER_SIZE + 13, flags)

        tcpHeader.sequenceNumber = sequenceNum
        backingBuffer.putInt(IP4_HEADER_SIZE + 4, sequenceNum.toInt())

        tcpHeader.acknowledgementNumber = ackNum
        backingBuffer.putInt(IP4_HEADER_SIZE + 8, ackNum.toInt())

        // Reset header size, since we don't need options
        val dataOffset = (TCP_HEADER_SIZE shl 2).toByte()
        tcpHeader.dataOffsetAndReserved = dataOffset
        backingBuffer.put(IP4_HEADER_SIZE + 12, dataOffset)

        updateTcpChecksum(payloadSize)

        val ip4TotalLength = IP4_HEADER_SIZE + TCP_HEADER_SIZE + payloadSize
        backingBuffer.putShort(2, ip4TotalLength.toShort())
        ip4Header.totalLength = ip4TotalLength

        updateIp4Checksum()
    }

    fun updateUdpBuffer(buffer: ByteBuffer, payloadSize: Int) {
        buffer.position(0)
        fillHeader(buffer)
        backingBuffer = buffer

        val udpHeader = header as UdpHeader

        val udpTotalLength = UDP_HEADER_SIZE + payloadSize
        backingBuffer.putShort(IP4_HEADER_SIZE + 4, udpTotalLength.toShort())
        udpHeader.length = udpTotalLength

        // Disable UDP checksum validation
        backingBuffer.putShort(IP4_HEADER_SIZE + 6, 0.toShort())
        udpHeader.checksum = 0

        val ip4TotalLength = IP4_HEADER_SIZE + udpTotalLength
        backingBuffer.putShort(2, ip4TotalLength.toShort())
        ip4Header.totalLength = ip4TotalLength

        updateIp4Checksum()
    }

    private fun updateIp4Checksum() {
        val buffer = backingBuffer.duplicate()
        buffer.position(0)

        // Clear previous checksum
        buffer.putShort(10, 0.toShort())

        var ipLength = ip4Header.headerLength
        var sum = 0
        while (ipLength > 0) {
            sum += BitUtils.getUnsignedShort(buffer.short)
            ipLength -= 2
        }
        while (sum shr 16 > 0)
            sum = (sum and 0xFFFF) + (sum shr 16)

        sum = sum.inv()
        ip4Header.headerChecksum = sum
        backingBuffer.putShort(10, sum.toShort())
    }

    private fun updateTcpChecksum(payloadSize: Int) {
        var tcpLength = TCP_HEADER_SIZE + payloadSize

        // Calculate pseudo-header checksum
        var buffer = ByteBuffer.wrap(ip4Header.sourceAddress.address)
        var sum = BitUtils.getUnsignedShort(buffer.short) + BitUtils.getUnsignedShort(buffer.short)

        buffer = ByteBuffer.wrap(ip4Header.destinationAddress.address)
        sum += BitUtils.getUnsignedShort(buffer.short) + BitUtils.getUnsignedShort(buffer.short)

        sum += Ip4Header.TransportProtocol.TCP.number + tcpLength

        buffer = backingBuffer.duplicate()
        // Clear previous checksum
        buffer.putShort(IP4_HEADER_SIZE + 16, 0.toShort())

        // Calculate TCP segment checksum
        buffer.position(IP4_HEADER_SIZE)
        while (tcpLength > 1) {
            sum += BitUtils.getUnsignedShort(buffer.short)
            tcpLength -= 2
        }
        if (tcpLength > 0)
            sum += BitUtils.getUnsignedByte(buffer.get()).toInt() shl 8

        while (sum shr 16 > 0)
            sum = (sum and 0xFFFF) + (sum shr 16)

        sum = sum.inv()
        (header as TcpHeader).checksum = sum
        backingBuffer.putShort(IP4_HEADER_SIZE + 16, sum.toShort())
    }

    private fun fillHeader(buffer: ByteBuffer) {
        ip4Header.fillHeader(buffer)
        header.fillHeader(buffer)
    }

    class Ip4Header @Throws(UnknownHostException::class)
    constructor(buffer: ByteBuffer) {
        var version: Byte = 0
        var IHL: Byte = 0
        var headerLength: Int = 0
        var typeOfService: Short = 0
        var totalLength: Int = 0

        var identificationAndFlagsAndFragmentOffset: Int = 0

        var TTL: Short = 0
        private val protocolNum: Short
        var protocol: TransportProtocol
        var headerChecksum: Int = 0

        var sourceAddress: InetAddress
        var destinationAddress: InetAddress

        var optionsAndPadding: Int = 0

        enum class TransportProtocol constructor(val number: Int) {
            TCP(6),
            UDP(17),
            Other(0xFF);

            companion object {
                fun numberToEnum(protocolNumber: Int): TransportProtocol = when (protocolNumber) {
                    6 -> TCP
                    17 -> UDP
                    else -> Other
                }
            }
        }

        init {
            val versionAndIHL = buffer.get().toInt()
            this.version = (versionAndIHL shr 4).toByte()
            this.IHL = (versionAndIHL and 0x0F).toByte()
            this.headerLength = this.IHL.toInt() shl 2

            this.typeOfService = BitUtils.getUnsignedByte(buffer.get())
            this.totalLength = BitUtils.getUnsignedShort(buffer.short)

            this.identificationAndFlagsAndFragmentOffset = buffer.int

            this.TTL = BitUtils.getUnsignedByte(buffer.get())
            this.protocolNum = BitUtils.getUnsignedByte(buffer.get())
            this.protocol = TransportProtocol.numberToEnum(protocolNum.toInt())
            this.headerChecksum = BitUtils.getUnsignedShort(buffer.short)

            val addressBytes = ByteArray(4)
            buffer.get(addressBytes, 0, 4)
            this.sourceAddress = InetAddress.getByAddress(addressBytes)

            buffer.get(addressBytes, 0, 4)
            this.destinationAddress = InetAddress.getByAddress(addressBytes)

            //this.optionsAndPadding = buffer.getInt();
        }

        fun fillHeader(buffer: ByteBuffer) {
            buffer.put((this.version.toInt() shl 4 or this.IHL.toInt()).toByte())
            buffer.put(this.typeOfService.toByte())
            buffer.putShort(this.totalLength.toShort())

            buffer.putInt(this.identificationAndFlagsAndFragmentOffset)

            buffer.put(this.TTL.toByte())
            buffer.put(this.protocol.number.toByte())
            buffer.putShort(this.headerChecksum.toShort())

            buffer.put(this.sourceAddress.address)
            buffer.put(this.destinationAddress.address)
        }

        override fun toString(): String {
            val sb = StringBuilder("IP4Header{")
            sb.append("version=").append(version.toInt())
            sb.append(", IHL=").append(IHL.toInt())
            sb.append(", typeOfService=").append(typeOfService.toInt())
            sb.append(", totalLength=").append(totalLength)
            sb.append(", identificationAndFlagsAndFragmentOffset=").append(identificationAndFlagsAndFragmentOffset)
            sb.append(", TTL=").append(TTL.toInt())
            sb.append(", protocol=").append(protocolNum.toInt()).append(":").append(protocol)
            sb.append(", headerChecksum=").append(headerChecksum)
            sb.append(", sourceAddress=").append(sourceAddress.hostAddress)
            sb.append(", destinationAddress=").append(destinationAddress.hostAddress)
            sb.append('}')
            return sb.toString()
        }
    }

    interface Header {
        var sourcePort: Int
        var destinationPort: Int

        fun fillHeader(buffer: ByteBuffer)
    }

    class TcpHeader(buffer: ByteBuffer) : Header {
        companion object {
            const val FIN = 0x01
            const val SYN = 0x02
            const val RST = 0x04
            const val PSH = 0x08
            const val ACK = 0x10
            const val URG = 0x20
        }

        override var sourcePort: Int = 0
        override var destinationPort: Int = 0

        var sequenceNumber: Long = 0
        var acknowledgementNumber: Long = 0

        var dataOffsetAndReserved: Byte = 0
        var headerLength: Int = 0
        var flags: Byte = 0
        var window: Int = 0

        var checksum: Int = 0
        var urgentPointer: Int = 0

        lateinit var optionsAndPadding: ByteArray

        val isFIN: Boolean get() = flags.toInt() and FIN == FIN
        val isSYN: Boolean get() = flags.toInt() and SYN == SYN
        val isRST: Boolean get() = flags.toInt() and RST == RST
        val isPSH: Boolean get() = flags.toInt() and PSH == PSH
        val isACK: Boolean get() = flags.toInt() and ACK == ACK
        val isURG: Boolean get() = flags.toInt() and URG == URG

        init {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.short)
            this.destinationPort = BitUtils.getUnsignedShort(buffer.short)

            this.sequenceNumber = BitUtils.getUnsignedInt(buffer.int)
            this.acknowledgementNumber = BitUtils.getUnsignedInt(buffer.int)

            this.dataOffsetAndReserved = buffer.get()
            this.headerLength = this.dataOffsetAndReserved.toInt() and 0xF0 shr 2
            this.flags = buffer.get()
            this.window = BitUtils.getUnsignedShort(buffer.short)

            this.checksum = BitUtils.getUnsignedShort(buffer.short)
            this.urgentPointer = BitUtils.getUnsignedShort(buffer.short)

            val optionsLength = this.headerLength - TCP_HEADER_SIZE
            if (optionsLength > 0) {
                optionsAndPadding = ByteArray(optionsLength)
                buffer.get(optionsAndPadding, 0, optionsLength)
            }
        }

        override fun fillHeader(buffer: ByteBuffer) {
            buffer.putShort(sourcePort.toShort())
            buffer.putShort(destinationPort.toShort())

            buffer.putInt(sequenceNumber.toInt())
            buffer.putInt(acknowledgementNumber.toInt())

            buffer.put(dataOffsetAndReserved)
            buffer.put(flags)
            buffer.putShort(window.toShort())

            buffer.putShort(checksum.toShort())
            buffer.putShort(urgentPointer.toShort())
        }

        override fun toString(): String {
            val sb = StringBuilder("TCPHeader{")
            sb.append("sourcePort=").append(sourcePort)
            sb.append(", destinationPort=").append(destinationPort)
            sb.append(", sequenceNumber=").append(sequenceNumber)
            sb.append(", acknowledgementNumber=").append(acknowledgementNumber)
            sb.append(", headerLength=").append(headerLength)
            sb.append(", window=").append(window)
            sb.append(", checksum=").append(checksum)
            sb.append(", flags=")
            if (isFIN) sb.append(" FIN")
            if (isSYN) sb.append(" SYN")
            if (isRST) sb.append(" RST")
            if (isPSH) sb.append(" PSH")
            if (isACK) sb.append(" ACK")
            if (isURG) sb.append(" URG")
            sb.append('}')
            return sb.toString()
        }
    }

    private class UdpHeader(buffer: ByteBuffer) : Header {
        override var sourcePort: Int = 0
        override var destinationPort: Int = 0

        var length: Int = 0
        var checksum: Int = 0

        init {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.short)
            this.destinationPort = BitUtils.getUnsignedShort(buffer.short)

            this.length = BitUtils.getUnsignedShort(buffer.short)
            this.checksum = BitUtils.getUnsignedShort(buffer.short)
        }

        override fun fillHeader(buffer: ByteBuffer) {
            buffer.putShort(this.sourcePort.toShort())
            buffer.putShort(this.destinationPort.toShort())

            buffer.putShort(this.length.toShort())
            buffer.putShort(this.checksum.toShort())
        }

        override fun toString(): String {
            val sb = StringBuilder("UDPHeader{")
            sb.append("sourcePort=").append(sourcePort)
            sb.append(", destinationPort=").append(destinationPort)
            sb.append(", length=").append(length)
            sb.append(", checksum=").append(checksum)
            sb.append('}')
            return sb.toString()
        }
    }

    private object BitUtils {
        fun getUnsignedByte(value: Byte): Short = (value and 0xFF.toByte()).toShort()

        fun getUnsignedShort(value: Short): Int = (value and 0xFFFF.toShort()).toInt()

        fun getUnsignedInt(value: Int): Long = value.toLong() and 0xFFFFFFFFL
    }
}
