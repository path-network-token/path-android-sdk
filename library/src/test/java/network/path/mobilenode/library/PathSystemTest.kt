package network.path.mobilenode.library

import junit.framework.Assert.assertEquals
import network.path.mobilenode.library.domain.PathSystem
import org.junit.Test

class PathSystemTest {
    companion object {
        private const val DIGIT_ONLY_ADDRESS = "0x0123456789012345678901234567890123456789"
        private const val VALID_ADDRESS = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        private const val INVALID_ADDRESS = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAED"
    }

    @Test
    fun testIsWalletAddressValid() {
        assertEquals(PathSystem.isWalletAddressValid(VALID_ADDRESS), true)
        assertEquals(PathSystem.isWalletAddressValid(DIGIT_ONLY_ADDRESS), true)
        assertEquals(PathSystem.isWalletAddressValid(INVALID_ADDRESS), false)
    }

    @Test
    fun testStartStop() {

    }
}
