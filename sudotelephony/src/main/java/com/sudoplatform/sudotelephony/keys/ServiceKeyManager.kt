/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.keys

import com.sudoplatform.sudotelephony.keys.DeviceKeyManager.DeviceKeyManagerException

/**
 * Adds the telephony service's key ring and key pair operations to the local key store.
 */
internal interface ServiceKeyManager : DeviceKeyManager {
    /**
     * Returns the key ring id associated with the owner's service.
     *
     * @return The identifier of the key ring associated with the owner's service.
     * @throws [DeviceKeyManagerException.UserIdNotFoundException] if the user id cannot be found.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getKeyRingId(): String

    /**
     * Returns the [KeyPair] with the identifier [id] if it exists.
     *
     * @param id [String] Identifier of the [KeyPair] to retrieve.
     * @return The [KeyPair] with the identifier [id], or `null` if it does not exist.
     * @throws [DeviceKeyManagerException.KeyOperationFailedException] if the key operation fails.
     */
    @Throws(DeviceKeyManagerException::class)
    fun getKeyPairWithId(id: String): KeyPair?

    /**
     * Generates and stores a new [KeyPair].
     *
     * @return The generated [KeyPair].
     * @throws [DeviceKeyManagerException.KeyGenerationException] if unable to generate the [KeyPair].
     */
    @Throws(DeviceKeyManagerException::class)
    fun generateKeyPair(): KeyPair
}
