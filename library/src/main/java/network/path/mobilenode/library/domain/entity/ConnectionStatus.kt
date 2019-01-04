package network.path.mobilenode.library.domain.entity

/**
 * Connection status of [network.path.mobilenode.library.domain.PathSystem].
 *
 * @see [network.path.mobilenode.library.domain.PathSystem.status] for more details.
 */
enum class ConnectionStatus {
    LOOKING, CONNECTED, PROXY, DISCONNECTED
}
