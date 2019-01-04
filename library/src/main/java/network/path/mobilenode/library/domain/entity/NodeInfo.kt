package network.path.mobilenode.library.domain.entity

/**
 * Class with information about user's connection as seen by Path API.
 */
data class NodeInfo(
        /**
         * Node ID of current node
         */
        val nodeId: String?,
        /**
         * AS number
         */
        val asn: Int?,
        /**
         * AS organization name
         */
        val asOrganization: String?,
        /**
         * Network prefix (in form of 192.168.0.0/24)
         */
        val networkPrefix: String?,
        /**
         * AS location
         */
        val location: String?
)
