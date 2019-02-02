package network.path.mobilenode.library.data.runner.mtr

internal data class MtrResult(
        val ttl: Int,
        val host: String,
        val ip: String,
        val timeout: Boolean,
        val delay: Double,
        val min: Double = delay,
        val max: Double = delay,
        val err: String?
)

internal class Mtr {
    external fun trace(server: String, port: Int, resolve: Boolean): Array<MtrResult?>?
}
