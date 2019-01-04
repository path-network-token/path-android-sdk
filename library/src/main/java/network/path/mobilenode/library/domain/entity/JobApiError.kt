package network.path.mobilenode.library.domain.entity

internal data class JobApiError(
        val type: String,
        val errorCode: Int,
        val description: String
)
