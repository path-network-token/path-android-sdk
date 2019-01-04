package network.path.mobilenode.library.domain.entity

/**
 * Connection status of [network.path.mobilenode.library.domain.PathSystem].
 *
 * @see [network.path.mobilenode.library.domain.PathSystem.status] for more details.
 */
enum class ConnectionStatus {
    /**
     * Initial connection status (looking for node)
     */
    LOOKING,
    /**
     * Latest check-in completed successfully
     */
    CONNECTED,
    /**
     * Latest check-in completed successfully through SOCKS proxy
     */
    PROXY,
    /**
     * Latest check-in failed
     */
    DISCONNECTED
}
