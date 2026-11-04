/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.keys

import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.util.UUID

/**
 * Manages the lifecycle of key pairs associated with the telephony service.
 *
 * @property keyRingServiceName [String] Namespace the key ring identifier is derived from.
 * @property userClient [SudoUserClient] Resolves the signed-in user's identifier.
 * @property keyManager [KeyManagerInterface] The platform key store.
 * @property logger [Logger] Errors and diagnostic information are logged here.
 */
internal class DefaultServiceKeyManager(
    private val keyRingServiceName: String,
    private val userClient: SudoUserClient,
    private val keyManager: KeyManagerInterface,
    private val logger: Logger,
) : DefaultDeviceKeyManager(keyManager, logger),
    ServiceKeyManager {
    @Throws(DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException::class)
    override fun getKeyRingId(): String {
        try {
            val userId =
                userClient.getSubject()
                    ?: throw DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException(
                        "UserId not found",
                    )
            return "$keyRingServiceName.$userId"
        } catch (e: Exception) {
            throw DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException(
                "UserId could not be accessed",
                e,
            )
        }
    }

    override fun getKeyPairWithId(id: String): KeyPair? {
        try {
            val publicKey = keyManager.getPublicKeyData(id) ?: return null
            val privateKey = keyManager.getPrivateKeyData(id) ?: return null
            return KeyPair(
                keyId = id,
                keyRingId = getKeyRingId(),
                publicKey = publicKey,
                privateKey = privateKey,
            )
        } catch (e: KeyManagerException) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "KeyManager exception",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun generateKeyPair(): KeyPair {
        val keyId = UUID.randomUUID().toString()
        try {
            keyManager.generateKeyPair(keyId, true)

            val publicKey = keyManager.getPublicKeyData(keyId)
            val privateKey = keyManager.getPrivateKeyData(keyId)
            return KeyPair(
                keyId = keyId,
                keyRingId = getKeyRingId(),
                publicKey = publicKey!!,
                privateKey = privateKey!!,
            )
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException(
                "Failed to generate key pair",
                e,
            )
        }
    }
}
