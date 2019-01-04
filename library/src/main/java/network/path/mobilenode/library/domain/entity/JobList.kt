package network.path.mobilenode.library.domain.entity

import com.google.gson.annotations.SerializedName

internal data class JobList(
        val type: String,
        val nodeId: String?,
        @SerializedName("ASN")
        val asn: Int?,
        @SerializedName("AS_organization")
        val asOrganization: String?,
        val networkPrefix: String?,
        val location: String?,
        val jobs: List<JobExecutionId>
) {
    val nodeInfo: NodeInfo
        get() = NodeInfo(nodeId, asn, asOrganization, networkPrefix, location)

}

internal data class JobExecutionId(val executionUuid: String)
