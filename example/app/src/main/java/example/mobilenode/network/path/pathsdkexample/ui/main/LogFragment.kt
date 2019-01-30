package example.mobilenode.network.path.pathsdkexample.ui.main

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import example.mobilenode.network.path.pathsdkexample.MemoryTree
import example.mobilenode.network.path.pathsdkexample.R
import kotlinx.android.synthetic.main.log_fragment.*

class LogFragment : Fragment(), MemoryTree.Listener {
    companion object {
        fun newInstance() = LogFragment()
    }

    private val logTree = MemoryTree.create()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.log_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textLogs.keyListener = null
        logTree.records.filter { it.shouldShow() }.forEach { append(it) }
        logTree.addListener(this)
    }

    override fun onDestroyView() {
        logTree.removeListener(this)
        super.onDestroyView()
    }

    override fun onLog(record: MemoryTree.Record) {
        if (record.shouldShow()) {
            Handler().post { append(record) }
        }
    }

    private fun append(record: MemoryTree.Record) {
        if (record.shouldShow()) {
            textLogs.append("${record.message}\n")
        }
    }

    private fun MemoryTree.Record.shouldShow() =
        priority != Log.VERBOSE &&
                (priority == Log.WARN ||
                        priority == Log.ERROR ||
                        message.startsWith("HTTP: ") ||
                        message.startsWith("DOMAIN: ") ||
                        message.startsWith("SYSTEM: ") ||
                        message.startsWith("LOCATION: "))
}
