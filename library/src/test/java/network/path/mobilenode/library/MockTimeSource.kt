package network.path.mobilenode.library

import network.path.mobilenode.library.data.runner.TimeSource
import java.time.Instant

object MockTimeSource : TimeSource {
    override val currentTimeMillis: Long
        get() = Instant.now().toEpochMilli()
}
