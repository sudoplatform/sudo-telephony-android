/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.keys

import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.PublicKey

/**
 * Responsible for managing the local storage and lifecycle of key pairs associated with the
 * telephony service.
 */
internal interface DeviceKeyManager {
    /**
     * Defines the exceptions for the [DeviceKeyManager] methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class DeviceKeyManagerException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        /** The signed-in user's identifier could not be resolved. */
        class UserIdNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : DeviceKeyManagerException(message = message, cause = cause)

        /** A key could not be generated. */
        class KeyGenerationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : DeviceKeyManagerException(message = message, cause = cause)

        /** An operation against the key store failed. */
        class KeyOperationFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : DeviceKeyManagerException(message = message, cause = cause)

        /** Sealed data could not be decrypted. */
        class DecryptionException(
            message: String? = null,
            cause: Throwable? = null,
        ) : DeviceKeyManagerException(message = message, cause = cause)

        /** A key archive could not be read or written. */
        class SecureKeyArchiveException(
            message: String? = null,
            cause: Throwable? = null,
        ) : DeviceKeyManagerException(message = message, cause = cause)

        /** An unexpected failure. */
        class UnknownException(
            message: String? = null,
            cause: Throwable? = null,
        ) : DeviceKeyManagerException(message = message, cause = cause)
    }

    /**
     * Returns the [PublicKey] with the identifier [keyId] if it exists.
     *
     * @param keyId [String] Identifier of the public key to retrieve.
     * @return The [PublicKey] that matches [keyId], or `null` if it does not exist.
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if the key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getPublicKeyWithId(keyId: String): PublicKey?

    /**
     * Returns true if a private key with the identifier [keyId] exists.
     *
     * @param keyId [String] Identifier of the private key to look for.
     * @return True if the private key exists, false otherwise.
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if the key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun privateKeyExists(keyId: String): Boolean

    /**
     * Decrypt [data] with the private key identified by [keyId].
     *
     * @param data [ByteArray] Data to decrypt.
     * @param keyId [String] Identifier of the private key to decrypt with.
     * @param algorithm [KeyManagerInterface.PublicKeyEncryptionAlgorithm] Algorithm to use.
     * @return The decrypted data.
     * @throws [DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithKeyPairId(
        data: ByteArray,
        keyId: String,
        algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm,
    ): ByteArray

    /**
     * Decrypt [data] with the symmetric [key] under the initialisation vector [iv].
     *
     * @param key [ByteArray] The symmetric key to decrypt with.
     * @param data [ByteArray] Data to decrypt, followed by its authentication tag when
     *  [algorithm] is authenticated.
     * @param iv [ByteArray] The initialisation vector [data] was encrypted under.
     * @param algorithm [KeyManagerInterface.SymmetricEncryptionAlgorithm] Algorithm to use.
     * @return The decrypted data.
     * @throws [DeviceKeyManagerException.DecryptionException] if the data cannot be decrypted.
     */
    @Throws(DeviceKeyManagerException::class)
    fun decryptWithSymmetricKey(
        key: ByteArray,
        data: ByteArray,
        iv: ByteArray,
        algorithm: KeyManagerInterface.SymmetricEncryptionAlgorithm,
    ): ByteArray

    /**
     * Replace the keys in the store with those held in [archiveData].
     *
     * Every existing key is removed first, so importing an archive makes the store match the
     * archive rather than merging into it.
     *
     * @param archiveData [ByteArray] The key archive to import.
     * @throws [DeviceKeyManagerException.SecureKeyArchiveException] if the archive cannot be
     *  read.
     */
    @Throws(DeviceKeyManagerException::class)
    fun importKeys(archiveData: ByteArray)

    /**
     * Export the keys in the store as an archive.
     *
     * @return The key archive.
     * @throws [DeviceKeyManagerException.SecureKeyArchiveException] if the archive cannot be
     *  written.
     */
    @Throws(DeviceKeyManagerException::class)
    fun exportKeys(): ByteArray

    /**
     * Remove all the keys from the [DeviceKeyManager].
     *
     * @throws [DeviceKeyManagerException.UnknownException] if the keys could not be removed.
     */
    @Throws(DeviceKeyManagerException::class)
    fun removeAllKeys()
}
