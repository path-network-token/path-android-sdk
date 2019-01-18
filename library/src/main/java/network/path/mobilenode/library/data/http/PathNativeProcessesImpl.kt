package network.path.mobilenode.library.data.http

import android.content.Context
import network.path.mobilenode.library.BuildConfig
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.DomainGenerator
import network.path.mobilenode.library.domain.PathNativeProcesses
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.utils.Executable
import network.path.mobilenode.library.utils.GuardedProcessPool
import timber.log.Timber
import java.io.File

internal class PathNativeProcessesImpl(
    private val context: Context,
    private val storage: PathStorage
) : PathNativeProcesses {
    companion object {
        private const val TIMEOUT = 600
        private const val PROXY_PORT = 443
        private const val PROXY_PASSWORD = "PathNetwork"
        private const val PROXY_ENCRYPTION_METHOD = "aes-256-cfb"
    }

    private val ssLocal = GuardedProcessPool()
    private val simpleObfs = GuardedProcessPool()

    override fun start() {
        stop()

        val host = DomainGenerator.findDomain(storage)
        if (host != null) {
            Timber.d("NATIVE: found proxy domain [$host]")

            val libs = context.applicationInfo.nativeLibraryDir
            val obfsCmd = mutableListOf(
                File(libs, Executable.SIMPLE_OBFS).absolutePath,
                "-s", host,
                "-p", PROXY_PORT.toString(),
                "-l", Constants.SIMPLE_OBFS_PORT.toString(),
                "-t", TIMEOUT.toString(),
                "--obfs", "http"
            )
            if (BuildConfig.DEBUG) {
                obfsCmd.add("-v")
            }
            simpleObfs.start(obfsCmd)

            val cmd = mutableListOf(
                File(libs, Executable.SS_LOCAL).absolutePath,
                "-u",
                "-s", Constants.LOCALHOST,
                "-p", Constants.SIMPLE_OBFS_PORT.toString(),
                "-k", PROXY_PASSWORD,
                "-m", PROXY_ENCRYPTION_METHOD,
                "-b", Constants.LOCALHOST,
                "-l", Constants.SS_LOCAL_PORT.toString(),
                "-t", TIMEOUT.toString()
            )
            if (BuildConfig.DEBUG) {
                cmd.add("-v")
            }

            ssLocal.start(cmd)
        } else {
            Timber.w("NATIVE: proxy domain not found")
        }
    }

    override fun stop() {
        Timber.d("NATIVE: stopping native processes and scheduled restart thread")
        simpleObfs.killAll()
        ssLocal.killAll()
        Executable.killAll(context)
    }
}
