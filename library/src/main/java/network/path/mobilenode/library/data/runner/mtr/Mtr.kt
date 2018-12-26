package network.path.mobilenode.library.data.runner.mtr

class MtrResult(
        val ttl: Int,
        val host: String,
        val ip: String,
        val timeout: Boolean,
        val recv_ttl: Int,
        val ext: String?,
        val delay: Double,
        val err: String?
)

class Mtr {
    external fun trace(server: String, port: Int): Array<MtrResult?>?
}
