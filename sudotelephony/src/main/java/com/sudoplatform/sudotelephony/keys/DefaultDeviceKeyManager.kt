/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.keys

import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudokeymanager.KeyType
import com.sudoplatform.sudokeymanager.SecureKeyArchive
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.PublicKey

/**
 * Default [DeviceKeyManager] backed by the platform key manager, which stores keys in the
 * Android key store.
 *
 * @property keyManager [KeyManagerInterface] The platform key store.
 * @property logger [Logger] Errors and diagnostic information are logged here.
 */
internal open class DefaultDeviceKeyManager(
    private val keyManager: KeyManagerInterface,
    private val logger: Logger,
) : DeviceKeyManager {
    override fun getPublicKeyWithId(keyId: String): PublicKey? {
        try {
            val publicKey = keyManager.getPublicKey(keyId) ?: return null
            return PublicKey(keyId, publicKey.encoded)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "KeyManager exception",
                e,
            )
        }
    }

    override fun privateKeyExists(keyId: String): Boolean {
        try {
            return keyManager.getPrivateKeyData(keyId) != null
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "KeyManager exception",
                e,
            )
        }
    }

    override fun decryptWithKeyPairId(
        data: ByteArray,
        keyId: String,
        algorithm: KeyManagerInterface.PublicKeyEncryptionAlgorithm,
    ): ByteArray {
        try {
            return keyManager.decryptWithPrivateKey(keyId, data, algorithm)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException(
                "Failed to decrypt with key pair",
                e,
            )
        }
    }

    override fun decryptWithSymmetricKey(
        key: ByteArray,
        data: ByteArray,
        iv: ByteArray,
        algorithm: KeyManagerInterface.SymmetricEncryptionAlgorithm,
    ): ByteArray {
        try {
            return keyManager.decryptWithSymmetricKey(key, data, iv, algorithm)
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException(
                "Failed to decrypt with symmetric key",
                e,
            )
        }
    }

    override fun importKeys(archiveData: ByteArray) {
        try {
            keyManager.removeAllKeys()
            val archive = SecureKeyArchive.getInstanceV3(archiveData, keyManager)
            archive.unarchive()
            archive.saveKeys()
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException(
                "Failed to import keys",
                e,
            )
        }
    }

    override fun exportKeys(): ByteArray {
        try {
            val archive = SecureKeyArchive.getInstanceV3(keyManager)
            archive.excludedKeyTypes = mutableSetOf(KeyType.PUBLIC_KEY)
            archive.loadKeys()
            return archive.archive()
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException(
                "Failed to export keys",
                e,
            )
        }
    }

    override fun removeAllKeys() {
        try {
            keyManager.removeAllKeys()
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.UnknownException(
                "Failed to remove all keys",
                e,
            )
        }
    }
}
