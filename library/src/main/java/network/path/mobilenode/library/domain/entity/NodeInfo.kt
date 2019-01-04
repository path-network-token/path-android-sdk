package network.path.mobilenode.library.domain.entity

/**
 * Class with information about user's connection as seen by Path API.
 */
data class NodeInfo(
        val nodeId: String?,
        val asn: Int?,
        val asOrganization: String?,
        val networkPrefix: String?,
        val location: String?
)
