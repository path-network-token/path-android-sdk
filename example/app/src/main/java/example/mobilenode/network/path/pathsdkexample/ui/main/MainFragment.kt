package example.mobilenode.network.path.pathsdkexample.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import example.mobilenode.network.path.pathsdkexample.R
import kotlinx.android.synthetic.main.main_fragment.*
import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.JobTypeStatistics
import network.path.mobilenode.library.domain.entity.NodeInfo

class MainFragment : Fragment() {
    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        viewModel.let {
            it.onViewCreated()
            it.nodeId.observe(this, ::setNodeId)
            it.status.observe(this, ::setStatus)
            it.nodeInfo.observe(this, ::setNodeInfo)
            it.isRunning.observe(this, ::setRunning)
            it.statistics.observe(this, ::setStatistics)

            updateState(it.isStarted)
            labelMode.text = getString(if (it.isTest) R.string.label_test_server else R.string.label_prod_server)
        }

        // Checkboxes
        checkboxAutoStart.isChecked = viewModel.autoStart
        checkboxAutoStart.setOnCheckedChangeListener { _, value -> viewModel.autoStart = value }

        checkboxWifiOnly.isChecked = viewModel.isWifiOnly
        checkboxWifiOnly.setOnCheckedChangeListener { _, value -> viewModel.isWifiOnly = value }

        // Buttons
        buttonStatus.setOnClickListener {
            updateState(viewModel.toggleConnection())
        }

        buttonToggle.setOnClickListener {
            viewModel.toggle()
        }

        buttonLogs.setOnClickListener {
            requireActivity().supportFragmentManager
                .beginTransaction()
                .addToBackStack(LogFragment::class.java.simpleName)
                .replace(R.id.container, LogFragment.newInstance())
                .commit()
        }
    }

    private fun updateState(isStarted: Boolean) {
        buttonStatus.text = getString(if (isStarted) R.string.button_stop else R.string.button_start)
        buttonToggle.isEnabled = isStarted
    }

    private fun setNodeId(nodeId: String?) {
        value0.text = nodeId?.orNoData()
    }

    private fun setStatus(status: ConnectionStatus) {
        valueStatus.text = status.label
        valueStatus.setTextColor(status.color)
    }

    private fun setNodeInfo(nodeInfo: NodeInfo?) {
        value1.text = nodeInfo?.networkPrefix.orNoData()
        value2.text = nodeInfo?.asn?.toString().orNoData()
        value3.text = nodeInfo?.asOrganization.orNoData()
        value4.text = nodeInfo?.location.orNoData()
    }

    private fun setRunning(isRunning: Boolean) {
        valueRunning.text = getString(if (isRunning) R.string.state_running else R.string.state_paused)
        valueRunning.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isRunning) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
        buttonToggle.text = getString(if (isRunning) R.string.button_pause else R.string.button_resume)
    }

    private fun setStatistics(stats: List<JobTypeStatistics>) {
        val totalCount = stats.map { it.count }.sum()
        labelStats1.text = getString(R.string.label_total_jobs)
        valueStats1.text = totalCount.toString()

        if (stats.isNotEmpty()) {
            val otherStats = stats.subList(2, stats.size - 1)
                .fold(JobTypeStatistics(null, 0, 0)) { total, s ->
                    total.addOther(s)
                }

            applyStats(stats[0], labelStats2, valueStats2)
            applyStats(stats[1], labelStats3, valueStats3)
            applyStats(otherStats, labelStats4, valueStats4)
        }
    }

    private fun applyStats(stat: JobTypeStatistics, label: TextView, value: TextView) {
        label.text = stat.type.title
        value.text = "${stat.count} (${stat.averageLatency} ms)"
    }

    private fun String?.orNoData() = this ?: getString(R.string.no_data)

    private val ConnectionStatus.label: String
        get() = getString(
            when (this) {
                ConnectionStatus.CONNECTED -> R.string.status_connected
                ConnectionStatus.PROXY -> R.string.status_proxy
                ConnectionStatus.LOOKING,
                ConnectionStatus.DISCONNECTED -> R.string.status_disconnected
            }
        )

    private val ConnectionStatus.color: Int
        get() = ContextCompat.getColor(
            requireContext(), when (this) {
                ConnectionStatus.CONNECTED -> android.R.color.holo_green_dark
                ConnectionStatus.PROXY -> android.R.color.holo_blue_dark
                ConnectionStatus.LOOKING,
                ConnectionStatus.DISCONNECTED -> android.R.color.holo_red_dark
            }
        )


    private val JobType?.title
        get() = getString(
            when (this) {
                null -> R.string.other_checks
                JobType.HTTP -> R.string.http_checks
                JobType.TCP -> R.string.tcp_checks
                JobType.UDP -> R.string.udp_checks
                JobType.DNS -> R.string.dns_checks
                JobType.TRACEROUTE -> R.string.traceroute_checks
                JobType.UNKNOWN -> R.string.unknown_checks
            }
        )

    private fun <T> LiveData<T>.observe(lifecycleOwner: LifecycleOwner, block: (T) -> Unit) {
        observe(lifecycleOwner, Observer { block(it) })
    }
}
