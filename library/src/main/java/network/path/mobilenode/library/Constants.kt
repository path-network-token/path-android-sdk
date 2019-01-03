package network.path.mobilenode.library

import java.util.concurrent.TimeUnit

internal object Constants {
    const val PATH_API_VERSION = "1.0.1"

    const val PATH_DEFAULT_WALLET_ADDRESS = "0x0000000000000000000000000000000000000000"

    const val JOB_TIMEOUT_MILLIS = 10_000L
    const val TCP_UDP_READ_WRITE_TIMEOUT_MILLIS = 5000L
    const val DEFAULT_DEGRADED_TIMEOUT_MILLIS = 1000L
    const val DEFAULT_CRITICAL_TIMEOUT_MILLIS = 2000L
    val TRACEROUTE_JOB_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(1)

    val TCP_UDP_PORT_RANGE = 1..0xFFFF
    const val DEFAULT_UDP_PORT = 67
    const val DEFAULT_TCP_PORT = 80

    const val DEFAULT_TRACEPATH_PORT = 53
    const val RESPONSE_LENGTH_BYTES_MAX = 1 shl 15

    const val LOCALHOST = "127.0.0.1"
    val SS_LOCAL_PORT = if (BuildConfig.DEBUG) 1091 else 1081
    val SIMPLE_OBFS_PORT = if (BuildConfig.DEBUG) 1092 else 1082
}
