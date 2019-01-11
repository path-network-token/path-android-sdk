package network.path.mobilenode.library

import network.path.mobilenode.library.domain.PathSystem
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PathSystemTest {
    companion object {
        private const val DIGIT_ONLY_ADDRESS = "0x0123456789012345678901234567890123456789"
        private const val VALID_ADDRESS = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        private const val INVALID_ADDRESS = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAED"
    }

    @Test
    fun testIsWalletAddressValid() {
        Assertions.assertTrue(PathSystem.isWalletAddressValid(VALID_ADDRESS))
        Assertions.assertTrue(PathSystem.isWalletAddressValid(DIGIT_ONLY_ADDRESS))
        Assertions.assertFalse(PathSystem.isWalletAddressValid(INVALID_ADDRESS))
    }
}
