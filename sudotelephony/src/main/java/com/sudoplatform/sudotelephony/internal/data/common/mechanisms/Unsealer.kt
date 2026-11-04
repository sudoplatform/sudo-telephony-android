/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.common.mechanisms

import com.amazonaws.util.Base64
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager

/**
 * Unseals the sealed fields of a phone message.
 *
 * The service seals each field with the public key registered for the message's phone number.
 * A sealed value is base64 and, once decoded, holds:
 *
 * - bytes `0` until [BLOCK_SIZE_RSA]: a one-off AES key, encrypted with the public key.
 * - the following [IV_SIZE_AES_GCM] bytes: the nonce the field was encrypted under.
 * - the remainder: the field, encrypted with that AES key, followed by its
 *   [TAG_SIZE_AES_GCM]-byte authentication tag.
 *
 * @property deviceKeyManager [DeviceKeyManager] Performs the decryption with the device's
 *  private key.
 */
internal class Unsealer(
    private val deviceKeyManager: DeviceKeyManager,
) {
    companion object {
        /**
         * Length of the RSA-encrypted AES key that prefixes a sealed value, which is the
         * modulus size of the 2048-bit key pair registered when a number is provisioned.
         */
        const val BLOCK_SIZE_RSA = 256

        /** Length of the AES-GCM nonce that follows the encrypted AES key. */
        const val IV_SIZE_AES_GCM = 12

        /** Length of the AES-GCM authentication tag that ends a sealed value. */
        const val TAG_SIZE_AES_GCM = 16

        /**
         * Length of the shortest possible sealed value, which is the one that seals an empty
         * field: an encrypted AES key, a nonce and a tag, with no ciphertext between them.
         */
        private const val MINIMUM_SIZE_SEALED = BLOCK_SIZE_RSA + IV_SIZE_AES_GCM + TAG_SIZE_AES_GCM
    }

    /** Thrown when a sealed value cannot be unsealed. */
    sealed class UnsealerException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        /** The sealed value is too short to hold the envelope around a field. */
        class SealedDataTooShortException(
            message: String? = null,
            cause: Throwable? = null,
        ) : UnsealerException(message, cause)

        /** The value was sealed with an algorithm this version of the SDK cannot read. */
        class UnsupportedAlgorithmException(
            message: String? = null,
            cause: Throwable? = null,
        ) : UnsealerException(message, cause)
    }

    /**
     * Unseal a base64 sealed string.
     *
     * @param keyId [String] Identifier of the private key the value was sealed for.
     * @param valueBase64 [String] The sealed value.
     * @param algorithm [String] Algorithm the value was sealed with.
     * @return The plaintext.
     * @throws UnsealerException if the value cannot be unsealed.
     */
    @Throws(UnsealerException::class)
    fun unseal(
        keyId: String,
        valueBase64: String,
        algorithm: String = StringConstants.PUBLIC_KEY_ALGORITHM,
    ): String {
        if (algorithm != StringConstants.PUBLIC_KEY_ALGORITHM) {
            throw UnsealerException.UnsupportedAlgorithmException(algorithm)
        }

        val valueBytes = Base64.decode(valueBase64)
        if (valueBytes.size < MINIMUM_SIZE_SEALED) {
            throw UnsealerException.SealedDataTooShortException(
                "Sealed value is ${valueBytes.size} bytes, which cannot hold an encrypted key, a nonce and a tag",
            )
        }

        val encryptedCipherKey = valueBytes.copyOfRange(0, BLOCK_SIZE_RSA)
        val iv = valueBytes.copyOfRange(BLOCK_SIZE_RSA, BLOCK_SIZE_RSA + IV_SIZE_AES_GCM)
        val encryptedData =
            valueBytes.copyOfRange(BLOCK_SIZE_RSA + IV_SIZE_AES_GCM, valueBytes.size)

        val cipherKey =
            deviceKeyManager.decryptWithKeyPairId(
                encryptedCipherKey,
                keyId,
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
            )
        return String(
            deviceKeyManager.decryptWithSymmetricKey(
                cipherKey,
                encryptedData,
                iv,
                KeyManagerInterface.SymmetricEncryptionAlgorithm.AES_GCM_256,
            ),
            Charsets.UTF_8,
        )
    }
}
