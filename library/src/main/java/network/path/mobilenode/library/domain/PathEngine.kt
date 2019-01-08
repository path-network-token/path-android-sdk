package network.path.mobilenode.library.domain

import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobList
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult

internal interface PathEngine {
    interface Listener {
        fun onStatusChanged(status: ConnectionStatus)
        fun onRequestReceived(request: JobRequest)
        fun onNodeId(nodeId: String?)
        fun onJobListReceived(jobList: JobList?)
        fun onRunning(isRunning: Boolean)
    }

    val status: ConnectionStatus
    val nodeId: String?
    val jobList: JobList?
    val isJobExecutionRunning: Boolean

    // Initializes connection and starts retrieving (by either listening or polling) jobs
    fun start()

    // Send result of a job back to server
    fun processResult(result: JobResult)

    // Stop any interaction with the server
    fun stop()

    fun toggle()

    fun addListener(l: Listener): Boolean
    fun removeListener(l: Listener): Boolean
}
