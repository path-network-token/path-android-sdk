package network.path.mobilenode.library

import network.path.mobilenode.library.utils.CustomThreadPoolManager
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class CustomThreadPoolManagerTest {
    private lateinit var manager: CustomThreadPoolManager

    @BeforeEach
    fun init() {
        manager = CustomThreadPoolManager()
    }

    @Test
    fun testImmediate() {
        val result = manager.run("test immediate", delay = 0) {
            1
        }
        Assertions.assertNotNull(result)
        Assertions.assertEquals(result!!.get(), 1)
    }

    @Test
    fun testScheduledTimeout() {
        val result = manager.run("test scheduled fail", delay = 1000) {
            1
        }
        Assertions.assertNotNull(result)
        Assertions.assertTimeoutPreemptively(Duration.ofMillis(500)) {
            result?.get()
        }
    }

    @Test
    fun testScheduledSuccess() {
        val result = manager.run("test scheduled success", delay = 500) {
            2
        }
        Assertions.assertNotNull(result)
        Assertions.assertEquals(result!!.get(), 2)
    }

    @Test
    fun testShutdown() {
        manager.destroy()
        Assertions.assertNull(manager.run("test null") {})
    }
}
