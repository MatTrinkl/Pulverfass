package at.aau.pulverfass.shared.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256(bytes: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { digestPinned ->
        if (bytes.isEmpty()) {
            CC_SHA256(null, 0u, digestPinned.addressOf(0))
        } else {
            bytes.usePinned { dataPinned ->
                CC_SHA256(dataPinned.addressOf(0), bytes.size.toUInt(), digestPinned.addressOf(0))
            }
        }
    }
    return digest.toByteArray()
}
