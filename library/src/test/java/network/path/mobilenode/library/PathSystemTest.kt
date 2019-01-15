package network.path.mobilenode.library

import network.path.mobilenode.library.domain.PathSystem
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PathSystemTest {
    companion object {
        private const val DIGIT_ONLY_ADDRESS = "0x0123456789012345678901234567890123456789"
        private const val VALID_ADDRESS = "0xe9A3245A1368a5b006A6b5C7b35Ab016A085B065"
        private const val INVALID_ADDRESS = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAED"
        private const val UPPERCASE_ADDRESS = "0XF9A3245A1368A5B006A6B5C7B35AB016A085B065"
        private const val LOWERCASE_ADDRESS = "0xf9a3245a1368a5b006a6b5c7b35ab016a085b065"
    }

    @Test
    fun testIsWalletAddressValid() {
        Assertions.assertTrue(PathSystem.isWalletAddressValid(VALID_ADDRESS))
        Assertions.assertTrue(PathSystem.isWalletAddressValid(DIGIT_ONLY_ADDRESS))
        Assertions.assertTrue(PathSystem.isWalletAddressValid(UPPERCASE_ADDRESS))
        Assertions.assertTrue(PathSystem.isWalletAddressValid(LOWERCASE_ADDRESS))
        Assertions.assertFalse(PathSystem.isWalletAddressValid(INVALID_ADDRESS))
    }
}
